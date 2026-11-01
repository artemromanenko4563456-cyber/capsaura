package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class CapsAuraClient implements ClientModInitializer {

	// ======== НАСТРОЙКИ ========
	// Клавиши привязаны к физическому положению: GLFW_KEY_B = клавиша «И» на русской раскладке
	private static final int TOGGLE_KEY = GLFW.GLFW_KEY_B;          // включение ауры
	private static final int FRIEND_KEY = GLFW.GLFW_KEY_KP_ENTER;   // друг: добавить/убрать (Num Enter)
	private static final double RANGE = 4.0;                        // радиус атаки в блоках
	private static final int ATTACKS_PER_SECOND = 7;                // ударов в секунду (от 1 до 20)
	private static final boolean ATTACK_PLAYERS = true;             // бить игроков
	private static final boolean ATTACK_HOSTILE = true;             // бить враждебных мобов
	private static final boolean ATTACK_PASSIVE = false;            // бить животных и жителей
	private static final boolean AIM = true;                        // наводить камеру
	// ===========================

	private static KeyMapping toggleKey;
	private static KeyMapping friendKey;
	private static boolean enabled = false;
	private static int attackCharge = 0;
	static final Set<String> friends = new TreeSet<>();

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("capsaura", "main"));

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.toggle", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));
		friendKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.friend", InputConstants.Type.KEYSYM, FRIEND_KEY, category));
				BridgeAssist.init(category);
		   EspOverlay.init(category);

		loadFriends();
		ClientTickEvents.END_CLIENT_TICK.register(CapsAuraClient::onTick);
	}

	private static void onTick(Minecraft mc) {
		handleToggle(mc);
		handleFriendKey(mc);

		if (!enabled || mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		LocalPlayer player = mc.player;
		if (!player.isAlive()) {
			return;
		}

		LivingEntity target = findTarget(mc, player);
		if (target == null) {
			return;
		}

		if (AIM) {
			aimAt(player, target);
		}

		// Накопительный счётчик: ровно ATTACKS_PER_SECOND ударов за 20 тиков
		attackCharge += Math.min(ATTACKS_PER_SECOND, 20);
		if (attackCharge >= 20) {
			attackCharge -= 20;
			mc.gameMode.attack(player, target);
			player.swing(InteractionHand.MAIN_HAND);
		}
	}

	// ---------- включение ----------

	private static void handleToggle(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			attackCharge = 0;
			if (enabled) {
				loadFriends();
			}
			say(mc, "Kill Aura: " + (enabled ? "ON (friends: " + friends.size() + ")" : "OFF"));
		}
	}

	// ---------- друзья ----------

	/** Num Enter: добавляет или убирает из друзей игрока, на которого вы смотрите. */
	private static void handleFriendKey(Minecraft mc) {
		while (friendKey.consumeClick()) {
			if (mc.player == null || mc.level == null) {
				continue;
			}
			Player looked = findLookedAtPlayer(mc, mc.player);
			if (looked == null) {
				say(mc, "Friend: наведите прицел на игрока");
				continue;
			}
			String name = looked.getName().getString();
			String key = name.toLowerCase(Locale.ROOT);
			if (friends.remove(key)) {
				say(mc, "Friend removed: " + name);
			} else {
				friends.add(key);
				say(mc, "Friend added: " + name);
			}
			saveFriends();
		}
	}

	/** Игрок, ближайший к центру прицела (в пределах 64 блоков), или null. */
	private static Player findLookedAtPlayer(Minecraft mc, LocalPlayer self) {
		Vec3 eye = self.getEyePosition();
		Vec3 look = self.getViewVector(1.0F);
		Player best = null;
		double bestDot = 0.97; // примерно 14 градусов от центра прицела

		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Player other) || other == self) {
				continue;
			}
			Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
			double len = to.length();
			if (len < 0.1 || len > 64.0) {
				continue;
			}
			double dot = to.scale(1.0 / len).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = other;
			}
		}
		return best;
	}

	private static Path friendsPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("capsaura-friends.txt");
	}

	/** Читает config/capsaura-friends.txt (создаёт, если нет). */
	private static void loadFriends() {
		friends.clear();
		try {
			Path file = friendsPath();
			if (!Files.exists(file)) {
				saveFriends();
			}
			for (String line : Files.readAllLines(file)) {
				String name = line.trim();
				if (!name.isEmpty() && !name.startsWith("#")) {
					friends.add(name.toLowerCase(Locale.ROOT));
				}
			}
		} catch (Exception ignored) {
			// файл не прочитался: работаем с текущим списком
		}
	}

	private static void saveFriends() {
		try {
			List<String> lines = new ArrayList<>();
			lines.add("# Friends: one nickname per line. The aura will not touch them.");
			lines.add("# Lines starting with # are ignored. Case does not matter.");
			lines.addAll(friends);
			Files.write(friendsPath(), lines);
		} catch (Exception ignored) {
			// не удалось сохранить: список останется только в памяти
		}
	}

	// ---------- цели ----------

	/** Ближайшая подходящая цель в радиусе или null. */
	private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
		LivingEntity best = null;
		double bestDist = RANGE * RANGE;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !isValidTarget(player, living)) {
				continue;
			}
			double dist = player.distanceToSqr(living);
			if (dist < bestDist) {
				bestDist = dist;
				best = living;
			}
		}
		return best;
	}

	private static boolean isValidTarget(LocalPlayer self, LivingEntity e) {
		if (e == self || !e.isAlive() || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player) {
			if (!ATTACK_PLAYERS) return false;
			return !friends.contains(e.getName().getString().toLowerCase(Locale.ROOT));
		}
		if (e instanceof Enemy) {
			return ATTACK_HOSTILE;
		}
		return ATTACK_PASSIVE;
	}

	/** Поворачивает камеру на центр цели. */
	private static void aimAt(LocalPlayer player, LivingEntity target) {
		Vec3 from = player.getEyePosition();
		Vec3 to = target.getBoundingBox().getCenter();
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double flat = Math.sqrt(dx * dx + dz * dz);

		player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
		player.setXRot((float) (-Math.toDegrees(Math.atan2(dy, flat))));
	}

	private static void say(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal(text));
		}
	}
}
