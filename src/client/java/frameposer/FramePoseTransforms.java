package frameposer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public final class FramePoseTransforms {
	private FramePoseTransforms() {
	}

	public static void apply(PoseStack poseStack, FramePose pose) {
		if (pose == null || pose.isIdentityTransform()) {
			return;
		}
		poseStack.translate(pose.offX(), pose.offY(), pose.offZ());
		poseStack.mulPose(Axis.XP.rotationDegrees(pose.rotX()));
		poseStack.mulPose(Axis.YP.rotationDegrees(pose.rotY()));
		poseStack.mulPose(Axis.ZP.rotationDegrees(pose.rotZ()));
		float scale = pose.scale();
		if (scale != 1.0F) {
			poseStack.scale(scale, scale, scale);
		}
	}
}
