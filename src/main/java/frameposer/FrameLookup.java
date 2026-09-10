package frameposer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import frameposer.mixin.ItemFrameAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FrameLookup {
	private static final double MAX_DIST_SQR = 8.0 * 8.0;

	private FrameLookup() {
	}

	public static boolean inRange(Player player, Entity entity) {
		return player.distanceToSqr(entity) <= MAX_DIST_SQR;
	}

	public static boolean inRange(Player player, BlockPos pos) {
		return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DIST_SQR;
	}

	public static FrameSnapshot snapshot(Level level, FrameHandle handle) {
		if (handle.block()) {
			return snapshotBlock(level, handle.pos());
		}
		return snapshotEntity(level, handle.entityId());
	}

	public static FrameSnapshot snapshotEntity(Level level, int entityId) {
		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof ItemFrame frame)) {
			return null;
		}
		return new FrameSnapshot(
			FrameHandle.entity(frame.getId()),
			vanillaPose(frame),
			frame.isInvisible(),
			frame instanceof GlowItemFrame
		);
	}

	public static FrameSnapshot snapshotBlock(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!FastFrames.isFrameBlock(state)) {
			return null;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return null;
		}
		return new FrameSnapshot(
			FrameHandle.block(pos),
			FramePoserAttachments.get(be),
			FastFrames.boolValue(state, "invisible", false),
			FastFrames.isGlowFrame(state)
		);
	}

	public static ItemStack item(Level level, FrameHandle handle) {
		if (handle.block()) {
			BlockEntity be = level.getBlockEntity(handle.pos());
			if (be instanceof Container container && container.getContainerSize() > 0) {
				return container.getItem(0);
			}
			return ItemStack.EMPTY;
		}
		Entity entity = level.getEntity(handle.entityId());
		if (entity instanceof ItemFrame frame) {
			return frame.getItem();
		}
		return ItemStack.EMPTY;
	}

	public static FramePose vanillaPose(ItemFrame frame) {
		FramePose stored = FramePoserAttachments.get(frame);
		return new FramePose(
			stored.rotX(),
			stored.rotY(),
			stored.rotZ(),
			stored.offX(),
			stored.offY(),
			stored.offZ(),
			stored.scale(),
			((ItemFrameAccessor) frame).frameposer$isFixed(),
			frame.isInvulnerable()
		);
	}

	public static ItemFrame entityFrom(Player player, int entityId) {
		Entity entity = player.level().getEntity(entityId);
		if (!(entity instanceof ItemFrame frame) || !inRange(player, frame)) {
			return null;
		}
		return frame;
	}

	public static BlockEntity blockFrom(Player player, BlockPos pos) {
		if (!inRange(player, pos) || !(player.level() instanceof ServerLevel)) {
			return null;
		}
		BlockState state = player.level().getBlockState(pos);
		if (!FastFrames.isFrameBlock(state)) {
			return null;
		}
		return player.level().getBlockEntity(pos);
	}
}
