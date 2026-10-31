package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
import org.lwjgl.glfw.GLFW;

public class CapsAuraClient implements ClientModInitializer {
	// ---- Настройки (меняйте и пересобирайте) ----
	/** Радиус атаки в блоках. */
	private static final double RANGE = 4.0;
	/** true = бить всех живых мобов (животные, жители), false = только враждебных. */
	private static final boolean ATTACK_PASSIVE = false;
	/** true = бить других игроков. */
	private static final boolean ATTACK_PLAYERS = true;
	// ---------------------------------------------

	private static KeyMapping toggleKey;
	private static boolean enabled = false;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("capsaura", "main"));

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_CAPS_LOCK,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(CapsAuraClient::onTick);
	}

	private static void onTick(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			if (mc.gui != null) {
				mc.gui.setOverlayMessage(
						Component.literal("Kill Aura: " + (enabled ? "ON" : "OFF")), false);
			}
		}

		if (!enabled || mc.player == null || mc.level == null
				|| mc.gameMode == null || mc.screen != null) {
			return;
		}

		LocalPlayer player = mc.player;
		if (!player.isAlive() || player.getAttackStrengthScale(0.5f) < 1.0f) {
			return; // ждём, пока восстановится кулдаун оружия
		}

		LivingEntity target = null;
		double best = RANGE * RANGE;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || e == player || !living.isAlive()) {
				continue;
			}
			if (living instanceof ArmorStand) {
				continue;
			}
			if (living instanceof Player) {
				if (!ATTACK_PLAYERS) continue;
			} else if (!ATTACK_PASSIVE && !(living instanceof Enemy)) {
				continue;
			}

			double dist = player.distanceToSqr(living);
			if (dist < best) {
				best = dist;
				target = living;
			}
		}

		if (target != null) {
			mc.gameMode.attack(player, target);
			player.swing(InteractionHand.MAIN_HAND);
		}
	}
}
