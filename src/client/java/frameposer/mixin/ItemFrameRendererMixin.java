package frameposer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import frameposer.FramePose;
import frameposer.FramePoseHolder;
import frameposer.FramePoseTransforms;
import frameposer.FramePoserAttachments;
import frameposer.client.ClientFramePoses;
import frameposer.client.FrameGlowHandler;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
		at = @At("TAIL")
	)
	private void frameposer$extract(ItemFrame frame, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
		if (state instanceof FramePoseHolder holder) {
			holder.frameposer$setPose(pickPose(frame));
		}
		if (FrameGlowHandler.isGlowing(frame)) {
			FrameGlowHandler.tint(state);
		}
	}

	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = {
			@At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
			),
			@At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/renderer/MapRenderer;render(Lnet/minecraft/client/renderer/state/MapRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ZI)V"
			)
		}
	)
	private void frameposer$poseItem(
		ItemFrameRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState camera,
		CallbackInfo ci
	) {
		if (state instanceof FramePoseHolder holder) {
			FramePoseTransforms.apply(poseStack, holder.frameposer$pose());
		}
	}

	@Unique
	private static FramePose pickPose(ItemFrame frame) {
		FramePose cached = ClientFramePoses.resolve(frame);
		return cached != null ? cached : FramePoserAttachments.get(frame);
	}
}
