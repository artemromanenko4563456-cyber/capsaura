package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Bow assist: while you hold a drawn bow, the camera aims at a player with gravity
 * compensation and smoothed target movement prediction. You release the bow yourself.
 */
public final class BowAssist {

    // ======== SETTINGS ========
    static boolean enabled = false;
    static double MAX_RANGE = 60.0;       // max distance to a target, blocks
    static boolean ATTACK_FRIENDS = false;
    static double LEAD = 1.0;             // how strongly to lead a moving target (0 = no lead)
    static double MAX_STEP_YAW = 40.0;    // max camera turn per tick, degrees
    static double MAX_STEP_PITCH = 30.0;
    // ==========================

    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_M;
    private static KeyMapping toggleKey;

    // locked target and its smoothed speed (blocks per tick)
    private static AbstractClientPlayer locked = null;
    private static double svx = 0.0;
    private static double svz = 0.0;

    private BowAssist() {}

    public static void init(KeyMapping.Category category) {
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.capsaura.bow", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));
        ClientTickEvents.END_CLIENT_TICK.register(BowAssist::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("Bow Assist: " + (enabled ? "ON" : "OFF")));
            }
        }

        LocalPlayer player = mc.player;
        if (!enabled || player == null || mc.level == null) {
            locked = null;
            return;
        }
        if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW)) {
            locked = null;
            return;
        }

        // Aim as if the bow is fully drawn, so the angle does not jump while drawing
        double speed = 3.0;

        AbstractClientPlayer target = pickTarget(mc, player);
        if (target == null) {
            return;
        }

        // Smoothed target speed (a raw per-tick delta is too noisy and causes "circling")
        double rawVx = clampSpeed(target.getX() - target.xo);
        double rawVz = clampSpeed(target.getZ() - target.zo);
        svx = svx * 0.8 + rawVx * 0.2;
        svz = svz * 0.8 + rawVz * 0.2;

        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        double tx = target.getX();
        double ty = target.getY() + target.getBbHeight() * 0.6;
        double tz = target.getZ();

        double flightTicks = 0.0;
        double theta = 0.0;
        for (int i = 0; i < 4; i++) {
            double px = tx + svx * flightTicks * LEAD;
            double pz = tz + svz * flightTicks * LEAD;
            double dx = px - eyeX;
            double dz = pz - eyeZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double[] res = solvePitch(dist, ty - eyeY, speed);
            theta = res[0];
            flightTicks = Math.min(res[1], 40.0);
        }

        double px = tx + svx * flightTicks * LEAD;
        double pz = tz + svz * flightTicks * LEAD;
        double dx = px - eyeX;
        double dz = pz - eyeZ;

        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) (-Math.toDegrees(theta));

        // Turn towards the target by the shortest path with a step limit
        float yawDiff = wrap(wantYaw - player.getYRot());
        float pitchDiff = wantPitch - player.getXRot();
        yawDiff = (float) Math.max(-MAX_STEP_YAW, Math.min(MAX_STEP_YAW, yawDiff));
        pitchDiff = (float) Math.max(-MAX_STEP_PITCH, Math.min(MAX_STEP_PITCH, pitchDiff));

        player.setYRot(player.getYRot() + yawDiff);
        player.setXRot(player.getXRot() + pitchDiff);
    }

    private static double clampSpeed(double v) {
        return Math.max(-0.8, Math.min(0.8, v));
    }

    private static float wrap(float deg) {
        while (deg > 180.0f) deg -= 360.0f;
        while (deg < -180.0f) deg += 360.0f;
        return deg;
    }

    /** Keeps the same target until it dies or goes out of range, so the aim does not jump between players. */
    private static AbstractClientPlayer pickTarget(Minecraft mc, LocalPlayer self) {
        if (locked != null && isValid(locked, self)) {
            return locked;
        }

        AbstractClientPlayer best = null;
        double bestDist = MAX_RANGE;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!isValid(p, self)) {
                continue;
            }
            double d = self.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best != locked) {
            svx = 0.0;
            svz = 0.0;
        }
        locked = best;
        return best;
    }

    private static boolean isValid(AbstractClientPlayer p, LocalPlayer self) {
        if (p == self || !p.isAlive() || p.isSpectator()) {
            return false;
        }
        if (self.distanceTo(p) > MAX_RANGE) {
            return false;
        }
        if (!ATTACK_FRIENDS) {
            String name = p.getName().getString().toLowerCase(Locale.ROOT);
            if (CapsAuraClient.friends.contains(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simulates an arrow (moves, then drag 0.99, then gravity 0.05) and finds the launch angle
     * (radians, up is positive) that hits the point at horizontal distance dist and height dy.
     * Returns {angle, flight time in ticks}. Prefers the low (direct) trajectory.
     */
    private static double[] solvePitch(double dist, double dy, double speed) {
        double bestTheta = Math.atan2(dy, Math.max(dist, 0.001));
        double bestTime = dist / Math.max(speed, 0.1);
        double bestErr = Double.MAX_VALUE;

        for (double deg = -89.0; deg <= 89.0; deg += 0.25) {
            double th = Math.toRadians(deg);
            double mx = Math.cos(th) * speed;
            double my = Math.sin(th) * speed;
            double x = 0.0;
            double y = 0.0;
            int ticks = 0;
            boolean reached = false;
            double hitY = 0.0;
            double hitT = 0.0;

            while (ticks < 400) {
                double nx = x + mx;
                double ny = y + my;
                ticks++;
                if (nx >= dist) {
                    double f = mx > 0.0001 ? (dist - x) / (nx - x) : 0.0;
                    hitY = y + (ny - y) * f;
                    hitT = ticks - 1 + f;
                    reached = true;
                    break;
                }
                x = nx;
                y = ny;
                mx *= 0.99;
                my *= 0.99;
                my -= 0.05;
                if (mx < 0.01) {
                    break;
                }
            }

            if (!reached) {
                continue;
            }
            double err = Math.abs(hitY - dy);
            if (err < bestErr) {
                bestErr = err;
                bestTheta = th;
                bestTime = hitT;
            }
            if (err < 0.15) {
                break;
            }
        }
        return new double[] {bestTheta, bestTime};
    }
}
