package frameposer.mixin.fastitemframes;

import java.lang.reflect.Field;
import frameposer.FramePose;
import frameposer.FramePoseHolder;
import frameposer.FramePoserAttachments;
import frameposer.client.ClientFramePoses;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "fuzs.fastitemframes.common.client.renderer.blockentity.ItemFrameBlockRenderer")
public class ItemFrameBlockRendererMixin {
	@Unique
	private static Field nestedField;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void frameposer$copyPose(@Coerce BlockEntity blockEntity, @Coerce Object renderState, float partialTick, Vec3 camera, @Coerce Object overlay, CallbackInfo ci) {
		ItemFrameRenderState nested = nestedState(renderState);
		if (nested instanceof FramePoseHolder holder && blockEntity != null) {
			holder.frameposer$setPose(pickPose(blockEntity));
		}
	}

	@Unique
	private static FramePose pickPose(BlockEntity blockEntity) {
		FramePose cached = ClientFramePoses.resolve(blockEntity);
		return cached != null ? cached : FramePoserAttachments.get(blockEntity);
	}

	private static ItemFrameRenderState nestedState(Object renderState) {
		if (renderState == null) {
			return null;
		}
		try {
			if (nestedField == null) {
				nestedField = renderState.getClass().getField("entityRenderState");
			}
			return (ItemFrameRenderState) nestedField.get(renderState);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}
}
