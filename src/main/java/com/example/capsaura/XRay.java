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
        String name = path.substring(0, path.length() - 4).replace("deepslate_", "").replace("nether_", "");
        int color;
        if (path.contains("diamond")) color = 0xFF40E0FF;
        else if (path.contains("emerald")) color = 0xFF40FF60;
        else if (path.contains("gold")) color = 0xFFFFD040;
        else if (path.contains("iron")) color = 0xFFE0B090;
        else if (path.contains("coal")) color = 0xFFAAAAAA;
        else if (path.contains("redstone")) color = 0xFFFF3030;
        else if (path.contains("lapis")) color = 0xFF4060FF;
        else if (path.contains("copper")) color = 0xFFE07040;
        else if (path.contains("quartz")) color = 0xFFFFFFFF;
        else color = 0xFFFFFF55;
        return new OreType(name, color);
    }

    // ---------- отрисовка ----------

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!enabled) {
            return;
        }
        List<Ore> list = ores;
        if (list.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null) {
            return;
        }

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        setupCamera(mc, self, partial);
        Vec3 eye = self.getEyePosition(partial);

        for (Ore ore : list) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            boolean any = false;

            for (int ix = 0; ix <= 1; ix++) {
                for (int iy = 0; iy <= 1; iy++) {
                    for (int iz = 0; iz <= 1; iz++) {
                        double[] p = project(ore.x() + ix - eye.x, ore.y() + iy - eye.y, ore.z() + iz - eye.z);
                        if (p == null) {
                            continue;
                        }
                        any = true;
                        minX = Math.min(minX, p[0]);
                        maxX = Math.max(maxX, p[0]);
                        minY = Math.min(minY, p[1]);
                        maxY = Math.max(maxY, p[1]);
                    }
                }
            }
            if (!any) {
                continue;
            }

            int x = (int) Math.floor(minX);
            int y = (int) Math.floor(minY);
            int w = Math.max(2, (int) Math.ceil(maxX - minX));
            int h = Math.max(2, (int) Math.ceil(maxY - minY));
            graphics.outline(x, y, w, h, ore.color());

            double dx = ore.x() + 0.5 - eye.x;
            double dy = ore.y() + 0.5 - eye.y;
            double dz = ore.z() + 0.5 - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= LABEL_DISTANCE) {
                String label = String.format(Locale.ROOT, "%s %.0fm", ore.name(), dist);
                int textW = mc.font.width(label);
                graphics.text(mc.font, label, x + w / 2 - textW / 2, y - 10, ore.color(), true);
            }
        }
    }

    private static void setupCamera(Minecraft mc, LocalPlayer self, float partial) {
        double yaw = Math.toRadians(self.getViewYRot(partial));
        double pitch = Math.toRadians(self.getViewXRot(partial));
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);

        fx = -sy * cp;
        fy = -sp;
        fz = cy * cp;
        rx = -cy;
        rz = -sy;
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;

        screenW = mc.getWindow().getGuiScaledWidth();
        screenH = mc.getWindow().getGuiScaledHeight();
        aspect = screenW / screenH;
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
    }

    private static double[] project(double dx, double dy, double dz) {
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.05) {
            return null;
        }
        double x = dx * rx + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        double nx = x / (z * tanHalf * aspect);
        double ny = y / (z * tanHalf);
        return new double[] { screenW / 2.0 + nx * screenW / 2.0, screenH / 2.0 - ny * screenH / 2.0 };
    }
}
