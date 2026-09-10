package frameposer.client;

import com.google.gson.Gson;
import frameposer.FastFrames;
import frameposer.FrameHandle;
import frameposer.FramePose;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public final class PoseCloud {
	// wait a couple ticks so a chunks worth of frames pile up, then
	// ask for all of them in one shot instead of one http call each
	private static final Gson GSON = new Gson();
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final int MAX_QUERY = 64;
	private static final int SETTLE_TICKS = 5;
	private static final long POSED_MS = 2500L;
	private static final long EMPTY_MS = 60000L;

	private static final byte UNKNOWN = 0;
	private static final byte EMPTY = 1;
	private static final byte POSED = 2;

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.build();

	private static final Map<String, Long> lastFetch = new HashMap<>();
	private static final Map<String, Byte> status = new HashMap<>();
	private static final Map<String, String> lastPush = new HashMap<>();
	private static final Set<String> pending = new HashSet<>();
	private static final Set<String> fifKeys = new HashSet<>();
	private static final Set<String> visible = new HashSet<>();
	private static boolean querying;
	private static String lastServer;
	private static int settleTicks;
	private static boolean fifLoaded = FabricLoader.getInstance().isModLoaded("fastitemframes");

	private PoseCloud() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || !ClientFramePoses.multiplayer()) {
			return;
		}
		if (querying) {
			return;
		}
		String server = ClientFramePoses.serverKey();
		if (!server.equals(lastServer)) {
			clear();
			lastServer = server;
		}
		if (settleTicks > 0 && --settleTicks > 0) {
			return;
		}
		boolean scan = client.player.tickCount % 5 == 0;
		if (pending.isEmpty() && !scan) {
			return;
		}
		collectNearby(client, !pending.isEmpty() || client.player.tickCount % 40 == 0);
		query(server);
	}

	public static void notice(String key) {
		if (queue(key)) {
			settleTicks = SETTLE_TICKS;
		}
	}

	public static void onChunk(LevelChunk chunk) {
		ingestChunk(chunk, true);
	}

	public static void push(Level level, FrameHandle handle, FramePose pose) {
		if (!ClientFramePoses.multiplayer()) {
			return;
		}
		String key = ClientFramePoses.cloudKey(level, handle);
		if (key == null) {
			return;
		}
		String base = baseUrl();
		if (base == null) {
			return;
		}
		FramePose next = pose == null ? FramePose.IDENTITY : pose.sanitized();
		String json = GSON.toJson(CloudPose.from(next));
		if (json.equals(lastPush.get(key))) {
			return;
		}
		lastPush.put(key, json);
		send("PUT", uri(base, ClientFramePoses.serverKey(), key), json);
		long now = System.currentTimeMillis();
		lastFetch.put(key, now);
		status.put(key, next.equals(FramePose.IDENTITY) ? EMPTY : POSED);
		pending.remove(key);
	}

	public static void disconnect() {
		clear();
	}

	private static void clear() {
		lastFetch.clear();
		status.clear();
		lastPush.clear();
		pending.clear();
		fifKeys.clear();
		visible.clear();
		lastServer = null;
		querying = false;
		settleTicks = 0;
	}

	private static void query(String server) {
		String base = baseUrl();
		if (base == null) {
			return;
		}
		long now = System.currentTimeMillis();
		List<String> wanted = new ArrayList<>();
		Set<String> picked = new HashSet<>();
		for (String key : pending) {
			if (!isDue(key, now) || !picked.add(key)) {
				continue;
			}
			wanted.add(key);
			if (wanted.size() >= MAX_QUERY) {
				break;
			}
		}
		if (wanted.size() < MAX_QUERY) {
			for (String key : visible) {
				if (!isDue(key, now) || !picked.add(key)) {
					continue;
				}
				wanted.add(key);
				if (wanted.size() >= MAX_QUERY) {
					break;
				}
			}
		}
		if (wanted.isEmpty()) {
			return;
		}
		querying = true;
		pending.removeAll(wanted);
		QueryBody body = new QueryBody();
		body.keys = wanted;
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v1/servers/" + enc(server) + "/query"))
			.timeout(TIMEOUT)
			.header("Content-Type", "application/json")
			.header("User-Agent", "FramePoser")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
			.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.whenComplete((response, error) -> {
				querying = false;
				if (error != null || response == null || response.statusCode() / 100 != 2) {
					for (String key : wanted) {
						pending.add(key);
					}
					return;
				}
				QueryResult result = GSON.fromJson(response.body(), QueryResult.class);
				Minecraft.getInstance().execute(() -> applyResult(wanted, result, System.currentTimeMillis()));
			});
	}

	private static boolean isDue(String key, long now) {
		Long seen = lastFetch.get(key);
		if (seen == null) {
			return true;
		}
		byte state = status.getOrDefault(key, UNKNOWN);
		if (state == EMPTY) {
			return now - seen >= EMPTY_MS;
		}
		if (state == POSED) {
			return now - seen >= POSED_MS;
		}
		return true;
	}

	private static void applyResult(List<String> wanted, QueryResult result, long now) {
		Set<String> hit = new HashSet<>();
		if (result != null && result.frames != null) {
			result.frames.forEach((key, cloud) -> {
				if (cloud == null) {
					return;
				}
				hit.add(key);
				FramePose pose = cloud.toPose();
				ClientFramePoses.applyCloud(key, pose);
				lastFetch.put(key, now);
				status.put(key, pose.equals(FramePose.IDENTITY) ? EMPTY : POSED);
			});
		}
		for (String key : wanted) {
			lastFetch.put(key, now);
			if (!hit.contains(key)) {
				status.put(key, EMPTY);
			}
		}
	}

	private static void send(String method, URI uri, String json) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(TIMEOUT)
			.header("User-Agent", "FramePoser");
		if ("DELETE".equals(method)) {
			builder.DELETE();
		} else {
			builder.header("Content-Type", "application/json");
			builder.method(method, HttpRequest.BodyPublishers.ofString(json));
		}
		HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding());
	}

	private static boolean queue(String key) {
		if (key == null || !ClientFramePoses.multiplayer()) {
			return false;
		}
		if (!status.containsKey(key) || status.get(key) == UNKNOWN) {
			return pending.add(key);
		}
		return false;
	}

	private static void ingestChunk(LevelChunk chunk, boolean settle) {
		if (!fifLoaded || chunk == null) {
			return;
		}
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			if (!FastFrames.isFrameBlock(be.getBlockState())) {
				continue;
			}
			String key = ClientFramePoses.cloudKey(be);
			fifKeys.add(key);
			visible.add(key);
			if (settle) {
				notice(key);
			} else {
				queue(key);
			}
		}
	}

	private static void collectNearby(Minecraft client, boolean walkFif) {
		Level level = client.level;
		visible.clear();
		int view = Math.max(2, client.options.getEffectiveRenderDistance());
		AABB box = client.player.getBoundingBox().inflate(view * 16.0);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
			String key = ClientFramePoses.cloudKey(frame);
			visible.add(key);
			queue(key);
		}
		if (!fifLoaded) {
			return;
		}
		if (!walkFif) {
			visible.addAll(fifKeys);
			return;
		}
		fifKeys.clear();
		var origin = client.player.chunkPosition();
		int originX = origin.x();
		int originZ = origin.z();
		for (int cx = originX - view; cx <= originX + view; cx++) {
			for (int cz = originZ - view; cz <= originZ + view; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				ingestChunk(level.getChunk(cx, cz), false);
			}
		}
	}

	private static String baseUrl() {
		PoseCloudConfig config = PoseCloudConfig.load();
		if (!config.enabled) {
			return null;
		}
		String url = config.url == null ? "" : config.url.trim();
		if (url.isEmpty()) {
			return null;
		}
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	private static URI uri(String base, String server, String frame) {
		return URI.create(base + "/v1/servers/" + enc(server) + "/frames/" + enc(frame));
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static final class QueryBody {
		List<String> keys;
	}

	private static final class QueryResult {
		Map<String, CloudPose> frames;
	}

	private static final class CloudPose {
		float rotX;
		float rotY;
		float rotZ;
		float offX;
		float offY;
		float offZ;
		float scale = 1.0F;
		boolean fixed;
		boolean invulnerable;

		static CloudPose from(FramePose pose) {
			CloudPose next = new CloudPose();
			next.rotX = pose.rotX();
			next.rotY = pose.rotY();
			next.rotZ = pose.rotZ();
			next.offX = pose.offX();
			next.offY = pose.offY();
			next.offZ = pose.offZ();
			next.scale = pose.scale();
			next.fixed = pose.fixed();
			next.invulnerable = pose.invulnerable();
			return next;
		}

		FramePose toPose() {
			return new FramePose(rotX, rotY, rotZ, offX, offY, offZ, scale <= 0.0F ? 1.0F : scale, fixed, invulnerable).sanitized();
		}
	}
}
