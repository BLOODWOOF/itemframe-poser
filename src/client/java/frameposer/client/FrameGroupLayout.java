package frameposer.client;

import frameposer.FrameHandle;
import frameposer.FrameLookup;
import frameposer.FramePose;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

// when a group of maps scales, shove offsets so the tiles stay stuck together
public final class FrameGroupLayout {
	private static final double MAP_UNIT = 0.0078125;

	private FrameGroupLayout() {
	}

	public static boolean isMap(Level level, FrameHandle handle) {
		ItemStack item = FrameLookup.item(level, handle);
		return item != null && item.is(Items.FILLED_MAP);
	}

	public static FramePose follow(
		Level level,
		FrameHandle pivot,
		FramePose oldPivot,
		FramePose nextPivot,
		FrameHandle member,
		FramePose oldMember
	) {
		if (!isMap(level, pivot) || !isMap(level, member)) {
			return nextPivot;
		}
		float from = oldMember.scale() <= 0.0F ? 1.0F : oldMember.scale();
		double ratio = nextPivot.scale() / from;
		Vec3 oldPivotAt = visual(level, pivot, oldPivot);
		Vec3 nextPivotAt = visual(level, pivot, nextPivot);
		Vec3 oldAt = visual(level, member, oldMember);
		if (oldPivotAt == null || nextPivotAt == null || oldAt == null) {
			return copyOnto(nextPivot, oldMember);
		}
		Vec3 nextAt = nextPivotAt.add(oldAt.subtract(oldPivotAt).scale(ratio));
		Vec3 origin = FrameLookup.origin(level, member);
		if (origin == null) {
			return copyOnto(nextPivot, oldMember);
		}
		Vec3 offset = worldToOffset(level, member, nextAt.subtract(origin));
		return new FramePose(
			nextPivot.rotX(),
			nextPivot.rotY(),
			nextPivot.rotZ(),
			(float) offset.x,
			(float) offset.y,
			(float) offset.z,
			nextPivot.scale(),
			nextPivot.fixed(),
			nextPivot.invulnerable()
		).sanitized();
	}

	private static FramePose copyOnto(FramePose nextPivot, FramePose oldMember) {
		return new FramePose(
			nextPivot.rotX(),
			nextPivot.rotY(),
			nextPivot.rotZ(),
			oldMember.offX(),
			oldMember.offY(),
			oldMember.offZ(),
			nextPivot.scale(),
			nextPivot.fixed(),
			nextPivot.invulnerable()
		).sanitized();
	}

	private static Vec3 visual(Level level, FrameHandle handle, FramePose pose) {
		Vec3 origin = FrameLookup.origin(level, handle);
		if (origin == null || pose == null) {
			return origin;
		}
		return origin.add(offsetToWorld(level, handle, pose.offX(), pose.offY(), pose.offZ()));
	}

	private static Vec3 offsetToWorld(Level level, FrameHandle handle, float offX, float offY, float offZ) {
		Vec3 local = new Vec3(offX * MAP_UNIT, offY * MAP_UNIT, offZ * MAP_UNIT);
		local = rotateZ(local, zRot(level, handle));
		return rotateFacing(local, facing(level, handle));
	}

	private static Vec3 worldToOffset(Level level, FrameHandle handle, Vec3 world) {
		Vec3 local = unrotateFacing(world, facing(level, handle));
		local = rotateZ(local, -zRot(level, handle));
		return new Vec3(local.x / MAP_UNIT, local.y / MAP_UNIT, local.z / MAP_UNIT);
	}

	private static float zRot(Level level, FrameHandle handle) {
		int rotation = rotation(level, handle);
		return (rotation % 4) * 90.0F + 180.0F;
	}

	private static Direction facing(Level level, FrameHandle handle) {
		if (!handle.block()) {
			Entity entity = level.getEntity(handle.entityId());
			if (entity instanceof ItemFrame frame) {
				return frame.getDirection();
			}
			return Direction.SOUTH;
		}
		BlockState state = level.getBlockState(handle.pos());
		Direction named = directionNamed(state, "facing");
		if (named != null) {
			return named;
		}
		for (Property<?> property : state.getProperties()) {
			Object value = state.getValue(property);
			if (value instanceof Direction direction) {
				return direction;
			}
		}
		return Direction.SOUTH;
	}

	private static Direction directionNamed(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (!property.getName().equals(name)) {
				continue;
			}
			Object value = state.getValue(property);
			if (value instanceof Direction direction) {
				return direction;
			}
		}
		return null;
	}

	private static int rotation(Level level, FrameHandle handle) {
		if (!handle.block()) {
			Entity entity = level.getEntity(handle.entityId());
			return entity instanceof ItemFrame frame ? frame.getRotation() : 0;
		}
		BlockState state = level.getBlockState(handle.pos());
		for (Property<?> property : state.getProperties()) {
			if (!property.getName().equals("rotation")) {
				continue;
			}
			Object value = state.getValue(property);
			if (value instanceof Number number) {
				return number.intValue();
			}
		}
		return 0;
	}

	private static Vec3 rotateFacing(Vec3 local, Direction facing) {
		float xRot;
		float yRot;
		if (facing.getAxis().isHorizontal()) {
			xRot = 0.0F;
			yRot = 180.0F - facing.toYRot();
		} else {
			xRot = -90.0F * facing.getAxisDirection().getStep();
			yRot = 180.0F;
		}
		Vec3 rotated = rotateY(local, yRot);
		return rotateX(rotated, xRot);
	}

	private static Vec3 unrotateFacing(Vec3 world, Direction facing) {
		float xRot;
		float yRot;
		if (facing.getAxis().isHorizontal()) {
			xRot = 0.0F;
			yRot = 180.0F - facing.toYRot();
		} else {
			xRot = -90.0F * facing.getAxisDirection().getStep();
			yRot = 180.0F;
		}
		Vec3 local = rotateX(world, -xRot);
		return rotateY(local, -yRot);
	}

	private static Vec3 rotateX(Vec3 v, float degrees) {
		double rad = Math.toRadians(degrees);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
	}

	private static Vec3 rotateY(Vec3 v, float degrees) {
		double rad = Math.toRadians(degrees);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
	}

	private static Vec3 rotateZ(Vec3 v, float degrees) {
		double rad = Math.toRadians(degrees);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		return new Vec3(v.x * cos - v.y * sin, v.x * sin + v.y * cos, v.z);
	}
}
