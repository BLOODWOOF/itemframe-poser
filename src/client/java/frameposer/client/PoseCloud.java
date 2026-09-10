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
	// one dump for the whole minecraft server, then we just paint whatever
	// is in view. slider drags get coalesced so we dont put every twitch
	private static final Gson GSON = new Gson();
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final int SETTLE_TICKS = 5;
	private static final long PUSH_GAP_MS = 1000L;
	private static final long DUMP_POSED_MS = 2000L;
	private static final long DUMP_EMPTY_MS = 5000L;

	private static final byte UNKNOWN = 0;
	private static final byte EMPTY = 1;
	private static final byte POSED = 2;

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.build();

	private static final Map<String, Byte> status = new HashMap<>();
	private static final Map<String, String> lastSent = new HashMap<>();
	private static final Map<String, Long> lastSentAt = new HashMap<>();
	private static final Map<String, Outgoing> outbound = new HashMap<>();
	private static final Set<String> pending = new HashSet<>();
	private static final Set<String> fifKeys = new HashSet<>();
	private static final Set<String> visible = new HashSet<>();
	private static final Map<String, CloudPose> dump = new HashMap<>();
	private static final Map<String, CloudPose> applied = new HashMap<>();
	private static String dumpEtag = "";
	private static long dumpAt;
	private static boolean dumpHadPoses;
	private static boolean querying;
	private static String lastServer;
	private static int settleTicks;
	private static boolean fifLoaded = FabricLoader.getInstance().isModLoaded("fastitemframes");

	private PoseCloud() {
	}

	public static void tick(Minecraft client) {
		flushPushes(false);
		if (client.player == null || client.level == null || !ClientFramePoses.multiplayer()) {
			return;
		}
		String server = ClientFramePoses.serverKey();
		if (lastServer == null || !lastServer.equals(server)) {
			flushPushes(true);
			clear();
			lastServer = server;
		}
		if (settleTicks > 0 && --settleTicks > 0) {
			return;
		}
		boolean scan = client.player.tickCount % 5 == 0;
		if (scan || !pending.isEmpty()) {
			collectNearby(client, !pending.isEmpty() || client.player.tickCount % 40 == 0);
			if (dumpAt != 0 && !pending.isEmpty()) {
				applyDump(false);
			}
			pending.clear();
		}
		if (querying) {
			return;
		}
		long now = System.currentTimeMillis();
		long gap = dumpHadPoses ? DUMP_POSED_MS : DUMP_EMPTY_MS;
		if (dumpAt != 0 && now - dumpAt < gap) {
			return;
		}
		fetchDump(server);
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
		if (key == null || baseUrl() == null) {
			return;
		}
		FramePose next = pose == null ? FramePose.IDENTITY : pose.sanitized();
		String json = GSON.toJson(CloudPose.from(next));
		if (json.equals(lastSent.get(key))) {
			outbound.remove(key);
			return;
		}
		long now = System.currentTimeMillis();
		Outgoing waiting = new Outgoing();
		waiting.server = ClientFramePoses.serverKey();
		waiting.key = key;
		waiting.json = json;
		waiting.empty = next.equals(FramePose.IDENTITY);
		Long sentAt = lastSentAt.get(key);
		if (sentAt == null || now - sentAt >= PUSH_GAP_MS) {
			waiting.due = now;
		} else {
			waiting.due = sentAt + PUSH_GAP_MS;
		}
		outbound.put(key, waiting);
	}

	public static void flush() {
		flushPushes(true);
	}

	public static void disconnect() {
		flushPushes(true);
		clear();
	}

	private static void clear() {
		status.clear();
		lastSent.clear();
		lastSentAt.clear();
		outbound.clear();
		pending.clear();
		fifKeys.clear();
		visible.clear();
		dump.clear();
		applied.clear();
		dumpEtag = "";
		dumpAt = 0;
		dumpHadPoses = false;
		lastServer = null;
		querying = false;
		settleTicks = 0;
	}

	private static void flushPushes(boolean all) {
		if (outbound.isEmpty()) {
			return;
		}
		String base = baseUrl();
		if (base == null) {
			outbound.clear();
			return;
		}
		long now = System.currentTimeMillis();
		List<String> done = new ArrayList<>();
		for (Outgoing waiting : outbound.values()) {
			if (!all && now < waiting.due) {
				continue;
			}
			send("PUT", uri(base, waiting.server, waiting.key), waiting.json);
			lastSent.put(waiting.key, waiting.json);
			lastSentAt.put(waiting.key, now);
			status.put(waiting.key, waiting.empty ? EMPTY : POSED);
			pending.remove(waiting.key);
			dumpEtag = "";
			if (waiting.empty) {
				dump.remove(waiting.key);
				applied.remove(waiting.key);
			} else {
				CloudPose pose = GSON.fromJson(waiting.json, CloudPose.class);
				dump.put(waiting.key, pose);
				applied.put(waiting.key, pose);
			}
			done.add(waiting.key);
		}
		dumpHadPoses = !dump.isEmpty();
		for (String key : done) {
			outbound.remove(key);
		}
	}

	private static void fetchDump(String server) {
		String base = baseUrl();
		if (base == null) {
			return;
		}
		querying = true;
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/v1/servers/" + enc(server)))
			.timeout(TIMEOUT)
			.header("User-Agent", "FramePoser")
			.GET();
		if (!dumpEtag.isEmpty()) {
			builder.header("If-None-Match", dumpEtag);
		}
		HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
			.whenComplete((response, error) -> {
				querying = false;
				if (error != null || response == null) {
					Minecraft.getInstance().execute(PoseCloud::markDumpAttempt);
					return;
				}
				int code = response.statusCode();
				if (code == 304) {
					Minecraft.getInstance().execute(() -> {
						dumpAt = System.currentTimeMillis();
						applyDump(true);
					});
					return;
				}
				if (code / 100 != 2) {
					Minecraft.getInstance().execute(PoseCloud::markDumpAttempt);
					return;
				}
				String tag = response.headers().firstValue("ETag").orElse("");
				QueryResult result = GSON.fromJson(response.body(), QueryResult.class);
				Minecraft.getInstance().execute(() -> takeDump(result, tag));
			});
	}

	private static void markDumpAttempt() {
		dumpAt = System.currentTimeMillis();
	}

	private static void takeDump(QueryResult result, String tag) {
		dump.clear();
		dumpHadPoses = false;
		if (result != null && result.frames != null) {
			result.frames.forEach((key, cloud) -> {
				if (cloud == null || !validDumpKey(key)) {
					return;
				}
				dump.put(key, cloud);
				dumpHadPoses = true;
			});
		}
		dumpEtag = tag == null ? "" : tag;
		dumpAt = System.currentTimeMillis();
		applyDump(false);
	}

	private static boolean validDumpKey(String key) {
		return key != null && (key.startsWith("e:") || key.startsWith("b:")) && !key.contains("..");
	}

	// only paint a frame when its new or the dump pose actually moved
	private static void applyDump(boolean newcomersOnly) {
		for (String key : visible) {
			if (editing(key)) {
				continue;
			}
			byte was = status.getOrDefault(key, UNKNOWN);
			if (newcomersOnly && was != UNKNOWN) {
				continue;
			}
			CloudPose cloud = dump.get(key);
			if (cloud == null) {
				if (was == POSED) {
					ClientFramePoses.applyCloud(key, FramePose.IDENTITY);
				}
				applied.remove(key);
				status.put(key, EMPTY);
				continue;
			}
			if (was != UNKNOWN && cloud.equals(applied.get(key))) {
				continue;
			}
			ClientFramePoses.applyCloud(key, cloud.toPose());
			applied.put(key, cloud);
			status.put(key, POSED);
		}
	}

	private static boolean editing(String key) {
		if (outbound.containsKey(key)) {
			return true;
		}
		return FramePoserScreen.current != null && FramePoserScreen.current.ownsCloudKey(key);
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

	private static final class Outgoing {
		String server;
		String key;
		String json;
		boolean empty;
		long due;
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

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof CloudPose pose)) {
				return false;
			}
			return Float.compare(rotX, pose.rotX) == 0
				&& Float.compare(rotY, pose.rotY) == 0
				&& Float.compare(rotZ, pose.rotZ) == 0
				&& Float.compare(offX, pose.offX) == 0
				&& Float.compare(offY, pose.offY) == 0
				&& Float.compare(offZ, pose.offZ) == 0
				&& Float.compare(scale, pose.scale) == 0
				&& fixed == pose.fixed
				&& invulnerable == pose.invulnerable;
		}

		@Override
		public int hashCode() {
			int hash = Float.hashCode(rotX);
			hash = 31 * hash + Float.hashCode(rotY);
			hash = 31 * hash + Float.hashCode(rotZ);
			hash = 31 * hash + Float.hashCode(offX);
			hash = 31 * hash + Float.hashCode(offY);
			hash = 31 * hash + Float.hashCode(offZ);
			hash = 31 * hash + Float.hashCode(scale);
			hash = 31 * hash + Boolean.hashCode(fixed);
			hash = 31 * hash + Boolean.hashCode(invulnerable);
			return hash;
		}
	}
}
