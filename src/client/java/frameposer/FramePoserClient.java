package frameposer;

import frameposer.client.ClientFramePoses;
import frameposer.client.FrameGroupScreen;
import frameposer.client.FrameGroups;
import frameposer.client.FramePoserScreen;
import frameposer.client.PoseCloud;
import frameposer.client.PoseCloudConfig;
import frameposer.net.FrameRetargetPayload;
import frameposer.net.UpdateFramePosePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class FramePoserClient implements ClientModInitializer {
	public static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.frameposer.open",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			KeyMapping.Category.register(FramePoser.id("general"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				openLookedAt(client);
			}
			if (client.player != null && client.player.tickCount % 40 == 0) {
				ClientFramePoses.flush();
			}
			PoseCloud.tick(client);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			ClientFramePoses.loadForConnection();
			PoseCloudConfig.load();
			if (ClientPlayNetworking.canSend(UpdateFramePosePayload.TYPE)) {
				ClientFramePoses.dropLocalCache();
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientFramePoses.unload();
			PoseCloud.disconnect();
		});

		ClientPlayNetworking.registerGlobalReceiver(FrameRetargetPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (FramePoserScreen.current != null) {
					FramePoserScreen.current.retarget(payload.from(), payload.to());
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(UpdateFramePosePayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().level == null || FramePoserScreen.owns(payload.handle())) {
					return;
				}
				ClientFramePoses.applyPacket(context.client().level, payload.handle(), payload.pose());
			});
		});

		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			ClientFramePoses.onEntityLoad(entity);
			if (entity instanceof ItemFrame frame) {
				PoseCloud.notice(ClientFramePoses.cloudKey(frame));
			}
		});
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> PoseCloud.onChunk(chunk));
	}

	private static void openLookedAt(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		FrameHandle looked = lookedAt(minecraft);
		if (looked == null) {
			return;
		}
		if (minecraft.gui.screen() instanceof FramePoserScreen || minecraft.gui.screen() instanceof FrameGroupScreen) {
			FrameGroups.toggle(minecraft.level, looked);
			if (minecraft.gui.screen() instanceof FrameGroupScreen groupScreen) {
				groupScreen.notice(looked);
			}
			return;
		}
		if (minecraft.gui.screen() != null) {
			return;
		}
		open(looked);
	}

	private static FrameHandle lookedAt(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemFrame frame) {
			if (FrameLookup.inRange(minecraft.player, frame)) {
				return FrameHandle.entity(frame.getId());
			}
			return null;
		}
		if (hit instanceof BlockHitResult blockHit
			&& FastFrames.isFrameBlock(minecraft.level.getBlockState(blockHit.getBlockPos()))
			&& FrameLookup.inRange(minecraft.player, blockHit.getBlockPos())) {
			return FrameHandle.block(blockHit.getBlockPos());
		}
		return null;
	}

	private static void open(FrameHandle handle) {
		Minecraft minecraft = Minecraft.getInstance();
		FramePoserScreen screen = new FramePoserScreen(handle);
		if (screen.isValid()) {
			minecraft.gui.setScreen(screen);
		}
	}
}
