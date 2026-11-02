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

    // ------------------------------------------------------------ key

    private static final int OPEN_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static KeyMapping openKey;

    // ------------------------------------------------------------ colors

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

    // ------------------------------------------------------------ sizes

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

    // ------------------------------------------------------------ views drawn over invisible buttons

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

    // ------------------------------------------------------------ screen state

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

    // ------------------------------------------------------------ opening

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

    // ------------------------------------------------------------ layout

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

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int tx = panelX + 6;
            int ty = panelY + HEADER_H + 10 + i * (TAB_H + 2);
            int tw = SIDEBAR_W - 12;
            this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                        currentTab = tab;
                        this.clearWidgets();
                        this.init();
                    })
                    .bounds(tx, ty, tw, TAB_H)
                    .build());
            tabViews.add(new TabView(tx, ty, tw, i, tab));
        }

        switch (currentTab) {
            case COMBAT -> buildCombat();
            case VISUALS -> buildVisuals();
            case WORLD -> buildWorld();
            case PLAYER -> buildPlayer();
            case INFO -> buildInfo();
        }

        closeX = panelX + 6;
        closeY = panelY + panelH - 28;
        closeW = SIDEBAR_W - 12;
        this.addRenderableWidget(Button.builder(Component.empty(), b -> this.onClose())
                .bounds(closeX, closeY, closeW, TAB_H)
                .build());
    }

    private int row(int i) {
        return contentY + i * ROW_H;
    }

    private void buildCombat() {
        int half = (contentW - GAP) / 2;
        addToggle(contentX, row(0), half, "Aura 1.8.9", "Kill aura in the classic 1.8.9 style (key B)",
                () -> CapsAuraClient.enabled,
                v -> { CapsAuraClient.enabled = v; if (v) CritAura.enabled = false; });
        addToggle(contentX + half + GAP, row(0), half, "Aura Crits", "Kill aura that hits in a fall for crits (key N)",
                () -> CritAura.enabled,
                v -> { CritAura.enabled = v; if (v) CapsAuraClient.enabled = false; });
        addSlider(contentX, row(1), contentW, "Range", "Attack distance in blocks",
                MenuConfig.RANGE_MIN, MenuConfig.RANGE_MAX, 1,
                CapsAuraClient.RANGE, v -> CapsAuraClient.RANGE = v);
        addSlider(contentX, row(2), contentW, "Attacks/sec (1.8.9)", "How many hits per second the 1.8.9 aura makes",
                MenuConfig.APS_MIN, MenuConfig.APS_MAX, 0,
                CapsAuraClient.ATTACKS_PER_SECOND, v -> CapsAuraClient.ATTACKS_PER_SECOND = (int) Math.round(v));
        addToggle(contentX, row(3), half, "Players", "Attack other players",
                () -> CapsAuraClient.ATTACK_PLAYERS, v -> CapsAuraClient.ATTACK_PLAYERS = v);
        addToggle(contentX + half + GAP, row(3), half, "Hostile", "Attack hostile mobs",
                () -> CapsAuraClient.ATTACK_HOSTILE, v -> CapsAuraClient.ATTACK_HOSTILE = v);
        addToggle(contentX, row(4), half, "Passive", "Attack animals and villagers",
                () -> CapsAuraClient.ATTACK_PASSIVE, v -> CapsAuraClient.ATTACK_PASSIVE = v);
        addToggle(contentX + half + GAP, row(4), half, "Aim", "Turn the camera towards the target",
                () -> CapsAuraClient.AIM, v -> CapsAuraClient.AIM = v);
        addToggle(contentX, row(5), contentW, "Aura 1.9+ (old)", "Your extra 1.9+ aura",
                () -> KillAura.enabled, v -> KillAura.setEnabled(v));
                addToggle(contentX, row(6), contentW, "Bow Assist", "Aims your bow at the nearest player (key M)",
                () -> BowAssist.enabled, v -> BowAssist.enabled = v);
                addToggle(contentX + half + GAP, row(4), half, "Aim", "Turn towards the target (other players see it)",
                () -> CapsAuraClient.AIM, v -> CapsAuraClient.AIM = v);
    }

    private void buildVisuals() {
        int half = (contentW - GAP) / 2;
        addToggle(contentX, row(0), half, "ESP", "Boxes, names and distance over players (key H)",
                () -> EspOverlay.espOn, v -> EspOverlay.espOn = v);
        addToggle(contentX + half + GAP, row(0), half, "Tracers", "Lines near the crosshair pointing at players (key J)",
                () -> EspOverlay.tracersOn, v -> EspOverlay.tracersOn = v);
        addSlider(contentX, row(1), contentW, "ESP distance", "How far away players are highlighted",
                MenuConfig.ESP_MIN, MenuConfig.ESP_MAX, 0,
                EspOverlay.MAX_DISTANCE, v -> EspOverlay.MAX_DISTANCE = v);
        addPalette(contentX, row(2), contentW, "Enemy color", "Color of the ESP box for regular players",
                () -> EspOverlay.COLOR_ENEMY, c -> EspOverlay.COLOR_ENEMY = c);
        addPalette(contentX, row(3), contentW, "Friend color", "Color of the ESP box for friends",
                () -> EspOverlay.COLOR_FRIEND, c -> EspOverlay.COLOR_FRIEND = c);
    }

    private void buildWorld() {
        addToggle(contentX, row(0), contentW, "Bridge", "Places blocks under you while you run (key G)",
                () -> BridgeAssist.enabled, v -> BridgeAssist.enabled = v);
        addSlider(contentX, row(1), contentW, "Place delay (ticks)", "Pause between placed blocks, 0 = fastest",
                MenuConfig.DELAY_MIN, MenuConfig.DELAY_MAX, 0,
                BridgeAssist.PLACE_DELAY_TICKS, v -> BridgeAssist.PLACE_DELAY_TICKS = (int) Math.round(v));
        addToggle(contentX, row(2), contentW, "X-Ray (ore highlight)", "Highlights ores around you through walls",
                () -> XRay.enabled, v -> XRay.setEnabled(v));
        addText(contentX + 2, row(3) + 6, "Mining bot (type in chat):", C_MUTED);
        addText(contentX + 2, row(3) + 18, ".mine diamond   .mine iron   .mine gold ...", C_TEXT);
        addText(contentX + 2, row(3) + 30, ".stop - stop the bot", C_TEXT);
    }

    private void buildPlayer() {
        addToggle(contentX, row(0), contentW, "Velocity (anti-KB)", "Reduces the knockback you take",
                () -> Velocity.enabled, v -> Velocity.enabled = v);
        addSlider(contentX, row(1), contentW, "Horizontal KB %", "0 = no sideways knockback, 100 = vanilla",
                MenuConfig.PERCENT_MIN, MenuConfig.PERCENT_MAX, 0,
                Velocity.HORIZONTAL, v -> Velocity.HORIZONTAL = Math.round(v));
        addSlider(contentX, row(2), contentW, "Vertical KB %", "0 = no upward knockback, 100 = vanilla",
                MenuConfig.PERCENT_MIN, MenuConfig.PERCENT_MAX, 0,
                Velocity.VERTICAL, v -> Velocity.VERTICAL = Math.round(v));
    }

    private void buildInfo() {
        String[][] keys = {
                {"Right Shift", "Open / close this menu"},
                {"B", "Aura 1.8.9"},
                {"N", "Aura Crits"},
                {"G", "Bridge"},
                {"H", "ESP boxes"},
                {"J", "Tracers"},
                {"Num Enter", "Add / remove friend (look at player)"},
        };
        for (int i = 0; i < keys.length; i++) {
            int y = row(0) + i * 16 + 2;
            addText(contentX + 2, y, keys[i][0], C_ACCENT);
            addText(contentX + 80, y, keys[i][1], C_TEXT);
        }
        addText(contentX + 2, row(0) + keys.length * 16 + 12, "Friends list: config/capsaura-friends.txt", C_MUTED);
    }

    // ------------------------------------------------------------ widget factories

    private void addToggle(int x, int y, int w, String label, String desc,
                           BooleanSupplier getter, Consumer<Boolean> setter) {
        this.addRenderableWidget(Button.builder(Component.empty(), b -> setter.accept(!getter.getAsBoolean()))
                .bounds(x, y, w, BTN_H)
                .build());
        switches.add(new SwitchView(x, y, w, label, desc, getter));
    }

    private void addSlider(int x, int y, int w, String label, String desc, double min, double max, int decimals,
                           double current, DoubleConsumer onChange) {
        ValueSlider slider = new ValueSlider(x, y, w, BTN_H, min, max, decimals, current, onChange);
        this.addRenderableWidget(slider);
        sliders.add(new SliderView(x, y, w, label, desc, slider));
    }

    private void addPalette(int x, int y, int w, String label, String desc, IntSupplier getter, IntConsumer setter) {
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            this.addRenderableWidget(Button.builder(Component.empty(), b -> setter.accept(color))
                    .bounds(paletteX(x, w, i), y + 2, SWATCH, SWATCH)
                    .build());
        }
        palettes.add(new PaletteView(x, y, w, label, desc, getter));
    }

    private void addText(int x, int y, String text, int color) {
        texts.add(new TextView(x, y, text, color));
    }

    private static int paletteX(int x, int w, int i) {
        return x + w - PALETTE.length * SWATCH_STEP + 2 + i * SWATCH_STEP;
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final double min, max;
        private final int decimals;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, double min, double max, int decimals,
                    double current, DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(),
                    Math.max(0.0, Math.min(1.0, (current - min) / (max - min))));
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.onChange = onChange;
        }

        double fraction() {
            return this.value;
        }

        String valueText() {
            return String.format(Locale.ROOT, "%." + decimals + "f", real());
        }

        private double real() {
            return min + (max - min) * this.value;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.empty());
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }

    // ------------------------------------------------------------ background

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, C_BORDER);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, C_HEADER);
        graphics.fill(panelX, panelY + HEADER_H, panelX + SIDEBAR_W, panelY + panelH, C_SIDEBAR);
        graphics.fillGradient(panelX + SIDEBAR_W, panelY + HEADER_H, panelX + panelW, panelY + HEADER_H + 16,
                0x407C5CFF, 0x007C5CFF);
        graphics.fill(panelX + SIDEBAR_W - 1, panelY + HEADER_H, panelX + SIDEBAR_W, panelY + panelH, C_BORDER);

        graphics.fill(panelX + SIDEBAR_W, panelY + panelH - FOOTER_H, panelX + panelW, panelY + panelH, C_HEADER);
        graphics.fill(panelX + SIDEBAR_W, panelY + panelH - FOOTER_H, panelX + panelW,
                panelY + panelH - FOOTER_H + 1, C_BORDER);

        long t = System.currentTimeMillis();
        float shift = (t % 4000L) / 4000.0f;
        for (int dx = 0; dx < panelW; dx += 4) {
            float h = dx / (float) panelW + shift;
            graphics.fill(panelX + dx, panelY, Math.min(panelX + dx + 4, panelX + panelW), panelY + 2, rainbow(h));
        }
    }

    // ------------------------------------------------------------ drawing over widgets

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        float shift = (System.currentTimeMillis() % 3000L) / 3000.0f;
        String title = "CapsAura";
        int tx = panelX + 12;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            graphics.text(this.font, ch, tx, panelY + 11, rainbow(shift + i * 0.08f), true);
            tx += this.font.width(ch);
        }
        graphics.text(this.font, "v1.0.0", tx + 6, panelY + 11, C_MUTED, false);
        String tabTitle = currentTab.title;
        graphics.text(this.font, tabTitle, panelX + panelW - 12 - this.font.width(tabTitle), panelY + 11,
                currentTab.color, false);

        for (TabView t : tabViews) {
            boolean selected = t.tab() == currentTab;
            boolean hover = hover(mouseX, mouseY, t.x(), t.y(), t.w(), TAB_H);
            float a = TAB_ANIM[t.index()];
            a += ((selected ? 1.0f : 0.0f) - a) * 0.3f;
            TAB_ANIM[t.index()] = a;

            int bg = blend(hover ? C_ROW_HOVER : C_SIDEBAR, C_ROW, a);
            graphics.fill(t.x(), t.y(), t.x() + t.w(), t.y() + TAB_H, bg);
            int barW = Math.round(3 * a);
            if (barW > 0) {
                graphics.fill(t.x(), t.y(), t.x() + barW, t.y() + TAB_H, t.tab().color);
            }
            graphics.fill(t.x() + 10, t.y() + 8, t.x() + 16, t.y() + 14, t.tab().color);
            graphics.text(this.font, t.tab().title, t.x() + 22, textY(t.y(), TAB_H),
                    selected ? C_TEXT : C_MUTED, false);
        }

        for (SwitchView s : switches) {
            boolean on = s.getter.getAsBoolean();
            s.anim += ((on ? 1.0f : 0.0f) - s.anim) * 0.35f;

            boolean hover = hover(mouseX, mouseY, s.x, s.y, s.w, BTN_H);
            graphics.fill(s.x, s.y, s.x + s.w, s.y + BTN_H, hover ? C_ROW_HOVER : C_ROW);
            int bar = Math.round(2 * s.anim);
            if (bar > 0) {
                graphics.fill(s.x, s.y, s.x + bar, s.y + BTN_H, C_ACCENT);
            }
            graphics.text(this.font, s.label, s.x + 7, textY(s.y, BTN_H), on ? C_TEXT : C_MUTED, false);

            int trackW = 26;
            int trackH = 12;
            int trackX = s.x + s.w - trackW - 6;
            int trackY = s.y + (BTN_H - trackH) / 2;
            drawPill(graphics, trackX, trackY, trackW, trackH, blend(C_SWITCH_OFF, C_ACCENT, s.anim));
            int knob = trackH - 4;
            int knobX = trackX + 2 + Math.round(s.anim * (trackW - 4 - knob));
            drawPill(graphics, knobX, trackY + 2, knob, knob, 0xFFFFFFFF);
        }

        for (SliderView sv : sliders) {
            boolean hover = hover(mouseX, mouseY, sv.x(), sv.y(), sv.w(), BTN_H);
            graphics.fill(sv.x(), sv.y(), sv.x() + sv.w(), sv.y() + BTN_H, hover ? C_ROW_HOVER : C_ROW);
            graphics.text(this.font, sv.label(), sv.x() + 7, sv.y() + 3, C_TEXT, false);
            String value = sv.slider().valueText();
            graphics.text(this.font, value, sv.x() + sv.w() - 6 - this.font.width(value), sv.y() + 3, C_ACCENT, false);

            int trackX1 = sv.x() + 6;
            int trackX2 = sv.x() + sv.w() - 6;
            int trackY = sv.y() + 14;
            graphics.fill(trackX1, trackY, trackX2, trackY + 3, C_TRACK);
            int fillX = trackX1 + (int) Math.round((trackX2 - trackX1) * sv.slider().fraction());
            graphics.fill(trackX1, trackY, fillX, trackY + 3, C_ACCENT);
            drawPill(graphics, fillX - 3, trackY - 3, 6, 9, 0xFFFFFFFF);
        }

        for (PaletteView pv : palettes) {
            boolean hover = hover(mouseX, mouseY, pv.x(), pv.y(), pv.w(), BTN_H);
            graphics.fill(pv.x(), pv.y(), pv.x() + pv.w(), pv.y() + BTN_H, hover ? C_ROW_HOVER : C_ROW);
            graphics.text(this.font, pv.label(), pv.x() + 7, textY(pv.y(), BTN_H), C_TEXT, false);

            int current = pv.getter().getAsInt() | 0xFF000000;
            for (int i = 0; i < PALETTE.length; i++) {
                int sx = paletteX(pv.x(), pv.w(), i);
                int sy = pv.y() + 2;
                boolean selected = (PALETTE[i] | 0xFF000000) == current;
                graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, selected ? 0xFFFFFFFF : C_ROW);
                graphics.fill(sx + 2, sy + 2, sx + SWATCH - 2, sy + SWATCH - 2, PALETTE[i] | 0xFF000000);
            }
        }

        for (TextView tv : texts) {
            graphics.text(this.font, tv.text(), tv.x(), tv.y(), tv.color(), false);
        }

        graphics.text(this.font, "Active: " + activeCount(), panelX + 12, closeY - 14, C_MUTED, false);
        boolean closeHover = hover(mouseX, mouseY, closeX, closeY, closeW, TAB_H);
        graphics.fill(closeX, closeY, closeX + closeW, closeY + TAB_H, closeHover ? C_ACCENT : C_ROW);
        String close = "Close";
        graphics.text(this.font, close, closeX + (closeW - this.font.width(close)) / 2, textY(closeY, TAB_H),
                C_TEXT, false);

        String hint = hoverHint(mouseX, mouseY);
        graphics.text(this.font, hint == null ? "Hover a setting to see what it does" : hint,
                contentX, panelY + panelH - FOOTER_H + 9, hint == null ? C_MUTED : C_TEXT, false);
    }

    private String hoverHint(int mx, int my) {
        for (SwitchView s : switches) {
            if (hover(mx, my, s.x, s.y, s.w, BTN_H)) return s.desc;
        }
        for (SliderView sv : sliders) {
            if (hover(mx, my, sv.x(), sv.y(), sv.w(), BTN_H)) return sv.desc();
        }
        for (PaletteView pv : palettes) {
            if (hover(mx, my, pv.x(), pv.y(), pv.w(), BTN_H)) return pv.desc();
        }
        return null;
    }

    private static int activeCount() {
        int n = 0;
        if (CapsAuraClient.enabled) n++;
        if (CritAura.enabled) n++;
        if (KillAura.enabled) n++;
        if (BridgeAssist.enabled) n++;
        if (EspOverlay.espOn) n++;
        if (EspOverlay.tracersOn) n++;
        if (XRay.enabled) n++;
        if (Velocity.enabled) n++;
        return n;
    }

    // ------------------------------------------------------------ helpers

    private int textY(int y, int h) {
        return y + (h - this.font.lineHeight) / 2 + 1;
    }

    private static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void drawPill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int rainbow(float h) {
        double p = h * Math.PI * 2.0;
        int r = (int) (127.5 + 127.5 * Math.sin(p));
        int g = (int) (127.5 + 127.5 * Math.sin(p + Math.PI * 2.0 / 3.0));
        int b = (int) (127.5 + 127.5 * Math.sin(p + Math.PI * 4.0 / 3.0));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClose() {
        MenuConfig.save();
        super.onClose();
    }
}
