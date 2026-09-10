package frameposer.mixin;

import frameposer.client.FrameGlowHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void frameposer$glowSelected(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof ItemFrame frame && FrameGlowHandler.isGlowing(frame)) {
			cir.setReturnValue(true);
		}
	}
}
