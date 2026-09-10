package frameposer.mixin.fastitemframes;

import frameposer.FramePoserAttachments;
import frameposer.FrameSync;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "fuzs.fastitemframes.common.handler.ItemFrameHandler")
public class ItemFrameHandlerMixin {
	@Inject(method = "setItemFrameBlock", at = @At("TAIL"))
	private static void frameposer$copyPose(ServerLevel level, BlockPos pos, BlockState state, ItemFrame itemFrame, CallbackInfo ci) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			FramePoserAttachments.copy(itemFrame, be);
			FrameSync.bounce(level, pos);
		}
	}
}
