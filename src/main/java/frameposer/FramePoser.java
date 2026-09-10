package frameposer;

import frameposer.net.FrameRetargetPayload;
import frameposer.net.UpdateFramePosePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FramePoser implements ModInitializer {
	public static final String MOD_ID = "frameposer";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		FramePoserAttachments.POSE.getClass();
		PayloadTypeRegistry.serverboundPlay().register(UpdateFramePosePayload.TYPE, UpdateFramePosePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateFramePosePayload.TYPE, UpdateFramePosePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FrameRetargetPayload.TYPE, FrameRetargetPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpdateFramePosePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> FrameApply.handle(context.player(), payload));
		});

		// same bounce as the head skin packet: whoever starts looking gets the current pose
		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			if (entity instanceof ItemFrame frame) {
				FrameSync.send(player, frame);
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockEntity be = level.getBlockEntity(hit.getBlockPos());
			if (be != null && FastFrames.isFrameBlock(level.getBlockState(hit.getBlockPos()))
				&& FramePoserAttachments.get(be).fixed()
				&& !player.isShiftKeyDown()) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			BlockEntity be = level.getBlockEntity(pos);
			if (be != null && FastFrames.isFrameBlock(level.getBlockState(pos))
				&& FramePoserAttachments.get(be).invulnerable()
				&& !player.hasInfiniteMaterials()) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (be != null && FastFrames.isFrameBlock(state)
				&& FramePoserAttachments.get(be).invulnerable()
				&& !player.hasInfiniteMaterials()) {
				return false;
			}
			return true;
		});
	}
}
