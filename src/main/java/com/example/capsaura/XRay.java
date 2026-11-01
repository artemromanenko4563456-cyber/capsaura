package com.example.capsaura;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class XRay {

    static volatile boolean enabled = false;
    private static volatile Set<Block> hidden = Set.of();

    // Блоки, которые скрываем. Всё остальное (руды, сундуки и т.д.) остаётся видимым.
    private static final Set<String> HIDDEN_NAMES = new HashSet<>(List.of(
            "stone", "deepslate", "cobblestone", "mossy_cobblestone", "cobbled_deepslate",
            "dirt", "grass_block", "coarse_dirt", "podzol", "mycelium", "rooted_dirt", "mud", "clay",
            "gravel", "sand", "red_sand", "sandstone", "red_sandstone", "snow_block",
            "granite", "diorite", "andesite", "tuff", "calcite", "dripstone_block",
            "netherrack", "basalt", "smooth_basalt", "blackstone", "soul_sand", "soul_soil",
            "end_stone", "terracotta"
    ));

    private XRay() {}

    /** Вызывается из миксина (в том числе из потоков сборки чанков). */
    public static boolean isHidden(BlockState state) {
        return enabled && hidden.contains(state.getBlock());
    }

    static void setEnabled(boolean on) {
        if (on && hidden.isEmpty()) {
            hidden = buildHiddenSet();
        }
        enabled = on;
        refresh();
    }

    private static Set<Block> buildHiddenSet() {
        Set<Block> set = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            if (HIDDEN_NAMES.contains(path)) {
                set.add(block);
            }
        }
        return set;
    }

    /** Перестраивает чанки и отключает/включает отсечение пещер. */
    private static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        setSmartCull(mc, !enabled);
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    // Без отключения "cave culling" глубокие руды не рисуются. Поле может называться иначе - тогда просто пропускаем.
    private static void setSmartCull(Minecraft mc, boolean value) {
        try {
            Field f = Minecraft.class.getDeclaredField("smartCull");
            f.setAccessible(true);
            f.setBoolean(mc, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}
