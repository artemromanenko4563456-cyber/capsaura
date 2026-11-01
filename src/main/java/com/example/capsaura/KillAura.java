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
 * Kill Aura для Minecraft 26.2 (Fabric, официальные названия Mojang).
 *
 * Бьёт ближайшую подходящую цель в радиусе RANGE, учитывая перезарядку оружия
 * и ограничение на количество ударов в секунду.
 *
 * Подключение: в CapsAuraClient.onInitializeClient() рядом со строкой
 * CritAura.init(...) добавьте такую же строку: KillAura.init(...).
 */
public final class KillAura {

    /** Клавиша включения (по умолчанию K). */
    public static final int TOGGLE_KEY = GLFW.GLFW_KEY_K;

    // ---- настройки ----------------------------------------------------

    /** Радиус атаки в блоках. */
    public static double RANGE = 3.5;
    /** Максимум ударов в секунду. */
    public static int MAX_CPS = 10;
    /** Минимальная зарядка оружия (0..1), при которой разрешён удар. */
    public static float MIN_CHARGE = 0.9F;
    public static boolean ATTACK_PLAYERS = false;
    public static boolean ATTACK_HOSTILE = true;
    public static boolean ATTACK_PASSIVE = false;
    /** Поворачивать камеру на цель перед ударом. */
    public static boolean AIM = true;

    // ---- состояние ----------------------------------------------------

    public static boolean enabled = false;

    private static KeyMapping toggleKey;
    private static int cooldownTicks = 0;

    private KillAura() {
    }

    public static void init(KeyMapping.Category category) {
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.capsaura.killaura", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));
        ClientTickEvents.END_CLIENT_TICK.register(KillAura::onTick);
    }

    // ---- логика -------------------------------------------------------

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            cooldownTicks = 0;
            say(mc, "Kill Aura: " + (enabled ? "ON" : "OFF"));
        }

        LocalPlayer player = mc.player;
        if (!enabled || player == null || mc.level == null || mc.gameMode == null || !player.isAlive()) {
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        LivingEntity target = findTarget(mc, player);
        if (target == null) {
            return;
        }

        if (AIM) {
            aimAt(player, target);
        }

        if (cooldownTicks > 0 || player.getAttackStrengthScale(0.5F) < MIN_CHARGE) {
            return;
        }

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        cooldownTicks = Math.max(1, 20 / Math.max(1, MAX_CPS));
    }

    private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
        LivingEntity best = null;
        double bestDist = RANGE * RANGE;

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
            if (!ATTACK_PLAYERS) {
                return false;
            }
            String name = other.getName().getString().toLowerCase(Locale.ROOT);
            return !CapsAuraClient.friends.contains(name);
        }
        return e instanceof Enemy ? ATTACK_HOSTILE : ATTACK_PASSIVE;
    }

    private static void aimAt(LocalPlayer player, LivingEntity target) {
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
