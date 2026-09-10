package frameposer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import frameposer.mixin.ItemFrameAccessor;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;

// Vanilla already syncs whatever is on the framed item, so we piggyback the pose there.
public final class ItemPoseData {
	private static final String KEY = "frameposer";
	private static final String GROUPS_KEY = "frameposer_groups";
	private static final Codec<List<String>> GROUPS_CODEC = Codec.STRING.listOf();

	private ItemPoseData() {
	}

	public static FramePose read(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().read(KEY, FramePose.CODEC).orElse(null);
	}

	public static List<String> readGroups(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return List.of();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return List.of();
		}
		List<String> raw = data.copyTag().read(GROUPS_KEY, GROUPS_CODEC).orElse(List.of());
		List<String> clean = new ArrayList<>();
		for (String id : raw) {
			if (FrameGroupIds.valid(id) && !clean.contains(id)) {
				clean.add(id);
			}
		}
		return clean;
	}

	public static void stamp(ItemFrame frame, FramePose pose) {
		ItemStack old = frame.getItem();
		if (old.isEmpty()) {
			return;
		}
		ItemStack next = apply(old, pose);
		if (ItemStack.matches(old, next)) {
			return;
		}
		EntityDataAccessor<ItemStack> accessor = ItemFrameAccessor.frameposer$itemData();
		frame.getEntityData().set(accessor, next);
	}

	public static void stamp(BlockEntity be, FramePose pose) {
		if (!(be instanceof Container container) || container.getContainerSize() <= 0) {
			return;
		}
		ItemStack old = container.getItem(0);
		if (old.isEmpty()) {
			return;
		}
		ItemStack next = apply(old, pose);
		if (ItemStack.matches(old, next)) {
			return;
		}
		container.setItem(0, next);
		be.setChanged();
	}

	public static void stampGroups(ItemFrame frame, List<String> groups) {
		ItemStack old = frame.getItem();
		if (old.isEmpty()) {
			return;
		}
		ItemStack next = applyGroups(old, groups);
		if (ItemStack.matches(old, next)) {
			return;
		}
		EntityDataAccessor<ItemStack> accessor = ItemFrameAccessor.frameposer$itemData();
		frame.getEntityData().set(accessor, next);
	}

	public static void stampGroups(BlockEntity be, List<String> groups) {
		if (!(be instanceof Container container) || container.getContainerSize() <= 0) {
			return;
		}
		ItemStack old = container.getItem(0);
		if (old.isEmpty()) {
			return;
		}
		ItemStack next = applyGroups(old, groups);
		if (ItemStack.matches(old, next)) {
			return;
		}
		container.setItem(0, next);
		be.setChanged();
	}

	public static ItemStack apply(ItemStack stack, FramePose pose) {
		ItemStack next = stack.copy();
		CustomData data = next.get(DataComponents.CUSTOM_DATA);
		CompoundTag root = data != null ? data.copyTag() : new CompoundTag();
		FramePose clean = pose == null ? FramePose.IDENTITY : pose.sanitized();
		if (clean.equals(FramePose.IDENTITY)) {
			root.remove(KEY);
		} else {
			root.store(KEY, FramePose.CODEC, clean);
		}
		if (root.isEmpty()) {
			next.remove(DataComponents.CUSTOM_DATA);
		} else {
			next.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		}
		return next;
	}

	public static ItemStack applyGroups(ItemStack stack, List<String> groups) {
		ItemStack next = stack.copy();
		CustomData data = next.get(DataComponents.CUSTOM_DATA);
		CompoundTag root = data != null ? data.copyTag() : new CompoundTag();
		List<String> clean = new ArrayList<>();
		if (groups != null) {
			for (String id : groups) {
				if (FrameGroupIds.valid(id) && !clean.contains(id)) {
					clean.add(id);
				}
			}
		}
		if (clean.isEmpty()) {
			root.remove(GROUPS_KEY);
		} else {
			root.store(GROUPS_KEY, GROUPS_CODEC, clean);
		}
		if (root.isEmpty()) {
			next.remove(DataComponents.CUSTOM_DATA);
		} else {
			next.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		}
		return next;
	}
}
