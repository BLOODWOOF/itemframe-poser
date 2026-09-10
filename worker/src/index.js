export default {
	async fetch(request, env) {
		if (request.method === "OPTIONS") {
			return cors(new Response(null, { status: 204 }));
		}

		let url;
		try {
			url = new URL(request.url);
		} catch {
			return json(400, { error: "bad url" });
		}

		const putMatch = url.pathname.match(/^\/v1\/servers\/([^/]+)\/frames\/(.+)$/);
		const queryMatch = url.pathname.match(/^\/v1\/servers\/([^/]+)\/query$/);
		const dumpMatch = url.pathname.match(/^\/v1\/servers\/([^/]+)$/);

		try {
			if (putMatch && request.method === "PUT") {
				return cors(await putFrame(env, decode(putMatch[1]), decode(putMatch[2]), request));
			}
			if (putMatch && request.method === "DELETE") {
				return cors(await deleteFrame(env, decode(putMatch[1]), decode(putMatch[2])));
			}
			if (queryMatch && request.method === "POST") {
				return cors(await queryFrames(env, decode(queryMatch[1]), request));
			}
			if (dumpMatch && request.method === "GET") {
				return cors(await getDump(env, decode(dumpMatch[1]), request));
			}
		} catch (err) {
			return json(500, { error: "store failed" });
		}

		return json(404, { error: "not found" });
	},
};

const SERVER_OK = /^[A-Za-z0-9._-]{1,80}$/;
const MAX_BODY = 64 * 1024;
const MAX_KEYS = 64;
const SKIP = "b:__migrated|0,0,0";

function decode(value) {
	try {
		return decodeURIComponent(value);
	} catch {
		return value;
	}
}

function validFrame(frame) {
	return typeof frame === "string" && (frame.startsWith("e:") || frame.startsWith("b:")) && !frame.includes("..") && frame !== SKIP;
}

function emptyPose(pose) {
	if (!pose || typeof pose !== "object") {
		return true;
	}
	const scale = pose.scale == null ? 1 : pose.scale;
	return !pose.rotX && !pose.rotY && !pose.rotZ && !pose.offX && !pose.offY && !pose.offZ && scale === 1 && !pose.fixed && !pose.invulnerable;
}

function etagFor(text) {
	let hash = 2166136261;
	for (let i = 0; i < text.length; i++) {
		hash ^= text.charCodeAt(i);
		hash = Math.imul(hash, 16777619);
	}
	return '"' + (hash >>> 0).toString(16) + "-" + text.length + '"';
}

function cleanDump(dump) {
	const frames = {};
	if (!dump || typeof dump !== "object") {
		return frames;
	}
	for (const [frame, pose] of Object.entries(dump)) {
		if (!validFrame(frame) || emptyPose(pose)) {
			continue;
		}
		frames[frame] = pose;
	}
	return frames;
}

async function ensureDumps(env) {
	await env.DB.prepare(
		"CREATE TABLE IF NOT EXISTS dumps (server TEXT PRIMARY KEY, json TEXT NOT NULL DEFAULT '{}')"
	).run();
}

async function loadDump(env, server) {
	try {
		return await readDump(env, server);
	} catch {
		await ensureDumps(env);
		return await readDump(env, server);
	}
}

async function readDump(env, server) {
	const row = await env.DB.prepare("SELECT json FROM dumps WHERE server = ?").bind(server).first();
	if (row && typeof row.json === "string") {
		try {
			return JSON.parse(row.json);
		} catch {
			return {};
		}
	}
	if (row && row.json && typeof row.json === "object") {
		return row.json;
	}
	const built = {};
	try {
		const rows = await env.DB.prepare("SELECT frame, json FROM poses WHERE server = ?").bind(server).all();
		for (const item of rows.results || []) {
			if (!validFrame(item.frame)) {
				continue;
			}
			try {
				built[item.frame] = JSON.parse(item.json);
			} catch {
				// skip junk
			}
		}
	} catch {
		// poses table might not exist on a fresh db
	}
	const frames = cleanDump(built);
	if (Object.keys(frames).length > 0) {
		await env.DB.prepare("INSERT OR IGNORE INTO dumps (server, json) VALUES (?, ?)").bind(server, JSON.stringify(frames)).run();
	}
	return frames;
}

