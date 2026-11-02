package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * "Aura 1.9+": PvP для новых версий (с перезарядкой оружия).
 *
 * Старая "Aura 1.8.9" бьёт N раз в секунду, не глядя на перезарядку.
 * Эта бьёт только когда оружие заряжено (на 1.9+ иначе урон режется).
 *
 * Настройки (Range, Players, Hostile, Passive, Aim) общие с меню CapsAura:
 * берутся из CapsAuraClient, поэтому отдельных ползунков не нужно.
 */
public final class KillAura {

    /** Клавиша включения (по умолчанию K). */
    public static final int TOGGLE_KEY = GLFW.GLFW_KEY_K;

    /** Минимальная зарядка оружия (0..1), при которой разрешён удар. */
    public static float MIN_CHARGE = 0.9F;

    public static boolean enabled = false;

    private static KeyMapping toggleKey;

    private KillAura() {
    }

    public static void init(KeyMapping.Category category) {
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.capsaura.killaura", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));
        ClientTickEvents.END_CLIENT_TICK.register(KillAura::onTick);
    }

    /** Включает/выключает ауру. При включении гасит остальные ауры, чтобы не били вдвоём. */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            CapsAuraClient.enabled = false;
            CritAura.enabled = false;
        }
    }

    // ---- логика -------------------------------------------------------

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            setEnabled(!enabled);
            say(mc, "Aura 1.9+: " + (enabled ? "ON" : "OFF"));
        }

        // если включили другую ауру (через меню или клавишу), эта выключается
        if (enabled && (CapsAuraClient.enabled || CritAura.enabled)) {
            enabled = false;
        }

        LocalPlayer player = mc.player;
        if (!enabled || player == null || mc.level == null || mc.gameMode == null || !player.isAlive()) {
            return;
        }

        LivingEntity target = findTarget(mc, player);
        if (target == null) {
            return;
        }

        if (CapsAuraClient.AIM) {
            aimAt(player, target);
        }

        if (player.getAttackStrengthScale(0.5F) < MIN_CHARGE) {
            return;
        }

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
        LivingEntity best = null;
        double bestDist = CapsAuraClient.RANGE * CapsAuraClient.RANGE;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || !isValidTarget(player, living)) {
                continue;
            }
            double dist = player.distanceToSqr(living);
            if (dist <= bestDist) {
                best = living;
                bestDist = dist;
            }
        }
        return best;
    }

    private static boolean isValidTarget(LocalPlayer player, LivingEntity e) {
        if (e == player || !e.isAlive() || e instanceof ArmorStand) {
            return false;
        }
        if (e instanceof Player other) {
            if (!CapsAuraClient.ATTACK_PLAYERS) {
                return false;
            }
            String name = other.getName().getString().toLowerCase(Locale.ROOT);
            return !CapsAuraClient.friends.contains(name);
        }
        return e instanceof Enemy ? CapsAuraClient.ATTACK_HOSTILE : CapsAuraClient.ATTACK_PASSIVE;
    }

    private static void SilentRotation.aimAt(player, target); {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);

        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, flat)));
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}
