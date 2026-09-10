package frameposer.client;

import frameposer.FrameHandle;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;

// client-only glow so you can spot the frame you clicked in the group list
public final class FrameGlowHandler {
	private static final int OUTLINE = 0xFFFFFF;
	private static FrameHandle highlighted;

	private FrameGlowHandler() {
	}

	public static void select(FrameHandle handle) {
		highlighted = handle;
	}

	public static void clear() {
		highlighted = null;
	}

	public static FrameHandle highlighted() {
		return highlighted;
	}

	public static boolean isGlowing(ItemFrame frame) {
		return highlighted != null && !highlighted.block() && highlighted.entityId() == frame.getId();
	}

	public static boolean isGlowing(BlockPos pos) {
		return highlighted != null && highlighted.block() && highlighted.pos().equals(pos);
	}

	public static void tint(ItemFrameRenderState state) {
		if (state != null) {
			state.outlineColor = OUTLINE;
		}
	}
}
