package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class EspOverlay {

	// ======== НАСТРОЙКИ ========
	private static final int ESP_KEY = GLFW.GLFW_KEY_H;      // рамки и надписи
	private static final int TRACER_KEY = GLFW.GLFW_KEY_J;   // линии у прицела
	private static final double MAX_DISTANCE = 128.0;        // дальше этого игроки не показываются
	private static final int TRACER_START = 14;              // отступ линии от центра прицела, пиксели
	private static final int TRACER_LENGTH = 30;             // длина линии, пиксели (поставьте 400, чтобы тянулись до цели)
	private static final int COLOR_ENEMY = 0xFFFF5555;
	private static final int COLOR_FRIEND = 0xFF55FF55;
	// ===========================

	private static KeyMapping espKey;
	private static KeyMapping tracerKey;
	private static boolean espOn = false;
	private static boolean tracersOn = false;

	// Базис камеры и параметры экрана, считаются один раз за кадр
	private static double fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, aspect, screenW, screenH;

	private EspOverlay() {}

	public static void init(KeyMapping.Category category) {
		espKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.esp", InputConstants.Type.KEYSYM, ESP_KEY, category));
		tracerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.tracers", InputConstants.Type.KEYSYM, TRACER_KEY, category));

		ClientTickEvents.END_CLIENT_TICK.register(EspOverlay::onTick);
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("capsaura", "esp"),
				EspOverlay::render);
	}

	private static void onTick(Minecraft mc) {
		while (espKey.consumeClick()) {
			espOn = !espOn;
			say(mc, "ESP: " + (espOn ? "ON" : "OFF"));
		}
		while (tracerKey.consumeClick()) {
			tracersOn = !tracersOn;
			say(mc, "Tracers: " + (tracersOn ? "ON" : "OFF"));
		}
	}

	private static void say(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(text));
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!espOn && !tracersOn) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer self = mc.player;
		if (self == null || mc.level == null) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		setupCamera(mc, self, partial);
		Vec3 eye = self.getEyePosition(partial);

		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Player other) || other == self || !other.isAlive()) {
				continue;
			}
			double dist = self.distanceTo(other);
			if (dist > MAX_DISTANCE) {
				continue;
			}

			String name = other.getName().getString();
			boolean friend = CapsAuraClient.friends.contains(name.toLowerCase(Locale.ROOT));
			int color = friend ? COLOR_FRIEND : COLOR_ENEMY;

			Vec3 pos = other.getPosition(partial);
			double hw = other.getBbWidth() / 2.0;
			double h = other.getBbHeight();

			if (espOn) {
				drawBox(graphics, mc, eye, pos, hw, h, name, dist, color);
			}
			if (tracersOn) {
				drawTracer(graphics, eye, pos.add(0, h / 2.0, 0), color);
			}
		}
	}

	/** Считает направления камеры (вперёд, вправо, вверх) и параметры проекции. */
	private static void setupCamera(Minecraft mc, LocalPlayer self, float partial) {
		double yaw = Math.toRadians(self.getViewYRot(partial));
		double pitch = Math.toRadians(self.getViewXRot(partial));
		double cy = Math.cos(yaw), sy = Math.sin(yaw);
		double cp = Math.cos(pitch), sp = Math.sin(pitch);

		fx = -sy * cp;
		fy = -sp;
		fz = cy * cp;
		rx = -cy;
		rz = -sy;
		// up = right x forward (right не имеет вертикальной составляющей)
		ux = -rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy;

		screenW = mc.getWindow().getGuiScaledWidth();
		screenH = mc.getWindow().getGuiScaledHeight();
		aspect = screenW / screenH;
		tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
	}

	/** Экранные координаты точки (смещение от глаз) или null, если точка позади камеры. */
	private static double[] project(double dx, double dy, double dz) {
		double z = dx * fx + dy * fy + dz * fz;
		if (z < 0.05) {
			return null;
		}
		double x = dx * rx + dz * rz;
		double y = dx * ux + dy * uy + dz * uz;
		double nx = x / (z * tanHalf * aspect);
		double ny = y / (z * tanHalf);
		return new double[] { screenW / 2.0 + nx * screenW / 2.0, screenH / 2.0 - ny * screenH / 2.0 };
	}

	private static void drawBox(GuiGraphicsExtractor graphics, Minecraft mc, Vec3 eye, Vec3 pos,
			double hw, double h, String name, double dist, int color) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		boolean any = false;

		for (int ix = -1; ix <= 1; ix += 2) {
			for (int iz = -1; iz <= 1; iz += 2) {
				for (int iy = 0; iy <= 1; iy++) {
					double[] p = project(
							pos.x + ix * hw - eye.x,
							pos.y + iy * h - eye.y,
							pos.z + iz * hw - eye.z);
					if (p == null) {
						continue;
					}
					any = true;
					minX = Math.min(minX, p[0]);
					maxX = Math.max(maxX, p[0]);
					minY = Math.min(minY, p[1]);
					maxY = Math.max(maxY, p[1]);
				}
			}
		}
		if (!any) {
			return; // игрок позади камеры
		}

		int x = (int) Math.floor(minX);
		int y = (int) Math.floor(minY);
		int w = Math.max(2, (int) Math.ceil(maxX - minX));
		int hgt = Math.max(2, (int) Math.ceil(maxY - minY));
		graphics.outline(x, y, w, hgt, color);

		String label = String.format(Locale.ROOT, "%s [%.1fm]", name, dist);
		int textW = mc.font.width(label);
		graphics.text(mc.font, label, x + w / 2 - textW / 2, y - 10, color, true);
	}

	/** Короткая линия от прицела в сторону цели (работает и для целей вне экрана и позади). */
	private static void drawTracer(GuiGraphicsExtractor graphics, Vec3 eye, Vec3 target, int color) {
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double x = dx * rx + dz * rz;
		double y = dx * ux + dy * uy + dz * uz;
		double len = Math.sqrt(x * x + y * y);
		if (len < 0.05) {
			return; // цель точно в центре прицела
		}
		double dirX = x / len;
		double dirY = -y / len; // на экране ось Y направлена вниз

		double cx = screenW / 2.0;
		double cy = screenH / 2.0;
		drawLine(graphics,
				cx + dirX * TRACER_START, cy + dirY * TRACER_START,
				cx + dirX * (TRACER_START + TRACER_LENGTH), cy + dirY * (TRACER_START + TRACER_LENGTH),
				color);
	}

	/** Линия из маленьких квадратиков (отдельной функции линии в графике нет). */
	private static void drawLine(GuiGraphicsExtractor graphics, double x1, double y1, double x2, double y2, int color) {
		double length = Math.hypot(x2 - x1, y2 - y1);
		int steps = Math.max(1, (int) (length / 1.5));
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / steps;
			int px = (int) Math.round(x1 + (x2 - x1) * t);
			int py = (int) Math.round(y1 + (y2 - y1) * t);
			graphics.fill(px, py, px + 2, py + 2, color);
		}
	}
}
