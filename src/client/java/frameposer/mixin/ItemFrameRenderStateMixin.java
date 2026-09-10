package frameposer.mixin;

import frameposer.FramePose;
import frameposer.FramePoseHolder;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemFrameRenderState.class)
public class ItemFrameRenderStateMixin implements FramePoseHolder {
	@Unique
	private FramePose frameposer$pose = FramePose.IDENTITY;

	@Override
	public FramePose frameposer$pose() {
		return this.frameposer$pose == null ? FramePose.IDENTITY : this.frameposer$pose;
	}

	@Override
	public void frameposer$setPose(FramePose pose) {
		this.frameposer$pose = pose == null ? FramePose.IDENTITY : pose;
	}
}
