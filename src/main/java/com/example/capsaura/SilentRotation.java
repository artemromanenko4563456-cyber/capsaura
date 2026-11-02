package com.example.capsaura;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Тихая ротация: камера у тебя не двигается, а серверу (и другим игрокам)
 * уходит пакет с поворотом на цель.
 * Поворот плавный: за тик голова доворачивается не больше чем на MAX_YAW_STEP градусов,
 * поэтому другие игроки видят движение, а не резкий рывок.
 */
public final class SilentRotation {
    /** true: камера у тебя на экране тоже крутится на цель. false: крутится только у других. */
    public static boolean cameraFollow = false;

    /** true: плавный доворот (шагами). false: мгновенный поворот на цель. */
    public static boolean smooth = true;

    /** Сколько тиков держать поворот после последнего наведения. */
    private static final int HOLD_TICKS = 5;
    /** Максимальный доворот за тик (градусы). Меньше = плавнее, но медленнее. */
    public static float MAX_YAW_STEP = 12.0F;
    public static float MAX_PITCH_STEP = 8.0F;

    private static boolean spoofing;
    private static int lastAimTick = Integer.MIN_VALUE;
    private static float curYaw;
    private static float curPitch;

    private SilentRotation() {}

    private static float yawStep() {
        return smooth ? MAX_YAW_STEP : 360.0F;
    }

    private static float pitchStep() {
        return smooth ? MAX_PITCH_STEP : 360.0F;
    }

    /** Вызвать один раз из CapsAuraClient.onInitializeClient(). */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SilentRotation::onEndTick);
    }

    /** Плавно доворачивает серверный поворот на цель, не трогая камеру (если cameraFollow выключен). */
    public static void aimAt(LocalPlayer player, LivingEntity target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        targetPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);

        // начинаем от настоящего поворота камеры, если до этого не целились
        if (!spoofing) {
            curYaw = player.getYRot();
            curPitch = player.getXRot();
        }

        float dYaw = Mth.clamp(Mth.wrapDegrees(targetYaw - curYaw), -yawStep(), yawStep());
        float dPitch = Mth.clamp(targetPitch - curPitch, -pitchStep(), pitchStep());
        curYaw += dYaw;
        curPitch = Mth.clamp(curPitch + dPitch, -90.0F, 90.0F);

        if (cameraFollow) {
            player.setYRot(curYaw);
            player.setXRot(curPitch);
        }

        spoofing = true;
        lastAimTick = player.tickCount;
        send(player, curYaw, curPitch);
    }

    private static void onEndTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!spoofing || player == null) {
            spoofing = false;
            return;
        }
        int age = player.tickCount - lastAimTick;
        if (age < 0 || age >= HOLD_TICKS) {
            // аура давно не целилась: плавно возвращаем серверу настоящий поворот камеры
            float realYaw = player.getYRot();
            float realPitch = player.getXRot();
            float dYaw = Mth.clamp(Mth.wrapDegrees(realYaw - curYaw), -yawStep(), yawStep());
            float dPitch = Mth.clamp(realPitch - curPitch, -pitchStep(), pitchStep());
            curYaw += dYaw;
            curPitch = Mth.clamp(curPitch + dPitch, -90.0F, 90.0F);
            send(player, curYaw, curPitch);

            boolean done = Math.abs(Mth.wrapDegrees(realYaw - curYaw)) < 0.5F
                    && Math.abs(realPitch - curPitch) < 0.5F;
            if (done) {
                send(player, realYaw, realPitch);
                spoofing = false;
            }
        } else if (age > 0) {
            // держим поворот, чтобы он дошёл до других игроков
            send(player, curYaw, curPitch);
        }
    }

    private static void send(LocalPlayer player, float yaw, float pitch) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround(), player.horizontalCollision));
    }
}
