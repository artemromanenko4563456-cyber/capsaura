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

    private static Tab currentTab = Tab.COMBAT;

    // ------------------------------------------------------- стан екрана

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW;
    private final List<Swatch> swatches = new ArrayList<>();

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

    // ------------------------------------------------------------- конфіг

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("capsaura.properties");
    }

    private static void loadConfig() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        CapsAuraClient.RANGE = clamp(getD(p, "range", CapsAuraClient.RANGE), RANGE_MIN, RANGE_MAX);
        CapsAuraClient.ATTACKS_PER_SECOND =
                (int) clamp(getI(p, "attacksPerSecond", CapsAuraClient.ATTACKS_PER_SECOND), APS_MIN, APS_MAX);
        CapsAuraClient.ATTACK_PLAYERS = getB(p, "attackPlayers", CapsAuraClient.ATTACK_PLAYERS);
        CapsAuraClient.ATTACK_HOSTILE = getB(p, "attackHostile", CapsAuraClient.ATTACK_HOSTILE);
        CapsAuraClient.ATTACK_PASSIVE = getB(p, "attackPassive", CapsAuraClient.ATTACK_PASSIVE);
        CapsAuraClient.AIM = getB(p, "aim", CapsAuraClient.AIM);

        EspOverlay.MAX_DISTANCE = clamp(getD(p, "espDistance", EspOverlay.MAX_DISTANCE), ESP_MIN, ESP_MAX);
        EspOverlay.COLOR_ENEMY = getI(p, "colorEnemy", EspOverlay.COLOR_ENEMY);
        EspOverlay.COLOR_FRIEND = getI(p, "colorFriend", EspOverlay.COLOR_FRIEND);

        BridgeAssist.PLACE_DELAY_TICKS =
                (int) clamp(getI(p, "placeDelay", BridgeAssist.PLACE_DELAY_TICKS), DELAY_MIN, DELAY_MAX);

        Velocity.HORIZONTAL = clamp(getD(p, "velocityH", Velocity.HORIZONTAL), PERCENT_MIN, PERCENT_MAX);
        Velocity.VERTICAL = clamp(getD(p, "velocityV", Velocity.VERTICAL), PERCENT_MIN, PERCENT_MAX);
    }

    private static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("range", String.valueOf(CapsAuraClient.RANGE));
        p.setProperty("attacksPerSecond", String.valueOf(CapsAuraClient.ATTACKS_PER_SECOND));
        p.setProperty("attackPlayers", String.valueOf(CapsAuraClient.ATTACK_PLAYERS));
        p.setProperty("attackHostile", String.valueOf(CapsAuraClient.ATTACK_HOSTILE));
        p.setProperty("attackPassive", String.valueOf(CapsAuraClient.ATTACK_PASSIVE));
        p.setProperty("aim", String.valueOf(CapsAuraClient.AIM));
        p.setProperty("espDistance", String.valueOf(EspOverlay.MAX_DISTANCE));
        p.setProperty("colorEnemy", String.valueOf(EspOverlay.COLOR_ENEMY));
        p.setProperty("colorFriend", String.valueOf(EspOverlay.COLOR_FRIEND));
        p.setProperty("placeDelay", String.valueOf(BridgeAssist.PLACE_DELAY_TICKS));
        p.setProperty("velocityH", String.valueOf(Velocity.HORIZONTAL));
        p.setProperty("velocityV", String.valueOf(Velocity.VERTICAL));
        try (OutputStream out = Files.newOutputStream(configPath())) {
            p.store(out, "CapsAura settings");
        } catch (IOException ignored) {
        }
    }

    private static double getD(Properties p, String key, double def) {
        try { return Double.parseDouble(p.getProperty(key)); } catch (Exception e) { return def; }
    }

    private static int getI(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key)); } catch (Exception e) { return def; }
    }

    private static boolean getB(Properties p, String key, boolean def) {
        String s = p.getProperty(key);
        return s == null ? def : Boolean.parseBoolean(s);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------- розмітка

    @Override
    protected void init() {
        swatches.clear();

        panelW = Math.min(this.width - 20, 400);
        panelH = Math.min(this.height - 20, 240);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        contentX = panelX + SIDEBAR_W + 14;
        contentW = panelW - SIDEBAR_W - 28;
        contentY = panelY + 44;

        // Вкладки зліва
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            String text = (tab == currentTab) ? "> " + tab.title : tab.title;
            this.addRenderableWidget(Button.builder(Component.literal(text), b -> {
                        currentTab = tab;
                        this.clearWidgets();
                        this.init();
                    })
                    .bounds(panelX + 8, panelY + 44 + i * ROW_H, SIDEBAR_W - 16, BTN_H)
                    .build());
        }

        // Вміст поточної вкладки
        switch (currentTab) {
            case COMBAT -> buildCombat();
            case VISUALS -> buildVisuals();
            case WORLD -> buildWorld();
            case PLAYER -> buildPlayer();
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(panelX + 8, panelY + panelH - 28, SIDEBAR_W - 16, BTN_H)
                .build());
    }

    private int row(int i) {
        return contentY + i * ROW_H;
    }

       private void buildCombat() {
        int half = (contentW - GAP) / 2;
        addToggle(contentX, row(0), half, "Aura 1.8.9",
                () -> CapsAuraClient.enabled,
                v -> { CapsAuraClient.enabled = v; if (v) CritAura.enabled = false; });
        addToggle(contentX + half + GAP, row(0), half, "Aura Crits",
                () -> CritAura.enabled,
                v -> { CritAura.enabled = v; if (v) CapsAuraClient.enabled = false; });
        addSlider(contentX, row(1), contentW, "Range", RANGE_MIN, RANGE_MAX, 1,
                CapsAuraClient.RANGE, v -> CapsAuraClient.RANGE = v);
        addSlider(contentX, row(2), contentW, "Attacks/sec (1.8.9)", APS_MIN, APS_MAX, 0,
                CapsAuraClient.ATTACKS_PER_SECOND, v -> CapsAuraClient.ATTACKS_PER_SECOND = (int) Math.round(v));
        addToggle(contentX, row(3), half, "Players",
                () -> CapsAuraClient.ATTACK_PLAYERS, v -> CapsAuraClient.ATTACK_PLAYERS = v);
        addToggle(contentX + half + GAP, row(3), half, "Hostile",
                () -> CapsAuraClient.ATTACK_HOSTILE, v -> CapsAuraClient.ATTACK_HOSTILE = v);
        addToggle(contentX, row(4), half, "Passive",
                () -> CapsAuraClient.ATTACK_PASSIVE, v -> CapsAuraClient.ATTACK_PASSIVE = v);
        addToggle(contentX + half + GAP, row(4), half, "Aim",
                () -> CapsAuraClient.AIM, v -> CapsAuraClient.AIM = v);
    }

    private void buildVisuals() {
        int half = (contentW - GAP) / 2;
        addToggle(contentX, row(0), half, "ESP",
                () -> EspOverlay.espOn, v -> EspOverlay.espOn = v);
        addToggle(contentX + half + GAP, row(0), half, "Tracers",
                () -> EspOverlay.tracersOn, v -> EspOverlay.tracersOn = v);
        addSlider(contentX, row(1), contentW, "ESP distance", ESP_MIN, ESP_MAX, 0,
                EspOverlay.MAX_DISTANCE, v -> EspOverlay.MAX_DISTANCE = v);
        addColorButton(row(2), "Enemy color",
                () -> EspOverlay.COLOR_ENEMY, c -> EspOverlay.COLOR_ENEMY = c);
        addColorButton(row(3), "Friend color",
                () -> EspOverlay.COLOR_FRIEND, c -> EspOverlay.COLOR_FRIEND = c);
    }

    private void buildWorld() {
        addToggle(contentX, row(0), contentW, "Bridge",
                () -> BridgeAssist.enabled, v -> BridgeAssist.enabled = v);
        addSlider(contentX, row(1), contentW, "Place delay (ticks)", DELAY_MIN, DELAY_MAX, 0,
                BridgeAssist.PLACE_DELAY_TICKS, v -> BridgeAssist.PLACE_DELAY_TICKS = (int) Math.round(v));
        addToggle(contentX, row(2), contentW, "X-Ray",
                () -> XRay.enabled, v -> XRay.setEnabled(v));
    }

    private void buildPlayer() {
        addToggle(contentX, row(0), contentW, "Velocity (anti-KB)",
                () -> Velocity.enabled, v -> Velocity.enabled = v);
        addSlider(contentX, row(1), contentW, "Horizontal KB %", PERCENT_MIN, PERCENT_MAX, 0,
                Velocity.HORIZONTAL, v -> Velocity.HORIZONTAL = Math.round(v));
        addSlider(contentX, row(2), contentW, "Vertical KB %", PERCENT_MIN, PERCENT_MAX, 0,
                Velocity.VERTICAL, v -> Velocity.VERTICAL = Math.round(v));
    }

    // ---------------------------------------------------------- віджети

    private void addToggle(int x, int y, int w, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.addRenderableWidget(Button.builder(toggleText(label, getter.getAsBoolean()), b -> {
                    boolean now = !getter.getAsBoolean();
                    setter.accept(now);
                    this.clearWidgets();
                    this.init();
                })
                .bounds(x, y, w, BTN_H)
                .build());
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private void addSlider(int x, int y, int w, String label, double min, double max, int decimals,
                           double current, DoubleConsumer onChange) {
        this.addRenderableWidget(new ValueSlider(x, y, w, BTN_H, label, min, max, decimals, current, onChange));
    }

    private void addColorButton(int y, String label, IntSupplier getter, IntConsumer setter) {
        this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    int cur = getter.getAsInt();
                    int next = 0;
                    for (int i = 0; i < PALETTE.length; i++) {
                        if (PALETTE[i] == cur) {
                            next = (i + 1) % PALETTE.length;
                            break;
                        }
                    }
                    setter.accept(PALETTE[next]);
                })
                .bounds(contentX, y, contentW, BTN_H)
                .build());
        swatches.add(new Swatch(contentX + contentW - 18, y + 4, getter));
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min, max;
        private final int decimals;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, int decimals,
                    double current, DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(),
                    Math.max(0.0, Math.min(1.0, (current - min) / (max - min))));
            this.label = label;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.onChange = onChange;
            updateMessage();
        }

        private double real() {
            return min + (max - min) * this.value;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            this.setMessage(Component.literal(
                    label + ": " + String.format(Locale.ROOT, "%." + decimals + "f", real())));
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }

    // ------------------------------------------------------------ рендер

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, COL_BORDER);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        graphics.fill(panelX, panelY, panelX + SIDEBAR_W, panelY + panelH, COL_SIDEBAR);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, COL_ACCENT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, "CapsAura", panelX + 10, panelY + 14, COL_ACCENT, false);
        graphics.text(this.font, "v1.0.0", panelX + 10, panelY + 26, COL_MUTED, false);
        graphics.text(this.font, currentTab.title, contentX, panelY + 14, COL_TEXT, false);
        String hint = currentTab == Tab.PLAYER ? "KB %: 0 = no knockback, 100 = vanilla" : "Right Shift - open/close";
        graphics.text(this.font, hint, contentX, panelY + 26, COL_MUTED, false);

        for (Swatch s : swatches) {
            graphics.fill(s.x() - 1, s.y() - 1, s.x() + 13, s.y() + 13, 0xFF000000);
            graphics.fill(s.x(), s.y(), s.x() + 12, s.y() + 12, s.color().getAsInt() | 0xFF000000);
        }
    }

    @Override
    public void onClose() {
        saveConfig();
        super.onClose();
    }
}
