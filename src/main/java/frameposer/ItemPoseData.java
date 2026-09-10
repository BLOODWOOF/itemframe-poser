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

// Vanilla already syncs whatever is on the framed item, so we piggyback the pose there.
public final class ItemPoseData {
	private static final String KEY = "frameposer";

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
}
