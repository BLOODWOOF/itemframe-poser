package frameposer.client;

import frameposer.FastFrames;
import frameposer.FrameGroupIds;
import frameposer.FrameHandle;
import frameposer.FrameLookup;
import frameposer.ItemPoseData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// same idea as armor poser, just numbered buckets on frames
public final class FrameGroups {
	public static final ChatFormatting[] COLORS = {
		ChatFormatting.RED,
		ChatFormatting.GREEN,
		ChatFormatting.BLUE,
		ChatFormatting.YELLOW,
		ChatFormatting.AQUA,
		ChatFormatting.LIGHT_PURPLE,
		ChatFormatting.GOLD,
		ChatFormatting.DARK_PURPLE
	};

	private static final Map<String, Set<String>> groups = new HashMap<>();
	private static String selected = "1";
	private static boolean editMode;
	private static int range = 8;
	private static boolean fifLoaded = FabricLoader.getInstance().isModLoaded("fastitemframes");

	private FrameGroups() {
	}

	public static String selected() {
		return selected;
	}

	public static void select(String id) {
		if (FrameGroupIds.valid(id)) {
			selected = id;
		}
	}

	public static boolean editMode() {
		return editMode;
	}

	public static void setEditMode(boolean value) {
		editMode = value;
	}

	public static int range() {
		return range;
	}

	public static void setRange(int value) {
		range = Math.max(4, Math.min(32, value));
	}

	public static boolean contains(String group, String key) {
		return groups.getOrDefault(group, Set.of()).contains(key);
	}

	public static List<String> groupsFor(String key) {
		List<String> ids = new ArrayList<>();
		if (key == null) {
			return ids;
		}
		for (String id : FrameGroupIds.ALL) {
			if (contains(id, key)) {
				ids.add(id);
			}
		}
		return ids;
	}

	public static boolean toggle(Level level, FrameHandle handle) {
		return toggle(level, handle, selected);
	}

	public static boolean toggle(Level level, FrameHandle handle, String group) {
		String key = ClientFramePoses.cloudKey(level, handle);
		if (key == null || !FrameGroupIds.valid(group)) {
			return false;
		}
		ingest(level, handle);
		if (contains(group, key)) {
			remove(level, handle, group);
			return false;
		}
		add(level, handle, group);
		return true;
	}

	public static void add(Level level, FrameHandle handle, String group) {
		String key = ClientFramePoses.cloudKey(level, handle);
		if (key == null || !FrameGroupIds.valid(group)) {
			return;
		}
		if (groups.computeIfAbsent(group, ignored -> new HashSet<>()).add(key)) {
			stamp(level, handle);
			ClientFramePoses.markDirty();
		}
	}

	public static void remove(Level level, FrameHandle handle, String group) {
		String key = ClientFramePoses.cloudKey(level, handle);
		if (key == null) {
			return;
		}
		Set<String> members = groups.get(group);
		if (members != null && members.remove(key)) {
			if (members.isEmpty()) {
				groups.remove(group);
			}
			stamp(level, handle);
			ClientFramePoses.markDirty();
		}
	}

	public static void clearGroup(Level level, String group) {
		Set<String> members = groups.remove(group);
		if (members == null || members.isEmpty()) {
			return;
		}
		for (String key : members) {
			FrameHandle handle = handleFromKey(level, key);
			if (handle != null) {
				stamp(level, handle);
			}
		}
		ClientFramePoses.markDirty();
	}

	public static List<FrameHandle> members(Level level, String group) {
		List<FrameHandle> handles = new ArrayList<>();
		for (String key : groups.getOrDefault(group, Set.of())) {
			FrameHandle handle = handleFromKey(level, key);
			if (handle != null) {
				handles.add(handle);
			}
		}
		return handles;
	}

	public static void ingest(Level level, FrameHandle handle) {
		ItemStack item = FrameLookup.item(level, handle);
		if (item.isEmpty()) {
			return;
		}
		String key = ClientFramePoses.cloudKey(level, handle);
		if (key == null) {
			return;
		}
		for (String id : ItemPoseData.readGroups(item)) {
			groups.computeIfAbsent(id, ignored -> new HashSet<>()).add(key);
		}
	}

