package frameposer.net;

import frameposer.FrameHandle;
import frameposer.FramePoser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FrameRetargetPayload(FrameHandle from, FrameHandle to) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FrameRetargetPayload> TYPE = new CustomPacketPayload.Type<>(
		FramePoser.id("retarget")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, FrameRetargetPayload> STREAM_CODEC = StreamCodec.composite(
		FrameHandle.STREAM_CODEC,
		FrameRetargetPayload::from,
		FrameHandle.STREAM_CODEC,
		FrameRetargetPayload::to,
		FrameRetargetPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
