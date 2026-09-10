package frameposer;

import frameposer.net.FrameRetargetPayload;
import frameposer.net.UpdateFramePosePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import frameposer.mixin.ItemFrameAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FrameApply {
	private FrameApply() {
	}

	public static void handle(ServerPlayer player, UpdateFramePosePayload payload) {
		if (player.level() instanceof ServerLevel && !player.isSpectator()) {
			if (payload.handle().block()) {
				applyBlock(player, payload);
			} else {
				applyEntity(player, payload);
			}
		}
	}

	private static void applyEntity(ServerPlayer player, UpdateFramePosePayload payload) {
		ItemFrame frame = FrameLookup.entityFrom(player, payload.handle().entityId());
		if (frame == null) {
			return;
		}

		FramePose pose = payload.pose().sanitized();
		frame.setInvisible(payload.invisible());
		((ItemFrameAccessor) frame).frameposer$setFixed(pose.fixed());
		frame.setInvulnerable(pose.invulnerable());
		FramePoserAttachments.set(frame, pose);
		ItemPoseData.stamp(frame, pose);

		ItemFrame target = frame;
		boolean wantGlow = payload.glowing();
		boolean isGlow = frame instanceof GlowItemFrame;
		if (wantGlow != isGlow) {
			if (wantGlow && !GlowInk.takeSac(player)) {
				FrameSync.bounce(frame);
				return;
			}
			ItemFrame next = FrameConversion.swapGlow(frame, wantGlow);
			if (next.getId() != frame.getId() && ServerPlayNetworking.canSend(player, FrameRetargetPayload.TYPE)) {
				ServerPlayNetworking.send(player, new FrameRetargetPayload(
					FrameHandle.entity(frame.getId()),
					FrameHandle.entity(next.getId())
				));
			}
			target = next;
		}
		FrameSync.bounce(target);
	}

	private static void applyBlock(ServerPlayer player, UpdateFramePosePayload payload) {
		BlockEntity be = FrameLookup.blockFrom(player, payload.handle().pos());
		if (be == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		FramePose pose = payload.pose().sanitized();
		FramePoserAttachments.set(be, pose);
		ItemPoseData.stamp(be, pose);

		BlockState state = level.getBlockState(be.getBlockPos());
		BlockState next = FastFrames.setBool(state, "invisible", payload.invisible());
		if (next != state) {
			level.setBlock(be.getBlockPos(), next, Block.UPDATE_ALL);
			state = next;
		}

		boolean wantGlow = payload.glowing();
		if (wantGlow != FastFrames.isGlowFrame(state)) {
			if (wantGlow && !GlowInk.takeSac(player)) {
				FrameSync.bounce(level, be.getBlockPos());
				return;
			}
			FrameConversion.swapGlowBlock(level, be.getBlockPos(), wantGlow);
		} else {
			be.setChanged();
			level.sendBlockUpdated(be.getBlockPos(), state, state, Block.UPDATE_ALL);
		}
		FrameSync.bounce(level, be.getBlockPos());
	}
}
