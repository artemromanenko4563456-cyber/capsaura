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

/** Киллаура для боёвки 1.9+: бьёт на полном кулдауне и в падении (критический удар). */
public final class CritAura {

    // ======== НАСТРОЙКИ ========
    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_N; // «Т» на русской раскладке
    private static final boolean AUTO_JUMP = true;          // сама прыгает, чтобы бить в падении
    private static final int MAX_WAIT_TICKS = 15;           // не получился крит - бить обычным ударом
    // ===========================

    static boolean enabled = false;

    private static KeyMapping toggleKey;
    private static int waitTicks = 0;
    private static boolean jumpHeld = false;

    private CritAura() {}

    public static void init(KeyMapping.Category category) {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.capsaura.crit", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));
        ClientTickEvents.END_CLIENT_TICK.register(CritAura::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            waitTicks = 0;
            if (enabled) {
                CapsAuraClient.enabled = false; // две ауры сразу не нужны
            } else {
                releaseJump(mc);
            }
            say(mc, "Crit Aura: " + (enabled ? "ON" : "OFF"));
        }

        if (!enabled || mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseJump(mc);
            return;
        }

        LocalPlayer p = mc.player;
        if (!p.isAlive()) {
            releaseJump(mc);
            return;
        }

        LivingEntity target = findTarget(mc, p);
        if (target == null) {
            waitTicks = 0;
            releaseJump(mc);
            return;
        }

        if (CapsAuraClient.AIM) {
            aimAt(p, target);
        }

        boolean ready = p.getAttackStrengthScale(0.5f) >= 1.0f;
        boolean falling = !p.onGround() && p.getDeltaMovement().y < 0
                && !p.isInWater() && !p.onClimbable() && !p.isSprinting();

        // Прыжок, когда кулдаун готов и мы стоим на земле
        if (AUTO_JUMP && p.onGround() && ready) {
            mc.options.keyJump.setDown(true);
            jumpHeld = true;
        } else {
            releaseJump(mc);
        }

        if (!ready) {
            waitTicks = 0;
            return;
        }

        waitTicks++;
        if (falling || waitTicks > MAX_WAIT_TICKS) {
            mc.gameMode.attack(p, target);
            p.swing(InteractionHand.MAIN_HAND);
            waitTicks = 0;
        }
    }

    private static void releaseJump(Minecraft mc) {
        if (jumpHeld && mc.options != null) {
            mc.options.keyJump.setDown(false);
        }
        jumpHeld = false;
    }

    // ---------- цели (те же настройки, что у обычной ауры) ----------

    private static LivingEntity findTarget(Minecraft mc, LocalPlayer player) {
        LivingEntity best = null;
        double bestDist = CapsAuraClient.RANGE * CapsAuraClient.RANGE;

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
            if (!CapsAuraClient.ATTACK_PLAYERS) return false;
            return !CapsAuraClient.friends.contains(e.getName().getString().toLowerCase(Locale.ROOT));
        }
        if (e instanceof Enemy) {
            return CapsAuraClient.ATTACK_HOSTILE;
        }
        return CapsAuraClient.ATTACK_PASSIVE;
    }

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
