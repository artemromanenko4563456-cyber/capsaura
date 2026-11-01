package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class CapsAuraScreen extends Screen {

    // ------------------------------------------------------------ константи

    private static final int OPEN_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static KeyMapping openKey;

    private static final int COL_BORDER  = 0xFF2A2A3A;
    private static final int COL_PANEL   = 0xF0141420;
    private static final int COL_SIDEBAR = 0xFF0E0E16;
    private static final int COL_ACCENT  = 0xFF7C5CFF;
    private static final int COL_TEXT    = 0xFFFFFFFF;
    private static final int COL_MUTED   = 0xFF8A8AA0;
    private static final int COL_SWITCH_OFF = 0xFF3A3A4A;
    private static final int COL_ROW     = 0xFF1A1A26;
    private static final int COL_ROW_HOVER = 0xFF24243A;

    private static final int[] PALETTE = {
            0xFFFF4040, 0xFFFFA040, 0xFFFFE040, 0xFF40FF60,
            0xFF40C8FF, 0xFF6080FF, 0xFFC060FF, 0xFFFFFFFF
    };

    // Межі повзунків
    private static final double RANGE_MIN = 1.0, RANGE_MAX = 6.0;
    private static final double APS_MIN = 1, APS_MAX = 20;
    private static final double ESP_MIN = 16, ESP_MAX = 128;
    private static final double DELAY_MIN = 0, DELAY_MAX = 10;
    private static final double PERCENT_MIN = 0, PERCENT_MAX = 100;

    private static final int SIDEBAR_W = 90;
    private static final int BTN_H = 20;
    private static final int GAP = 4;
    private static final int ROW_H = 24;

    private enum Tab {
        COMBAT("Combat"), VISUALS("Visuals"), WORLD("World"), PLAYER("Player");
        final String title;
        Tab(String title) { this.title = title; }
    }

    private record Swatch(int x, int y, IntSupplier color) {}

    /** Переключатель-ползунок: рисуется поверх невидимой кнопки. */
    private static final class SwitchView {
        final int x, y, w;
        final String label;
        final BooleanSupplier getter;
        float anim; // 0 = выключено, 1 = включено

        SwitchView(int x, int y, int w, String label, BooleanSupplier getter) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.label = label;
            this.getter = getter;
            this.anim = getter.getAsBoolean() ? 1.0f : 0.0f;
        }
    }

    private static Tab currentTab = Tab.COMBAT;

    // ------------------------------------------------------- стан екрана

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW;
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<SwitchView> switches = new ArrayList<>();

    public CapsAuraScreen() {
        super(Component.literal("CapsAura"));
    }

    // ------------------------------------------------------------ клавіша

    public static void init(KeyMapping.Category category) {
        loadConfig();
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.capsaura.menu", InputConstants.Type.KEYSYM, OPEN_KEY, category));
        ClientTickEvents.END_CLIENT_TICK.register(CapsAuraScreen::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (openKey.consumeClick()) {
            if (mc.player != null) {
                mc.setScreenAndShow(new CapsAuraScreen());
            }
        }
    }

    // -------------------------------------------------------------
