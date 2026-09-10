package frameposer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record FramePose(
	float rotX,
	float rotY,
	float rotZ,
	float offX,
	float offY,
	float offZ,
	float scale,
	boolean fixed,
	boolean invulnerable
) {
	public static final FramePose IDENTITY = new FramePose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, false, false);

	public static final Codec<FramePose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.FLOAT.optionalFieldOf("rotX", 0.0F).forGetter(FramePose::rotX),
		Codec.FLOAT.optionalFieldOf("rotY", 0.0F).forGetter(FramePose::rotY),
		Codec.FLOAT.optionalFieldOf("rotZ", 0.0F).forGetter(FramePose::rotZ),
		Codec.FLOAT.optionalFieldOf("offX", 0.0F).forGetter(FramePose::offX),
		Codec.FLOAT.optionalFieldOf("offY", 0.0F).forGetter(FramePose::offY),
		Codec.FLOAT.optionalFieldOf("offZ", 0.0F).forGetter(FramePose::offZ),
		Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(FramePose::scale),
		Codec.BOOL.optionalFieldOf("fixed", false).forGetter(FramePose::fixed),
		Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(FramePose::invulnerable)
	).apply(instance, FramePose::new));

	public static final StreamCodec<ByteBuf, FramePose> STREAM_CODEC = StreamCodec.of(
		(buf, pose) -> {
			buf.writeFloat(pose.rotX);
			buf.writeFloat(pose.rotY);
			buf.writeFloat(pose.rotZ);
			buf.writeFloat(pose.offX);
			buf.writeFloat(pose.offY);
			buf.writeFloat(pose.offZ);
			buf.writeFloat(pose.scale);
			buf.writeBoolean(pose.fixed);
			buf.writeBoolean(pose.invulnerable);
		},
		buf -> new FramePose(
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readBoolean(),
			buf.readBoolean()
		)
	);

	public static FramePose identity() {
		return IDENTITY;
	}

	public FramePose withFixed(boolean value) {
		return new FramePose(rotX, rotY, rotZ, offX, offY, offZ, scale, value, invulnerable);
	}

	public FramePose withInvulnerable(boolean value) {
		return new FramePose(rotX, rotY, rotZ, offX, offY, offZ, scale, fixed, value);
	}

	public boolean isIdentityTransform() {
		return rotX == 0.0F && rotY == 0.0F && rotZ == 0.0F
			&& offX == 0.0F && offY == 0.0F && offZ == 0.0F
			&& scale == 1.0F;
	}

	public FramePose sanitized() {
		float nextScale = Mth.clamp(scale, 0.01F, 16.0F);
		if (nextScale == scale) {
			return this;
		}
		return new FramePose(rotX, rotY, rotZ, offX, offY, offZ, nextScale, fixed, invulnerable);
	}
}
