package frameposer;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GlowInk {
	private GlowInk() {
	}

	public static boolean hasSac(Player player) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return player.getInventory().contains(stack -> stack.is(Items.GLOW_INK_SAC));
	}

	public static boolean takeSac(Player player) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(Items.GLOW_INK_SAC)) {
				continue;
			}
			stack.shrink(1);
			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
			return true;
		}
		return false;
	}
}
