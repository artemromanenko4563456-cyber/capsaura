package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class CapsAuraScreen extends Screen {

    // ------------------------------------------------------------ клавиша

    private static final int OPEN_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static KeyMapping openKey;

    // ------------------------------------------------------------ цвета

    private static final int C_BORDER = 0xFF2A2A3A;
    private static final int C_PANEL = 0xFF101018;
    private static final int C_HEADER = 0xFF14141F;
    private static final int C_SIDEBAR = 0xFF0B0B12;
    private static final int C_ROW = 0xFF181824;
    private static final int C_ROW_HOVER = 0xFF232338;
    private static final int C_TRACK = 0xFF2B2B3D;
    private static final int C_ACCENT = 0xFF7C5CFF;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFF8A8AA0;
    private static final int C_SWITCH_OFF = 0xFF3A3A4A;

    private static final int[] PALETTE = {
            0xFFFF4040, 0xFFFFA040, 0xFFFFE040, 0xFF40FF60,
            0xFF40C8FF, 0xFF6080FF, 0xFFC060FF, 0xFFFFFFFF
    };

    // ------------------------------------------------------------ размеры

    private static final int HEADER_H = 30;
    private static final int SIDEBAR_W = 98;
    private static final int PAD = 12;
    private static final int ROW_H = 24;
    private static final int BTN_H = 20;
    private static final int GAP = 4;
    private static final int TAB_H = 22;
    private static final int SWATCH = 16;
    private static final int SWATCH_STEP = 18;
    private static final int FOOTER_H = 26;

    private enum Tab {
        COMBAT("Combat", 0xFFFF5C7C),
        VISUALS("Visuals", 0xFF40C8FF),
        WORLD("World", 0xFF40FF60),
        PLAYER("Player", 0xFFFFA040),
        INFO("Info", 0xFFC060FF);

        final String title;
        final int color;

        Tab(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    private static Tab currentTab = Tab.COMBAT;
    private static final float[] TAB_ANIM = new float[Tab.values().length];
    private static boolean animReady = false;

    // ------------------------------------------------------------ картинки поверх невидимых кнопок

    private record TabView(int x, int y, int w, int index, Tab tab) {}

    private record SliderView(int x, int y, int w, String label, String desc, ValueSlider slider) {}

    private record PaletteView(int x, int y, int w, String label, String desc, IntSupplier getter) {}

    private record TextView(int x, int y, String text, int color) {}

    private static final class SwitchView {
        final int x, y, w;
        final String label, desc;
        final BooleanSupplier getter;
        float anim;

        SwitchView(int x, int y, int w, String label, String desc, BooleanSupplier getter) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.label = label;
            this.desc = desc;
            this.getter = getter;
            this.anim = getter.getAsBoolean() ? 1.0f : 0.0f;
        }
    }

    // ------------------------------------------------------------ состояние экрана

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW;
    private int closeX, closeY, closeW;

    private final List<TabView> tabViews = new ArrayList<>();
    private final List<SwitchView> switches = new ArrayList<>();
    private final List<SliderView> sliders = new ArrayList<>();
    private final List<PaletteView> palettes = new ArrayList<>();
    private final List<TextView> texts = new ArrayList<>();

    public CapsAuraScreen() {
        super(Component.literal("CapsAura"));
    }

    // ------------------------------------------------------------ открытие меню

    public static void init(KeyMapping.Category category) {
        MenuConfig.load();
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

    // ------------------------------------------------------------ раскладка

    @Override
    protected void init() {
        tabViews.clear();
        switches.clear();
        sliders.clear();
        palettes.clear();
        texts.clear();

        panelW = Math.min(this.width - 20, 440);
        panelH = Math.min(this.height - 20, 270);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        contentX = panelX + SIDEBAR_W + PAD;
        contentW = panelW - SIDEBAR_W - PAD * 2;
        contentY = panelY + HEADER_H + 14;

        if (!animReady) {
            for (Tab t : Tab.values()) {
                TAB_ANIM[t.ordinal()] = (t == currentTab) ? 1.0f : 0.0f;
            }
            animReady = true;
        }

        Tab[]
