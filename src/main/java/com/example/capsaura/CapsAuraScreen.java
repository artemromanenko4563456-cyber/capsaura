package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class CapsAuraScreen extends Screen {

    private static final int OPEN_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static KeyMapping openKey;

    private static final int BTN_W = 150;
    private static final int BTN_H = 20;
    private static final int GAP = 4;
    private static final int COLUMN_GAP = 10;
    private static final int TOP = 56;

    public CapsAuraScreen() {
        super(Component.literal("CapsAura"));
    }

    // Викликати з CapsAuraClient.onInitializeClient() поруч з EspOverlay.init / BridgeAssist.init
    public static void init(KeyMapping.Category category) {
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

    @Override
    protected void init() {
        int cx = this.width / 2;
        int leftX = cx - BTN_W - COLUMN_GAP / 2;
        int rightX = cx + COLUMN_GAP / 2;
        int step = BTN_H + GAP;

        // Ліва колонка: модулі
        addToggle(leftX, TOP, "Kill Aura",
                () -> CapsAuraClient.enabled, v -> CapsAuraClient.enabled = v);
        addToggle(leftX, TOP + step, "ESP",
                () -> EspOverlay.espOn, v -> EspOverlay.espOn = v);
        addToggle(leftX, TOP + step * 2, "Tracers",
                () -> EspOverlay.tracersOn, v -> EspOverlay.tracersOn = v);
        addToggle(leftX, TOP + step * 3, "Bridge",
                () -> BridgeAssist.enabled, v -> BridgeAssist.enabled = v);

        // Права колонка: цілі Kill Aura
        addToggle(rightX, TOP, "Players",
                () -> CapsAuraClient.ATTACK_PLAYERS, v -> CapsAuraClient.ATTACK_PLAYERS = v);
        addToggle(rightX, TOP + step, "Hostile mobs",
                () -> CapsAuraClient.ATTACK_HOSTILE, v -> CapsAuraClient.ATTACK_HOSTILE = v);
        addToggle(rightX, TOP + step * 2, "Passive mobs",
                () -> CapsAuraClient.ATTACK_PASSIVE, v -> CapsAuraClient.ATTACK_PASSIVE = v);
        addToggle(rightX, TOP + step * 3, "Aim assist",
                () -> CapsAuraClient.AIM, v -> CapsAuraClient.AIM = v);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx - 100, this.height - 30, 200, BTN_H)
                .build());
    }

    private void addToggle(int x, int y, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        Button button = Button.builder(toggleText(label, getter.getAsBoolean()), b -> {
                    boolean now = !getter.getAsBoolean();
                    setter.accept(now);
                    b.setMessage(toggleText(label, now));
                })
                .bounds(x, y, BTN_W, BTN_H)
                .build();
        this.addRenderableWidget(button);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick); // фон + кнопки

        int cx = this.width / 2;
        int leftCenter = cx - BTN_W / 2 - COLUMN_GAP / 2;
        int rightCenter = cx + BTN_W / 2 + COLUMN_GAP / 2;

        graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("Modules"), leftCenter, TOP - 12, 0xFFAAAAAA);
        graphics.centeredText(this.font, Component.literal("Kill Aura targets"), rightCenter, TOP - 12, 0xFFAAAAAA);
    }
}
