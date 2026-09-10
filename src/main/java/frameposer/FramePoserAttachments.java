package frameposer;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class FramePoserAttachments {
	public static final AttachmentType<FramePose> POSE = AttachmentRegistry.create(
		FramePoser.id("pose"),
		builder -> builder
			.initializer(FramePose::identity)
			.persistent(FramePose.CODEC)
			.syncWith(FramePose.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	private FramePoserAttachments() {
	}

	public static FramePose get(AttachmentTarget target) {
		FramePose pose = target.getAttached(POSE);
		return pose == null ? FramePose.IDENTITY : pose;
	}

	public static void set(AttachmentTarget target, FramePose pose) {
		FramePose next = pose == null ? FramePose.IDENTITY : pose.sanitized();
		if (next.equals(FramePose.IDENTITY)) {
			target.removeAttached(POSE);
		} else {
			target.setAttached(POSE, next);
		}
	}

	public static void copy(AttachmentTarget from, AttachmentTarget to) {
		set(to, get(from));
	}
}
