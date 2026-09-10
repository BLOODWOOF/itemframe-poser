package frameposer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import frameposer.FramePose;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class PosePresets {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<SavedPose>>() {
	}.getType();

	private PosePresets() {
	}

	public record SavedPose(String name, FramePose pose, boolean invisible) {
	}

	public static List<SavedPose> load() {
		Path file = file();
		if (!Files.exists(file)) {
			return new ArrayList<>(builtins());
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			List<SavedPose> loaded = GSON.fromJson(reader, LIST_TYPE);
			return loaded == null ? new ArrayList<>(builtins()) : new ArrayList<>(loaded);
		} catch (Exception ignored) {
			return new ArrayList<>(builtins());
		}
	}

	public static List<SavedPose> builtins() {
		return List.of(
			new SavedPose("Default", FramePose.IDENTITY, false),
			new SavedPose("Tilted", new FramePose(22.0F, 0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 1.0F, false, false), false),
			new SavedPose("Pop Out", new FramePose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.08F, 1.2F, false, false), false),
			new SavedPose("Mini", new FramePose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.5F, false, false), false),
			new SavedPose("Large", new FramePose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.75F, false, false), false)
		);
	}

	public static void saveAll(List<SavedPose> poses) {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(poses, LIST_TYPE, writer);
			}
		} catch (IOException ignored) {
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("frameposer").resolve("presets.json");
	}
}
