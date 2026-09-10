package frameposer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class PoseCloudConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT_URL = "https://frameposer-store.austin-c21341.workers.dev";
	private static PoseCloudConfig cached;

	public boolean enabled = true;
	public String url = DEFAULT_URL;

	public PoseCloudConfig() {
	}

	public static PoseCloudConfig load() {
		if (cached != null) {
			return cached;
		}
		Path file = file();
		if (!Files.exists(file)) {
			cached = new PoseCloudConfig();
			save(cached);
			return cached;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			PoseCloudConfig loaded = GSON.fromJson(reader, PoseCloudConfig.class);
			cached = loaded == null ? new PoseCloudConfig() : loaded;
			if (cached.url == null || cached.url.isBlank()) {
				cached.url = DEFAULT_URL;
			}
			return cached;
		} catch (Exception ignored) {
			cached = new PoseCloudConfig();
			return cached;
		}
	}

	private static void save(PoseCloudConfig config) {
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(config, writer);
			}
		} catch (IOException ignored) {
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("frameposer").resolve("cloud.json");
	}
}
