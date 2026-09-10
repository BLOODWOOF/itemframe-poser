package frameposer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import frameposer.mixin.ItemFrameAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FrameConversion {
	private FrameConversion() {
	}

	public static ItemFrame swapGlow(ItemFrame frame, boolean glowing) {
		if (!(frame.level() instanceof ServerLevel level)) {
			return frame;
		}
		if (glowing == frame instanceof GlowItemFrame) {
			return frame;
		}

		ItemStack item = frame.getItem().copy();
		int rotation = frame.getRotation();
		boolean invisible = frame.isInvisible();
		boolean invulnerable = frame.isInvulnerable();
		boolean fixed = ((ItemFrameAccessor) frame).frameposer$isFixed();
		FramePose pose = FramePoserAttachments.get(frame);
		BlockPos hangPos = frame.getPos();
		Direction facing = frame.getDirection();

		ItemFrame next = glowing ? new GlowItemFrame(level, hangPos, facing) : new ItemFrame(level, hangPos, facing);
		next.setItem(item, false);
		next.setRotation(rotation);
		next.setInvisible(invisible);
		next.setInvulnerable(invulnerable);
		((ItemFrameAccessor) next).frameposer$setFixed(fixed);
		FramePoserAttachments.set(next, pose);
		level.addFreshEntity(next);

		frame.setItem(ItemStack.EMPTY, false);
		frame.discard();
		return next;
	}

	public static boolean swapGlowBlock(ServerLevel level, BlockPos pos, boolean glowing) {
		BlockState state = level.getBlockState(pos);
		if (!FastFrames.isFrameBlock(state) || FastFrames.isGlowFrame(state) == glowing) {
			return true;
		}

		Block replacement = FastFrames.frameBlock(glowing);
		if (replacement == Blocks.AIR) {
			return false;
		}

		BlockEntity old = level.getBlockEntity(pos);
		FramePose pose = old == null ? FramePose.IDENTITY : FramePoserAttachments.get(old);
		BlockState next = FastFrames.copySharedProperties(state, replacement.defaultBlockState());
		level.setBlock(pos, next, Block.UPDATE_ALL);
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			FramePoserAttachments.set(be, pose);
			be.setChanged();
			level.sendBlockUpdated(pos, next, next, Block.UPDATE_ALL);
		}
		return true;
	}
}
