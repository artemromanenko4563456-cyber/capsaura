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
 * Bow assist: while you hold a drawn bow, the camera aims at the nearest player
 * with gravity compensation and target movement prediction. You still release the bow yourself.
 */
public final class BowAssist {

    // ======== SETTINGS ========
    static boolean enabled = false;
    static double MAX_RANGE = 60.0;      // max distance to a target, blocks
    static boolean ATTACK_FRIENDS = false;
    // ==========================

    private static final int TOGGLE_KEY = GLFW.GLFW_KEY_M;
    private static KeyMapping toggleKey;

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
        if (!enabled || player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW)) {
            return;
        }

        // Arrow speed depends on how long the bow has been drawn
        float power = player.getTicksUsingItem() / 20.0f;
        power = (power * power + power * 2.0f) / 3.0f;
        if (power < 0.1f) {
            return;
        }
        if (power > 1.0f) {
            power = 1.0f;
        }
        double speed = power * 3.0;

        AbstractClientPlayer target = findTarget(mc, player);
        if (target == null) {
            return;
        }

        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        // Target speed per tick (from the previous tick position)
        double vx = target.getX() - target.xo;
        double vy = target.getY() - target.yo;
        double vz = target.getZ() - target.zo;

        double aimHeight = target.getBbHeight() * 0.6;
        double tx = target.getX();
        double ty = target.getY() + aimHeight;
        double tz = target.getZ();

        double flightTicks = 0.0;
        double theta = 0.0;
        // Repeat so that the lead converges
        for (int i = 0; i < 4; i++) {
            double px = tx + vx * flightTicks;
            double py = ty + vy * flightTicks * 0.5;
            double pz = tz + vz * flightTicks;

            double dx = px - eyeX;
            double dz = pz - eyeZ;
            double dy = py - eyeY;
            double dist = Math.sqrt(dx * dx + dz * dz);

            double[] res = solvePitch(dist, dy, speed);
            theta = res[0];
            flightTicks = res[1];
        }

        double px = tx + vx * flightTicks;
        double pz = tz + vz * flightTicks;
        double dx = px - eyeX;
        double dz = pz - eyeZ;

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(theta));

        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private static AbstractClientPlayer findTarget(Minecraft mc, LocalPlayer self) {
        AbstractClientPlayer best = null;
        double bestDist = MAX_RANGE;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            if (!ATTACK_FRIENDS) {
                String name = p.getName().getString().toLowerCase(Locale.ROOT);
                if (CapsAuraClient.friends.contains(name)) {
                    continue;
                }
            }
            double d = self.distanceTo(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
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
                // the first (lowest) good angle is the direct shot
                break;
            }
        }
        return new double[] {bestTheta, bestTime};
    }
}
