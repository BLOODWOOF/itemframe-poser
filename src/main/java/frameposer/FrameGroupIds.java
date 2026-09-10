package frameposer;

public final class FrameGroupIds {
	public static final String[] ALL = {"1", "2", "3", "4", "5", "6", "7", "8"};

	private FrameGroupIds() {
	}

	public static boolean valid(String id) {
		return id != null && id.length() == 1 && id.charAt(0) >= '1' && id.charAt(0) <= '8';
	}
}
