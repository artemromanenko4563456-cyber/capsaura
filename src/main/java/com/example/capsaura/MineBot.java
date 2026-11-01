package com.example.capsaura;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Бот-шахтёр: роет туннель 2 блока высотой к ближайшей нужной руде. Команды: .mine <руда>, .stop */
public final class MineBot {

    // ======== НАСТРОЙКИ ========
    private static final int SEARCH_RADIUS = 24;          // радиус поиска руды
    private static final float MIN_HEALTH = 6.0f;         // ниже этого здоровья (3 сердца) бот останавливается
    private static final int STUCK_LIMIT_TICKS = 20 * 60; // 60 секунд на одну цель
    private static final double MINE_DISTANCE = 2.6;      // с такого расстояния ломаем саму руду
    private static final float ROTATION_SPEED = 4.0f;     // градусов за тик (меньше = камера медленнее)
    // ===========================

    private static boolean active = false;
    private static boolean keysHeld = false;
    private static String filter = "";
    private static BlockPos target = null;
    private static BlockPos collectPos = null;
    private static int collectTicks = 0;
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
            collectPos = null;
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

        // 1. Подбираем то, что выпало из добытой руды
        if (collectPos != null) {
            Vec3 c = Vec3.atCenterOf(collectPos);
            if (p.position().distanceTo(c) < 1.0 || ++collectTicks > 30) {
                collectPos = null;
                collectTicks = 0;
                setKeys(mc, false, false);
            } else {
                boolean aimed = lookAt(p, c);
                setKeys(mc, aimed, false);
                return;
            }
        }

        // 2. Выбираем цель
        if (target != null && !matches(mc.level.getBlockState(target).getBlock())) {
            // руда добыта: идём в её клетку за предметом
            collectPos = target;
            collectTicks = 0;
            target = null;
            stuckTicks = 0;
            return;
        }
        if (target == null) {
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

        // 3. Рядом с рудой - ломаем её, иначе роем туннель
        Vec3 center = Vec3.atCenterOf(target);
        if (p.getEyePosition().distanceTo(center) <= MINE_DISTANCE) {
            lookAt(p, center);
            setKeys(mc, false, false);
            dig(mc, p, target);
        } else {
            tunnel(mc, p);
        }
    }

    /** Прокладывает проход шириной в 1 блок и высотой в 2 в сторону цели. */
    private static void tunnel(Minecraft mc, LocalPlayer p) {
        BlockPos feet = p.blockPosition();
        int dx = target.getX() - feet.getX();
        int dy = target.getY() - feet.getY();
        int dz = target.getZ() - feet.getZ();

        Direction dir;
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
            dir = dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (dz != 0) {
            dir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            dir = p.getDirection();
        }

        int horizontal = Math.max(Math.abs(dx), Math.abs(dz));
        boolean up = dy >= 1 && Math.abs(dy) >= horizontal;
        boolean down = dy <= -1 && Math.abs(dy) >= horizontal;

        BlockPos front = feet.relative(dir);
        List<BlockPos> clear = new ArrayList<>();
        if (up) {
            // ступенька вверх: освобождаем место над головой и клетки на уровень выше впереди
            clear.add(feet.above(2));
            clear.add(front.above(1));
            clear.add(front.above(2));
        } else if (down) {
            // ступенька вниз
            clear.add(front.above());
            clear.add(front);
            clear.add(front.below());
        } else {
            clear.add(front.above());
            clear.add(front);
        }

        for (BlockPos pos : clear) {
            if (!passable(mc, pos)) {
                lookAt(p, Vec3.atCenterOf(pos));
                setKeys(mc, false, false);
                dig(mc, p, pos);
                return;
            }
        }

        // Путь свободен: поворачиваемся и идём вперёд (прыгаем, если поднимаемся)
        boolean aimed = lookHorizontal(p, Vec3.atCenterOf(front));
        setKeys(mc, aimed, up && aimed);
    }

    private static void dig(Minecraft mc, LocalPlayer p, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getDestroySpeed(mc.level, pos) < 0) {
            stop(mc, "Бот остановлен: неразрушимый блок на пути");
            return;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 c = Vec3.atCenterOf(pos);
        Direction face = Direction.getApproximateNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
        mc.gameMode.continueDestroyBlock(pos, face);
        p.swing(InteractionHand.MAIN_HAND);
    }

    private static boolean passable(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.getCollisionShape(mc.level, pos).isEmpty();
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

    // ---------- плавная камера ----------

    /** Плавно поворачивает камеру к точке. Возвращает true, когда уже смотрим примерно туда. */
    private static boolean lookAt(LocalPlayer p, Vec3 point) {
        Vec3 eye = p.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
        return rotateToward(p, yaw, pitch);
    }

    private static boolean lookHorizontal(LocalPlayer p, Vec3 point) {
        double dx = point.x - p.getX();
        double dz = point.z - p.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return rotateToward(p, yaw, 0.0f);
    }

    private static boolean rotateToward(LocalPlayer p, float targetYaw, float targetPitch) {
        float dYaw = wrap(targetYaw - p.getYRot());
        float dPitch = targetPitch - p.getXRot();
        float stepYaw = Math.max(-ROTATION_SPEED, Math.min(ROTATION_SPEED, dYaw));
        float stepPitch = Math.max(-ROTATION_SPEED, Math.min(ROTATION_SPEED, dPitch));
        p.setYRot(p.getYRot() + stepYaw);
        p.setXRot(p.getXRot() + stepPitch);
        return Math.abs(dYaw) < 12.0f;
    }

    private static float wrap(float angle) {
        float a = angle % 360.0f;
        if (a >= 180.0f) a -= 360.0f;
        if (a < -180.0f) a += 360.0f;
        return a;
    }

    // ---------- вспомогательное ----------

    private static void setKeys(Minecraft mc, boolean forward, boolean jump) {
        mc.options.keyUp.setDown(forward);
        mc.options.keyJump.setDown(jump);
        keysHeld = true;
    }

    private static void stop(Minecraft mc, String reason) {
        active = false;
        target = null;
        collectPos = null;
        if (keysHeld && mc.options != null) {
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
