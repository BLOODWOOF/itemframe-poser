import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// same routes as the old worker, just dumped onto disk instead of KV
// one json file per minecraft server so it stays persistant if node restarts
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";
const DIR = path.dirname(fileURLToPath(import.meta.url));
const DATA = process.env.DATA_DIR || path.join(DIR, "data");

const SERVER_OK = /^[A-Za-z0-9._-]{1,80}$/;
const MAX_BODY = 64 * 1024;
const MAX_KEYS = 64;
const LATEST = process.env.LATEST_VERSION || "1.3.5";

const CORS = {
	"Access-Control-Allow-Origin": "*",
	"Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
	"Access-Control-Allow-Headers": "Content-Type, User-Agent, If-None-Match, X-FramePoser-Version",
};

const memory = new Map();

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

function versionOk(req) {
	const got = req.headers["x-frameposer-version"] || "";
	return !!got && versionAtLeast(got, LATEST);
}

function versionAtLeast(got, need) {
	const a = parseVer(got);
	const b = parseVer(need);
	for (let i = 0; i < 3; i++) {
		if (a[i] > b[i]) {
			return true;
		}
		if (a[i] < b[i]) {
			return false;
		}
	}
	return true;
}

function parseVer(value) {
	const core = String(value || "").split(/[+-]/)[0];
	const parts = core.split(".");
	return [Number(parts[0]) || 0, Number(parts[1]) || 0, Number(parts[2]) || 0];
}

function fileFor(server) {
	return path.join(DATA, server + ".json");
}

function loadServer(server) {
	if (memory.has(server)) {
		return memory.get(server);
	}
	const file = fileFor(server);
	if (!fs.existsSync(file)) {
		memory.set(server, {});
		return memory.get(server);
	}
	try {
		const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
		const dump = parsed && typeof parsed === "object" ? parsed : {};
		memory.set(server, dump);
		return dump;
	} catch {
		memory.set(server, {});
		return memory.get(server);
	}
}

function saveServer(server, dump) {
	fs.mkdirSync(DATA, { recursive: true });
	const file = fileFor(server);
	const tmp = file + ".tmp";
	fs.writeFileSync(tmp, JSON.stringify(dump));
	fs.renameSync(tmp, file);
}

function send(res, status, payload) {
	const body = JSON.stringify(payload);
	res.writeHead(status, {
		...CORS,
		"Content-Type": "application/json",
		"Content-Length": Buffer.byteLength(body),
	});
	res.end(body);
}

function readBody(req) {
	return new Promise((resolve, reject) => {
		const chunks = [];
		let size = 0;
		req.on("data", (chunk) => {
			size += chunk.length;
			if (size > MAX_BODY) {
				reject(Object.assign(new Error("too big"), { code: 413 }));
				req.destroy();
				return;
			}
			chunks.push(chunk);
		});
		req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
		req.on("error", reject);
	});
}

async function putFrame(server, frame, req) {
	if (!SERVER_OK.test(server) || !validFrame(frame)) {
		return { status: 400, payload: { error: "bad request" } };
	}
	const body = await readBody(req);
	let pose;
	try {
		pose = JSON.parse(body);
	} catch {
		return { status: 400, payload: { error: "bad json" } };
	}
	if (!pose || typeof pose !== "object") {
		return { status: 400, payload: { error: "bad json" } };
	}
	const dump = loadServer(server);
	dump[frame] = pose;
	saveServer(server, dump);
	return { status: 200, payload: { ok: true } };
}

async function deleteFrame(server, frame) {
	if (!SERVER_OK.test(server) || !validFrame(frame)) {
		return { status: 400, payload: { error: "bad request" } };
	}
	const dump = loadServer(server);
	delete dump[frame];
	saveServer(server, dump);
	return { status: 200, payload: { ok: true } };
}

async function queryFrames(server, req) {
	if (!SERVER_OK.test(server)) {
		return { status: 400, payload: { error: "bad request" } };
	}
	const body = await readBody(req);
	let payload;
	try {
		payload = JSON.parse(body);
	} catch {
		return { status: 400, payload: { error: "bad json" } };
	}
	const keys = payload && Array.isArray(payload.keys) ? payload.keys.slice(0, MAX_KEYS) : [];
	const dump = loadServer(server);
	const frames = {};
	for (const key of keys) {
		if (typeof key === "string" && dump[key]) {
			frames[key] = dump[key];
		}
	}
	return { status: 200, payload: { frames } };
}

const server = http.createServer(async (req, res) => {
	if (req.method === "OPTIONS") {
		res.writeHead(204, CORS);
		res.end();
		return;
	}

	let url;
	try {
		url = new URL(req.url || "/", "http://localhost");
	} catch {
		send(res, 400, { error: "bad url" });
		return;
	}

	if (url.pathname === "/v1/version" && req.method === "GET") {
		send(res, 200, { version: LATEST });
		return;
	}

	const putMatch = url.pathname.match(/^\/v1\/servers\/([^/]+)\/frames\/(.+)$/);
	const queryMatch = url.pathname.match(/^\/v1\/servers\/([^/]+)\/query$/);

	if ((putMatch || queryMatch) && !versionOk(req)) {
		send(res, 426, { error: "upgrade", version: LATEST });
		return;
	}

	try {
		if (putMatch && req.method === "PUT") {
			const result = await putFrame(decode(putMatch[1]), decode(putMatch[2]), req);
			send(res, result.status, result.payload);
			return;
		}
		if (putMatch && req.method === "DELETE") {
			const result = await deleteFrame(decode(putMatch[1]), decode(putMatch[2]));
			send(res, result.status, result.payload);
			return;
		}
		if (queryMatch && req.method === "POST") {
			const result = await queryFrames(decode(queryMatch[1]), req);
			send(res, result.status, result.payload);
			return;
		}
	} catch (err) {
		if (err && err.code === 413) {
			send(res, 413, { error: "too big" });
			return;
		}
		send(res, 500, { error: "store failed" });
		return;
	}

	send(res, 404, { error: "not found" });
});

server.listen(PORT, HOST, () => {
	console.log("frameposer store on http://" + HOST + ":" + PORT);
});
