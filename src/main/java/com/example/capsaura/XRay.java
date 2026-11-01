package com.example.capsaura;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** "X-Ray": подсветка руд сквозь стены поверх экрана. */
public final class XRay {

    // ======== НАСТРОЙКИ ========
    private static final int RADIUS = 20;            // радиус поиска в блоках
    private static final int SCAN_EVERY_TICKS = 10;  // как часто пересканировать (20 тиков = 1 сек)
    private static final int MAX_ORES = 300;         // максимум рамок на экране
    private static final double LABEL_DISTANCE = 12; // ближе этого пишем название
    // ===========================

    static volatile boolean enabled = false;

    private record OreType(String name, int color) {}
    private record Ore(int x, int y, int z, int color, String name) {}

    private static final OreType NOT_ORE = new OreType(null, 0);
    private static final Map<Block, OreType> typeCache = new HashMap<>();
    private static volatile List<Ore> ores = List.of();
    private static int tickCounter = 0;

    // Базис камеры и параметры экрана, считаются один раз за кадр
    private static double fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, aspect, screenW, screenH;

    private XRay() {}

    /** Оставлено для совместимости со старым миксином: блоки больше не скрываются. */
    public static boolean isHidden(BlockState state) {
        return false;
    }

    static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            ores = List.of();
        }
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(XRay::onTick);
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("capsaura", "ores"),
                XRay::render);
    }

    // ---------- поиск руд ----------

    private static void onTick(Minecraft mc) {
        if (!enabled || mc.player == null || mc.level == null) {
            if (!ores.isEmpty()) {
                ores = List.of();
            }
            return;
        }
        tickCounter++;
        if (tickCounter % SCAN_EVERY_TICKS != 0) {
            return;
        }

        BlockPos center = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Ore> found = new ArrayList<>();

        scan:
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    OreType type = typeOf(state.getBlock());
                    if (type.name() == null) {
                        continue;
                    }
                    found.add(new Ore(pos.getX(), pos.getY(), pos.getZ(), type.color(), type.name()));
                    if (found.size() >= MAX_ORES) {
                        break scan;
                    }
                }
            }
        }
        ores = found;
    }

    private static OreType typeOf(Block block) {
        OreType cached = typeCache.get(block);
        if (cached != null) {
            return cached;
        }
        OreType type = classify(BuiltInRegistries.BLOCK.getKey(block).getPath());
        typeCache.put(block, type);
        return type;
    }

    private static OreType classify(String path) {
        if (path.equals("ancient_debris")) {
            return new OreType("debris", 0xFFB5651D);
        }
        if (!path.endsWith("_ore")) {
            return NOT_ORE;
        }
        String