async function patchDump(env, server, frame, pose) {
	const patch = JSON.stringify({ [frame]: pose });
	if (pose === null) {
		await env.DB.prepare("UPDATE dumps SET json = json_patch(json, ?) WHERE server = ?").bind(patch, server).run();
		return;
	}
	await env.DB.prepare(
		"INSERT INTO dumps (server, json) VALUES (?, ?) ON CONFLICT(server) DO UPDATE SET json = json_patch(json, excluded.json)"
	).bind(server, patch).run();
}

async function putFrame(env, server, frame, request) {
	if (!SERVER_OK.test(server) || !validFrame(frame)) {
		return json(400, { error: "bad request" });
	}
	await loadDump(env, server);
	const body = await request.text();
	if (body.length > MAX_BODY) {
		return json(413, { error: "too big" });
	}
	let pose;
	try {
		pose = JSON.parse(body);
	} catch {
		return json(400, { error: "bad json" });
	}
	if (!pose || typeof pose !== "object") {
		return json(400, { error: "bad json" });
	}
	await patchDump(env, server, frame, emptyPose(pose) ? null : pose);
	return json(200, { ok: true });
}

async function deleteFrame(env, server, frame) {
	if (!SERVER_OK.test(server) || !validFrame(frame)) {
		return json(400, { error: "bad request" });
	}
	await patchDump(env, server, frame, null);
	return json(200, { ok: true });
}

async function getDump(env, server, request) {
	if (!SERVER_OK.test(server)) {
		return json(400, { error: "bad url" });
	}
	const frames = cleanDump(await loadDump(env, server));
	const body = JSON.stringify({ frames });
	const etag = etagFor(body);
	if (request.headers.get("If-None-Match") === etag) {
		return new Response(null, {
			status: 304,
			headers: {
				ETag: etag,
				"Cache-Control": "public, max-age=2",
				"Access-Control-Allow-Origin": "*",
			},
		});
	}
	return new Response(body, {
		status: 200,
		headers: {
			"Content-Type": "application/json",
			ETag: etag,
			"Cache-Control": "public, max-age=2",
			"Access-Control-Allow-Origin": "*",
			"Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
			"Access-Control-Allow-Headers": "Content-Type, User-Agent, If-None-Match",
		},
	});
}

async function queryFrames(env, server, request) {
	if (!SERVER_OK.test(server)) {
		return json(400, { error: "bad request" });
	}
	const body = await request.text();
	if (body.length > MAX_BODY) {
		return json(413, { error: "too big" });
	}
	let payload;
	try {
		payload = JSON.parse(body);
	} catch {
		return json(400, { error: "bad json" });
	}
	const keys = payload && Array.isArray(payload.keys) ? payload.keys.filter((key) => typeof key === "string").slice(0, MAX_KEYS) : [];
	const dump = cleanDump(await loadDump(env, server));
	const frames = {};
	for (const key of keys) {
		if (dump[key]) {
			frames[key] = dump[key];
		}
	}
	return json(200, { frames });
}

function json(status, payload) {
	return new Response(JSON.stringify(payload), {
		status,
		headers: {
			"Content-Type": "application/json",
			"Cache-Control": "no-store",
			"Access-Control-Allow-Origin": "*",
			"Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
			"Access-Control-Allow-Headers": "Content-Type, User-Agent, If-None-Match",
		},
	});
}

function cors(response) {
	response.headers.set("Access-Control-Allow-Origin", "*");
	response.headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
	response.headers.set("Access-Control-Allow-Headers", "Content-Type, User-Agent, If-None-Match");
	return response;
}
