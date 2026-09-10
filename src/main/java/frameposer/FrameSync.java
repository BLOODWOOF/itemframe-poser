package frameposer;

import frameposer.net.UpdateFramePosePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;

public final class FrameSync {
	private FrameSync() {
	}

	public static void bounce(ItemFrame frame) {
		for (ServerPlayer tracker : PlayerLookup.tracking(frame)) {
			send(tracker, frame);
		}
	}

	public static void bounce(ServerLevel level, BlockPos pos) {
		for (ServerPlayer tracker : PlayerLookup.tracking(level, pos)) {
			send(tracker, level, pos);
		}
	}

	public static void send(ServerPlayer player, ItemFrame frame) {
		if (!ServerPlayNetworking.canSend(player, UpdateFramePosePayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, of(frame));
	}

	public static void send(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!ServerPlayNetworking.canSend(player, UpdateFramePosePayload.TYPE)) {
			return;
		}
		UpdateFramePosePayload payload = of(level, pos);
		if (payload != null) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static UpdateFramePosePayload of(ItemFrame frame) {
		return new UpdateFramePosePayload(
			FrameHandle.entity(frame.getId()),
			FrameLookup.vanillaPose(frame),
			frame.isInvisible(),
			frame instanceof GlowItemFrame
		);
	}

	public static UpdateFramePosePayload of(ServerLevel level, BlockPos pos) {
		FrameSnapshot snapshot = FrameLookup.snapshotBlock(level, pos);
		if (snapshot == null) {
			return null;
		}
		return new UpdateFramePosePayload(snapshot.handle(), snapshot.pose(), snapshot.invisible(), snapshot.glowing());
	}
}
