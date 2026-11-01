package com.example.capsaura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class BridgeAssist {

	// ======== НАСТРОЙКИ ========
	private static final int TOGGLE_KEY = GLFW.GLFW_KEY_G; // клавиша «П» на русской раскладке
	private static final int PLACE_DELAY_TICKS = 1;        // 1 = каждый тик, 2 = через тик и т.д.
	// ===========================

	private static final Direction[] SIDES = {
			Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
	};

	private static KeyMapping toggleKey;
	static boolean enabled = false;
	private static int delay = 0;

	private BridgeAssist() {}

	public static void init(KeyMapping.Category category) {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.capsaura.bridge", InputConstants.Type.KEYSYM, TOGGLE_KEY, category));
		ClientTickEvents.END_CLIENT_TICK.register(BridgeAssist::onTick);
	}

	private static void onTick(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal("Bridge: " + (enabled ? "ON" : "OFF")));
			}
		}

		if (delay > 0) {
			delay--;
		}
		if (!enabled || delay > 0 || mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		LocalPlayer player = mc.player;
		if (!(player.getMainHandItem().getItem() instanceof BlockItem)) {
			return; // в руке должен быть блок
		}

		BlockPos target = player.blockPosition().below();
		if (!isFree(mc, target)) {
			return; // под ногами уже есть опора
		}

		for (Direction side : SIDES) {
			BlockPos neighbor = target.relative(side);
			if (isFree(mc, neighbor)) {
				continue;
			}
			// Ставим блок на грань соседнего блока, обращённую к целевой клетке
			Direction face = side.getOpposite();
			Vec3 hitPos = Vec3.atCenterOf(neighbor).add(
					face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			BlockHitResult hit = new BlockHitResult(hitPos, face, neighbor, false);

			InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
			if (result.consumesAction()) {
				player.swing(InteractionHand.MAIN_HAND);
				delay = PLACE_DELAY_TICKS;
			}
			return;
		}
	}

	/** Клетка пустая или заменяемая (воздух, вода, трава). */
	private static boolean isFree(Minecraft mc, BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced();
	}
}