	public static List<Nearby> nearby(Level level, Vec3 origin, double reach) {
		List<Nearby> found = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		AABB box = new AABB(origin, origin).inflate(reach);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
			FrameHandle handle = FrameHandle.entity(frame.getId());
			String key = ClientFramePoses.cloudKey(frame);
			if (!seen.add(key)) {
				continue;
			}
			ingest(level, handle);
			found.add(new Nearby(handle, key, frame.getItem(), frame.isInvisible()));
		}
		if (!fifLoaded) {
			return found;
		}
		int view = Math.max(1, (int) Math.ceil(reach / 16.0) + 1);
		int originX = BlockPos.containing(origin).getX() >> 4;
		int originZ = BlockPos.containing(origin).getZ() >> 4;
		for (int cx = originX - view; cx <= originX + view; cx++) {
			for (int cz = originZ - view; cz <= originZ + view; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (!FastFrames.isFrameBlock(be.getBlockState())) {
						continue;
					}
					if (origin.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > reach * reach) {
						continue;
					}
					FrameHandle handle = FrameHandle.block(be.getBlockPos());
					String key = ClientFramePoses.cloudKey(be);
					if (!seen.add(key)) {
						continue;
					}
					ingest(level, handle);
					ItemStack item = FrameLookup.item(level, handle);
					boolean hidden = FastFrames.boolValue(be.getBlockState(), "invisible", false);
					found.add(new Nearby(handle, key, item, hidden));
				}
			}
		}
		return found;
	}

	public static FrameHandle handleFromKey(Level level, String key) {
		if (key == null || level == null) {
			return null;
		}
		if (key.startsWith("e:")) {
			UUID id;
			try {
				id = UUID.fromString(key.substring(2));
			} catch (IllegalArgumentException ignored) {
				return null;
			}
			Vec3 around = Vec3.ZERO;
			var player = Minecraft.getInstance().player;
			if (player != null) {
				around = player.position();
			}
			for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, new AABB(around, around).inflate(96.0))) {
				if (frame.getUUID().equals(id)) {
					return FrameHandle.entity(frame.getId());
				}
			}
			return null;
		}
		if (key.startsWith("b:")) {
			String rest = key.substring(2);
			int bar = rest.lastIndexOf('|');
			if (bar < 0) {
				return null;
			}
			String dim = rest.substring(0, bar);
			String world = level.dimension().identifier().toString();
			if (!dim.equals("world") && !dim.equals(world)) {
				return null;
			}
			String[] xyz = rest.substring(bar + 1).split(",");
			if (xyz.length != 3) {
				return null;
			}
			try {
				BlockPos pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				if (!FastFrames.isFrameBlock(level.getBlockState(pos))) {
					return null;
				}
				return FrameHandle.block(pos);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	public static void load(Map<String, List<String>> saved) {
		groups.clear();
		if (saved == null) {
			return;
		}
		saved.forEach((group, keys) -> {
			if (!FrameGroupIds.valid(group) || keys == null) {
				return;
			}
			Set<String> members = groups.computeIfAbsent(group, ignored -> new HashSet<>());
			for (String key : keys) {
				if (key != null && (key.startsWith("e:") || key.startsWith("b:"))) {
					members.add(key);
				}
			}
		});
	}

	public static Map<String, List<String>> export() {
		Map<String, List<String>> saved = new HashMap<>();
		groups.forEach((group, keys) -> saved.put(group, new ArrayList<>(keys)));
		return saved;
	}

	public static void clear() {
		groups.clear();
		editMode = false;
		selected = "1";
	}

	private static void stamp(Level level, FrameHandle handle) {
		String key = ClientFramePoses.cloudKey(level, handle);
		List<String> ids = groupsFor(key);
		if (handle.block()) {
			BlockEntity be = level.getBlockEntity(handle.pos());
			if (be != null) {
				ItemPoseData.stampGroups(be, ids);
			}
			return;
		}
		Entity entity = level.getEntity(handle.entityId());
		if (entity instanceof ItemFrame frame) {
			ItemPoseData.stampGroups(frame, ids);
		}
	}

	public record Nearby(FrameHandle handle, String key, ItemStack item, boolean hidden) {
	}
}
