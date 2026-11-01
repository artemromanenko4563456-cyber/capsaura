package com.example.capsaura;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Velocity {

    static boolean enabled = false;
    static double HORIZONTAL = 0.0; // сколько % горизонтального отбрасывания оставить (0-100)
    static double VERTICAL = 0.0;   // сколько % вертикального отбрасывания оставить (0-100)

    private static final ThreadLocal<Vec3> BEFORE = new ThreadLocal<>();

    private Velocity() {}

    /** Вызывается перед обработкой пакета скорости. */
    public static void before() {
        if (!enabled) return;
        LocalPlayer p = Minecraft.getInstance().player;
        BEFORE.set(p == null ? null : p.getDeltaMovement());
    }

    /** Вызывается после обработки пакета скорости. */
    public static void after() {
        Vec3 old = BEFORE.get();
        BEFORE.remove();
        if (!enabled || old == null) return;

        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;

        Vec3 now = p.getDeltaMovement();
        if (now.equals(old)) return; // пакет был не про нас

        double h = HORIZONTAL / 100.0;
        double v = VERTICAL / 100.0;
        p.setDeltaMovement(old.add(now.subtract(old).multiply(h, v, h)));
    }
}
