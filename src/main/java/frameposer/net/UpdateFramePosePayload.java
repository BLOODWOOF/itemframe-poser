package frameposer.net;

import frameposer.FrameHandle;
import frameposer.FramePose;
import frameposer.FramePoser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateFramePosePayload(
	FrameHandle handle,
	FramePose pose,
	boolean invisible,
	boolean glowing
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpdateFramePosePayload> TYPE = new CustomPacketPayload.Type<>(
		FramePoser.id("update_pose")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFramePosePayload> STREAM_CODEC = StreamCodec.composite(
		FrameHandle.STREAM_CODEC,
		UpdateFramePosePayload::handle,
		FramePose.STREAM_CODEC,
		UpdateFramePosePayload::pose,
		ByteBufCodecs.BOOL,
		UpdateFramePosePayload::invisible,
		ByteBufCodecs.BOOL,
		UpdateFramePosePayload::glowing,
		UpdateFramePosePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
