package com.example.capsaura;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Сохранение и загрузка настроек меню в config/capsaura.properties. */
final class MenuConfig {

    static final double RANGE_MIN = 1.0, RANGE_MAX = 6.0;
    static final double APS_MIN = 1, APS_MAX = 20;
    static final double ESP_MIN = 16, ESP_MAX = 128;
    static final double DELAY_MIN = 0, DELAY_MAX = 10;
    static final double PERCENT_MIN = 0, PERCENT_MAX = 100;

    private MenuConfig() {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("capsaura.properties");
    }

    static void load() {
        Path file = path();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
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

    static void save() {
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
        try (OutputStream out = Files.newOutputStream(path())) {
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
}
