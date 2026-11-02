package com.example.capsaura;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Тихая ротация: камера у тебя не двигается, а серверу (и другим игрокам)
 * уходит пакет с поворотом на цель.
 * Поворот удерживается несколько тиков и пересылается каждый тик,
 * чтобы сервер успел разослать его другим игрокам.
 */
public final class SilentRotation {
    /** Сколько тиков держать поворот после последнего наведения. */
    private static final int HOLD_TICKS = 5;

    private static boolean spoofing;
    private static int lastAimTick;
    private static float spoofYaw;
    private static float spoofPitch;

    private SilentRotation() {}

    /** Вызвать один раз из CapsAuraClient.onInitializeClient(). */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SilentRotation::onEndTick);
    }

    /** Считает углы на цель и шлёт их серверу, не трогая камеру. */
    public static void aimAt(LocalPlayer player, LivingEntity target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch));

        spoofYaw = yaw;
        spoofPitch = pitch;
        spoofing = true;
        lastAimTick = player.tickCount;
        send(player, yaw, pitch);
    }

    private static void onEndTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!spoofing || player == null) {
            spoofing = false;
            return;
        }
        int age = player.tickCount - lastAimTick;
        if (age < 0 || age >= HOLD_TICKS) {
            // аура давно не целилась: возвращаем серверу настоящий поворот камеры
            send(player, player.getYRot(), player.getXRot());
            spoofing = false;
        } else if (age > 0) {
            // держим поворот, чтобы он дошёл до других игроков
            send(player, spoofYaw, spoofPitch);
        }
    }

    private static void send(LocalPlayer player, float yaw, float pitch) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround(), player.horizontalCollision));
    }
}
