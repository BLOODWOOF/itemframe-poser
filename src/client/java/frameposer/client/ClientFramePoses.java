package frameposer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import frameposer.FrameHandle;
import frameposer.FrameLookup;
import frameposer.FramePose;
import frameposer.ItemPoseData;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientFramePoses {
	private static final Gson GSON = new GsonBuilder().create();
	private static final Map<UUID, FramePose> ENTITIES = new HashMap<>();
	private static final Map<String, FramePose> BLOCKS = new HashMap<>();
	private static final Map<Integer, FramePose> PENDING = new HashMap<>();
	private static String loadedKey;

	private static boolean dirty;

	private ClientFramePoses() {
	}

	public static void store(Level level, FrameHandle handle, FramePose pose) {
		ensureLoaded();
		FramePose next = pose == null ? FramePose.IDENTITY : pose.sanitized();
		if (handle.block()) {
			String key = blockKey(level, handle.pos());
			if (next.equals(FramePose.IDENTITY)) {
				BLOCKS.remove(key);
			} else {
				BLOCKS.put(key, next);
			}
			dirty = true;
			return;
		}

		Entity entity = level.getEntity(handle.entityId());
		if (!(entity instanceof ItemFrame frame)) {
			return;
		}
		if (next.equals(FramePose.IDENTITY)) {
			ENTITIES.remove(frame.getUUID());
		} else {
			ENTITIES.put(frame.getUUID(), next);
		}
		dirty = true;
	}

	// packet from the server, same as the head skin bounce. keep it in memory
	// so we dont write other peoples poses into the local json
	public static void applyPacket(Level level, FrameHandle handle, FramePose pose) {
		ensureLoaded();
		FramePose next = pose == null ? FramePose.IDENTITY : pose.sanitized();
		if (handle.block()) {
			writeBlock(blockKey(level, handle.pos()), next);
			return;
		}

		Entity entity = level.getEntity(handle.entityId());
		if (entity instanceof ItemFrame frame) {
			PENDING.remove(handle.entityId());
			writeEntity(frame.getUUID(), next);
			return;
		}
		PENDING.put(handle.entityId(), next);
	}

	public static void onEntityLoad(Entity entity) {
		if (!(entity instanceof ItemFrame frame)) {
			return;
		}
		FramePose pose = PENDING.remove(frame.getId());
		if (pose != null) {
			writeEntity(frame.getUUID(), pose);
		}
	}

	private static void writeBlock(String key, FramePose next) {
		if (next.equals(FramePose.IDENTITY)) {
			BLOCKS.remove(key);
		} else if (!next.equals(BLOCKS.get(key))) {
			BLOCKS.put(key, next);
		}
	}

	private static void writeEntity(UUID id, FramePose next) {
		if (next.equals(FramePose.IDENTITY)) {
			ENTITIES.remove(id);
		} else if (!next.equals(ENTITIES.get(id))) {
			ENTITIES.put(id, next);
		}
	}

	public static FramePose overlay(Level level, FrameHandle handle, FramePose fallback) {
		ensureLoaded();
		FramePose cached = peek(level, handle);
		if (cached != null) {
			return cached;
		}
		FramePose tagged = ItemPoseData.read(FrameLookup.item(level, handle));
		return tagged != null ? tagged : fallback;
	}

	public static FramePose resolve(ItemFrame frame) {
		ensureLoaded();
		FramePose cached = ENTITIES.get(frame.getUUID());
		if (cached != null) {
			return cached;
		}
		return ItemPoseData.read(frame.getItem());
	}

	public static FramePose resolve(BlockEntity be) {
		ensureLoaded();
		if (be == null) {
			return null;
		}
		FramePose cached = BLOCKS.get(blockKey(be.getLevel(), be.getBlockPos()));
		if (cached != null) {
			return cached;
		}
		if (be instanceof Container container && container.getContainerSize() > 0) {
			return ItemPoseData.read(container.getItem(0));
		}
		return null;
	}

	public static void loadForConnection() {
		String key = connectionKey();
		if (key.equals(loadedKey)) {
			return;
		}
		ENTITIES.clear();
		BLOCKS.clear();
		PENDING.clear();
		loadedKey = key;
		Path file = fileFor(key);
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Saved saved = GSON.fromJson(reader, Saved.class);
			if (saved == null) {
				return;
			}
			if (saved.entities != null) {
				saved.entities.forEach((id, pose) -> {
					try {
						ENTITIES.put(UUID.fromString(id), pose);
					} catch (IllegalArgumentException ignored) {
					}
				});
			}
			if (saved.blocks != null) {
				BLOCKS.putAll(saved.blocks);
			}
		} catch (Exception ignored) {
		}
	}

	// server has the packet channel, so dont keep leftover client-only poses around
	public static void dropLocalCache() {
		ENTITIES.clear();
		BLOCKS.clear();
		PENDING.clear();
		dirty = false;
	}

	public static String serverKey() {
		return connectionKey();
	}

	public static boolean multiplayer() {
		String key = connectionKey();
		return key.startsWith("mp-");
	}

	public static String cloudKey(Level level, FrameHandle handle) {
		if (handle.block()) {
			return "b:" + blockKey(level, handle.pos());
		}
		Entity entity = level.getEntity(handle.entityId());
		if (entity instanceof ItemFrame frame) {
			return "e:" + frame.getUUID();
		}
		return null;
	}

	public static String cloudKey(ItemFrame frame) {
		return "e:" + frame.getUUID();
	}

	public static String cloudKey(BlockEntity be) {
		return "b:" + blockKey(be.getLevel(), be.getBlockPos());
	}

	public static void applyCloud(String cloudKey, FramePose pose) {
		ensureLoaded();
		FramePose next = pose == null ? FramePose.IDENTITY : pose.sanitized();
		if (cloudKey.startsWith("e:")) {
			try {
				writeEntity(UUID.fromString(cloudKey.substring(2)), next);
			} catch (IllegalArgumentException ignored) {
			}
			return;
		}
		if (cloudKey.startsWith("b:")) {
			writeBlock(cloudKey.substring(2), next);
		}
	}

	public static void unload() {
		flush();
		ENTITIES.clear();
		BLOCKS.clear();
		PENDING.clear();
		loadedKey = null;
	}

	public static void flush() {
		if (dirty) {
			save();
			dirty = false;
		}
	}

	private static FramePose peek(Level level, FrameHandle handle) {
		if (handle.block()) {
			return BLOCKS.get(blockKey(level, handle.pos()));
		}
		Entity entity = level.getEntity(handle.entityId());
		if (entity instanceof ItemFrame frame) {
			return ENTITIES.get(frame.getUUID());
		}
		return null;
	}

	private static void ensureLoaded() {
		if (loadedKey == null) {
			loadForConnection();
		}
	}

	private static void save() {
		if (loadedKey == null) {
			return;
		}
		Saved saved = new Saved();
		ENTITIES.forEach((id, pose) -> saved.entities.put(id.toString(), pose));
		saved.blocks.putAll(BLOCKS);
		Path file = fileFor(loadedKey);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(saved, writer);
			}
		} catch (IOException ignored) {
		}
	}

	private static String blockKey(Level level, BlockPos pos) {
		String dim = "world";
		if (level != null) {
			dim = level.dimension().identifier().toString();
		}
		return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String connectionKey() {
		Minecraft client = Minecraft.getInstance();
		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isEmpty()) {
			return "mp-" + server.ip.replaceAll("[^a-zA-Z0-9._-]", "_");
		}
		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			return "sp-" + client.getSingleplayerServer().getServerDirectory().getFileName();
		}
		return "unknown";
	}

	private static Path fileFor(String key) {
		return FabricLoader.getInstance().getConfigDir().resolve("frameposer").resolve("client-poses").resolve(key + ".json");
	}

	private static final class Saved {
		Map<String, FramePose> entities = new HashMap<>();
		Map<String, FramePose> blocks = new HashMap<>();
	}
}
