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
		} catch (err) {
			return json(500, { error: "store failed" });
		}

		return json(404, { error: "not found" });
	},
};

const SERVER_OK = /^[A-Za-z0-9._-]{1,80}$/;
const MAX_BODY = 64 * 1024;
const MAX_KEYS = 64;

function decode(value) {
	try {
		return decodeURIComponent(value);
	} catch {
		return value;
	}
}

function validFrame(frame) {
	return typeof frame === "string" && (frame.startsWith("e:") || frame.startsWith("b:")) && !frame.includes("..");
}

async function readServer(env, server) {
	const raw = await env.POSES.get(server);
	if (!raw) {
		return {};
	}
	try {
		const parsed = JSON.parse(raw);
		return parsed && typeof parsed === "object" ? parsed : {};
	} catch {
		return {};
	}
}

async function putFrame(env, server, frame, request) {
	if (!SERVER_OK.test(server) || !validFrame(frame)) {
		return json(400, { error: "bad request" });
	}
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
	const dump = await readServer(env, server);
	dump[frame] = pose;
	await env.POSES.put(server, JSON.stringify(dump));
	return json(200, { ok: true });
}

async function deleteFrame(env, server, frame) {
	if (!SERVER_OK.test(server) || !validFrame(frame)) {
		return json(400, { error: "bad request" });
	}
	const dump = await readServer(env, server);
	delete dump[frame];
	await env.POSES.put(server, JSON.stringify(dump));
	return json(200, { ok: true });
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
	const keys = Array.isArray(payload.keys) ? payload.keys.slice(0, MAX_KEYS) : [];
	const dump = await readServer(env, server);
	const frames = {};
	for (const key of keys) {
		if (typeof key === "string" && dump[key]) {
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
			"Access-Control-Allow-Origin": "*",
			"Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
			"Access-Control-Allow-Headers": "Content-Type, User-Agent",
		},
	});
}

function cors(response) {
	response.headers.set("Access-Control-Allow-Origin", "*");
	response.headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
	response.headers.set("Access-Control-Allow-Headers", "Content-Type, User-Agent");
	return response;
}
