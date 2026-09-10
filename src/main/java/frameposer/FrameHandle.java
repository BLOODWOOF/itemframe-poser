package frameposer;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

public record FrameHandle(boolean block, int entityId, BlockPos pos) {
	public static final StreamCodec<ByteBuf, FrameHandle> STREAM_CODEC = StreamCodec.of(
		(buf, handle) -> {
			buf.writeBoolean(handle.block);
			if (handle.block) {
				BlockPos.STREAM_CODEC.encode(buf, handle.pos);
			} else {
			buf.writeInt(handle.entityId);
			}
		},
		buf -> {
			if (buf.readBoolean()) {
				return block(BlockPos.STREAM_CODEC.decode(buf));
			}
			return entity(buf.readInt());
		}
	);

	public static FrameHandle entity(int entityId) {
		return new FrameHandle(false, entityId, BlockPos.ZERO);
	}

	public static FrameHandle block(BlockPos pos) {
		return new FrameHandle(true, 0, pos.immutable());
	}
}
