package frameposer.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemFrame.class)
public interface ItemFrameAccessor {
	@Accessor("fixed")
	boolean frameposer$isFixed();

	@Accessor("fixed")
	void frameposer$setFixed(boolean value);

	@Accessor("DATA_ITEM")
	static EntityDataAccessor<ItemStack> frameposer$itemData() {
		throw new AssertionError();
	}
}
