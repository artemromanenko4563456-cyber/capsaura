package com.example.capsaura;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Тихая ротация: камера у тебя не двигается, а серверу (и другим игрокам)
 * уходит пакет с поворотом на цель прямо перед ударом.
 * Подходит для обеих аур: CapsAuraClient (по CPS) и KillAura (по заряду, 1.9+).
 */
public final class SilentRotation {
    private static boolean spoofing;
    private static int lastAimTick;

    private SilentRotation() {}

    /** Вызвать один раз из CapsAuraClient.onInitializeClient(). */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SilentRotation::onEndTick);
    }

    /** Замена старого aimAt: считает углы на цель и шлёт их серверу, не трогая камеру. */
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

        send(player, yaw, pitch);
        spoofing = true;
        lastAimTick = player.tickCount;
    }

    /** Если аура 2 тика не целилась, возвращаем серверу настоящий поворот камеры. */
    private static void onEndTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!spoofing || player == null) {
            spoofing = false;
            return;
        }
        int age = player.tickCount - lastAimTick;
        if (age < 0 || age >= 2) {
            send(player, player.getYRot(), player.getXRot());
            spoofing = false;
        }
    }

    private static void send(LocalPlayer player, float yaw, float pitch) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround(), player.horizontalCollision));
    }
}
