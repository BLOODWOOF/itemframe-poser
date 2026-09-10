package frameposer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class FastFrames {
	public static final String MOD_ID = "fastitemframes";
	public static final Identifier ITEM_FRAME = Identifier.fromNamespaceAndPath(MOD_ID, "item_frame");
	public static final Identifier GLOW_ITEM_FRAME = Identifier.fromNamespaceAndPath(MOD_ID, "glow_item_frame");

	private FastFrames() {
	}

	public static boolean isFrameBlock(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return isFrameId(id);
	}

	public static boolean isGlowFrame(BlockState state) {
		return GLOW_ITEM_FRAME.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	public static boolean isFrameId(Identifier id) {
		return ITEM_FRAME.equals(id) || GLOW_ITEM_FRAME.equals(id);
	}

	public static Block frameBlock(boolean glowing) {
		return BuiltInRegistries.BLOCK.getValue(glowing ? GLOW_ITEM_FRAME : ITEM_FRAME);
	}

	public static boolean boolValue(BlockState state, String name, boolean fallback) {
		BooleanProperty property = boolProperty(state, name);
		return property == null ? fallback : state.getValue(property);
	}

	public static BlockState setBool(BlockState state, String name, boolean value) {
		BooleanProperty property = boolProperty(state, name);
		return property == null ? state : state.setValue(property, value);
	}

	public static BlockState copySharedProperties(BlockState from, BlockState to) {
		BlockState next = to;
		for (Property<?> property : from.getProperties()) {
			next = copyProperty(next, from, property);
		}
		return next;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState copyProperty(BlockState to, BlockState from, Property<T> property) {
		if (!to.hasProperty(property)) {
			return to;
		}
		return to.setValue(property, from.getValue(property));
	}

	private static BooleanProperty boolProperty(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(name) && property instanceof BooleanProperty bool) {
				return bool;
			}
		}
		return null;
	}
}
