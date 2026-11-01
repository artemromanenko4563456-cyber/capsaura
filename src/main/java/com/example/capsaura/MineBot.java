package com.example.capsaura;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Простой бот-шахтёр: копает прямой туннель к ближайшей нужной руде. Команды: .mine <руда>, .stop */
public final class MineBot {

    // ======== НАСТРОЙКИ ========
    private static final int SEARCH_RADIUS = 24;          // радиус поиска руды
    private static final float MIN_HEALTH = 6.0f;         // ниже этого здоровья (3 сердца) бот останавливается
    private static final int STUCK_LIMIT_TICKS = 20 * 60; // 60 секунд на одну цель
    // ===========================

    private static boolean active = false;
    private static boolean keysHeld = false;
    private static String filter = "";
    private static BlockPos target = null;
    private static int stuckTicks = 0;

    private MineBot() {}

    public static void init() {
        ClientSendMessageEvents.ALLOW_CHAT.register(MineBot::onChat);
        ClientTickEvents.END_CLIENT_TICK.register(MineBot::onTick);
    }

    /** Возвращает false, чтобы команда не ушла на сервер. */
    private static boolean onChat(String message) {
        String lower = message.trim().toLowerCase(Locale.ROOT);
        if (!lower.startsWith(".")) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();

        if (lower.equals(".stop")) {
            stop(mc, "Бот остановлен");
            return false;
        }
        if (lower.startsWith(".mine")) {
            String arg = lower.substring(5).trim();
            if (arg.isEmpty()) {
                say(mc, "Использование: .mine diamond | iron | gold | coal | redstone | lapis | emerald | copper | debris");
                return false;
            }
            filter = arg;
            target = null;
            stuckTicks = 0;
            active = true;
            say(mc, "Копаю: " + arg + " (.stop - остановить)");
            return false;
        }
        return true;
    }

    private static void onTick(Minecraft mc) {
        if (!active) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            stop(mc, null);
            return;
        }

        LocalPlayer p = mc.player;
        if (p.getHealth() < MIN_HEALTH) {
            stop(mc, "Бот остановлен: мало здоровья");
            return;
        }
        if (lavaNearby(mc, p)) {
            stop(mc, "Бот остановлен: рядом лава");
            return;
        }

        // Цель добыта или ещё не выбрана: ищем ближайшую руду
        if (target == null || !matches(mc.level.getBlockState(target).getBlock())) {
            target = findNearest(mc, p);
            stuckTicks = 0;
            if (target == null) {
                stop(mc, "Руда не найдена рядом (радиус " + SEARCH_RADIUS + ")");
                return;
            }
        }
        if (++stuckTicks > STUCK_LIMIT_TICKS) {
            stop(mc, "Бот остановлен: застрял");
            return;
        }

        // Наводим камеру на руду
        Vec3 eye = p.getEyePosition();
        Vec3 to = Vec3.atCenterOf(target);
        double dx = to.x - eye.x;
        double dy = to.y - eye.y;
        double dz = to.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setXRot((float) (-Math.toDegrees(Math.atan2(dy, flat))));

        // Копаем то, что перед нами, и идём вперёд, пока руда не окажется рядом
        double dist = eye.distanceTo(to);
        mc.options.keyAttack.setDown(true);
        mc.options.keyUp.setDown(dist > 2.0);
        mc.options.keyJump.setDown(p.horizontalCollision && to.y > p.getY() + 0.5);
        keysHeld = true;
    }

    private static boolean matches(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (filter.equals("debris")) {
            return path.equals("ancient_debris");
        }
        return path.endsWith("_ore") && path.contains(filter);
    }

    private static BlockPos findNearest(Minecraft mc, LocalPlayer p) {
        BlockPos center = p.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!matches(mc.level.getBlockState(pos).getBlock())) {
                        continue;
                    }
                    double d = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean lavaNearby(Minecraft mc, LocalPlayer p) {
        BlockPos c = p.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.LAVA) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void stop(Minecraft mc, String reason) {
        active = false;
        target = null;
        if (keysHeld && mc.options != null) {
            mc.options.keyAttack.setDown(false);
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
        }
        keysHeld = false;
        if (reason != null) {
            say(mc, reason);
        }
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}
