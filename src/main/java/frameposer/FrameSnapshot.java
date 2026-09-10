package frameposer;

public record FrameSnapshot(
	FrameHandle handle,
	FramePose pose,
	boolean invisible,
	boolean glowing
) {
	public FrameSnapshot withHandle(FrameHandle next) {
		return new FrameSnapshot(next, pose, invisible, glowing);
	}

	public FrameSnapshot withPose(FramePose next) {
		return new FrameSnapshot(handle, next, invisible, glowing);
	}

	public FrameSnapshot withInvisible(boolean next) {
		return new FrameSnapshot(handle, pose, next, glowing);
	}

	public FrameSnapshot withGlowing(boolean next) {
		return new FrameSnapshot(handle, pose, invisible, next);
	}
}
