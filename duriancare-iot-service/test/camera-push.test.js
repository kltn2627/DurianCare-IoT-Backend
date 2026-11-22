"use strict";
// PUSH delivery: capture-now queues a command, the camera polls it, uploads a JPEG, the command completes and
// the "live" snapshot serves the stored frame. The backend never opens a connection to the camera.
// In-memory fixture pool (no database), real routers, temp uploads directory.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const http = require("node:http");
const express = require("express");

function memoryPool() {
  const db = { cameras: new Map(), commands: [], captures: [] };
  const now = () => new Date();
  const pool = {
    db,
    async query(sql, args = []) {
      const s = sql.replace(/\s+/g, " ").trim();
      if (/FROM "fx".iot_devices/.test(s)) return { rows: [] };
      if (/^SELECT .* FROM "fx".camera_devices WHERE device_id = \$1/.test(s)) {
        const row = db.cameras.get(args[0]); return { rows: row ? [{ ...row }] : [] };
      }
      if (/^SELECT .* FROM "fx".camera_devices ORDER BY online DESC/.test(s)) return { rows: [...db.cameras.values()].map(r => ({ ...r })) };
      if (/^UPDATE "fx".camera_devices SET ip_address = \$2/.test(s)) {
        const row = db.cameras.get(args[0]);
        if (!row || (args[5] && row.farm_id !== args[5])) return { rows: [] };
        Object.assign(row, { ip_address: args[1], port: args[2], online: true, last_seen: args[3], updated_at: args[3],
          camera_ready: args[7], last_error: args[8], firmware_version: args[9] ?? row.firmware_version });
        return { rows: [{ ...row }] };
      }
      if (/^UPDATE "fx".camera_devices SET diagnostics/.test(s)) {
        const row = db.cameras.get(args[0]);
        if (args[1]) row.diagnostics = JSON.parse(args[1]);
        if (args[2]) row.capabilities_reported = args[2];
        if (args[3] !== null) row.wifi_rssi = args[3];
        return { rows: [{ ...row }] };
      }
      if (/^UPDATE "fx".camera_devices SET last_frame_at/.test(s)) { db.cameras.get(args[0]).last_frame_at = now(); return { rows: [] }; }
      if (/^UPDATE "fx".camera_commands SET status = 'EXPIRED'/.test(s)) {
        for (const c of db.commands) if (c.device_id === args[0] && ["PENDING", "DELIVERED"].includes(c.status) &&
          c.expires_at <= now()) c.status = "EXPIRED";
        return { rows: [] };
      }
      if (/^SELECT .* FROM "fx".camera_commands WHERE device_id = \$1 AND reason = \$2 AND preview = \$3/.test(s)) {
        return { rows: db.commands.filter(c => c.device_id === args[0] && c.reason === args[1] && c.preview === args[2] &&
          ["PENDING", "DELIVERED"].includes(c.status)) };
      }
      if (/^INSERT INTO "fx".camera_commands/.test(s)) {
        const c = { id: crypto.randomUUID(), device_id: args[0], type: "CAPTURE", reason: args[1], preview: args[2],
          status: "PENDING", requested_by: args[3], created_at: now(), delivered_at: null, completed_at: null,
          expires_at: new Date(Date.now() + args[4] * 1000), capture_id: null, error: null };
        db.commands.push(c); return { rows: [c] };
      }
      if (/^UPDATE "fx".camera_commands c SET status = 'DELIVERED'/.test(s)) {
        const open = db.commands.filter(c => c.device_id === args[0] && ["PENDING", "DELIVERED"].includes(c.status)).slice(0, args[1]);
        for (const c of open) { c.status = "DELIVERED"; c.delivered_at ??= now(); }
        return { rows: open.map(c => ({ ...c })) };
      }
      if (/^SELECT COUNT\(\*\)::int AS n FROM "fx".camera_commands/.test(s)) {
        return { rows: [{ n: db.commands.filter(c => c.device_id === args[0] && ["PENDING", "DELIVERED"].includes(c.status)).length }] };
      }
      if (/^SELECT .* FROM "fx".camera_commands WHERE id = \$1 AND device_id = \$2/.test(s)) {
        return { rows: db.commands.filter(c => c.id === args[0] && c.device_id === args[1] && ["PENDING", "DELIVERED"].includes(c.status)) };
      }
      if (/^SELECT .* FROM "fx".camera_commands WHERE id = \$1/.test(s)) return { rows: db.commands.filter(c => c.id === args[0]) };
      if (/^UPDATE "fx".camera_commands SET status = 'COMPLETED'/.test(s)) {
        const c = db.commands.find(x => x.id === args[0]); Object.assign(c, { status: "COMPLETED", capture_id: args[1] }); return { rows: [] };
      }
      if (/^UPDATE "fx".camera_commands SET status = 'FAILED'/.test(s)) {
        const c = db.commands.find(x => x.id === args[0] && x.device_id === args[1] && ["PENDING", "DELIVERED"].includes(x.status));
        if (c) Object.assign(c, { status: "FAILED", error: args[2] }); return { rowCount: c ? 1 : 0, rows: [] };
      }
      if (/^DELETE FROM "fx".camera_commands WHERE device_id = \$2 AND preview = TRUE/.test(s)) {
        db.commands = db.commands.filter(c => !(c.device_id === args[1] && c.preview === true && c.id === args[0]));
        return { rows: [] };
      }
      if (/^INSERT INTO "fx".camera_captures/.test(s)) {
        const row = { id: crypto.randomUUID(), device_id: args[0], image_url: args[1], capture_type: args[2],
          captured_at: args[3], ai_status: args[4], delivery: args[5], command_id: args[6] };
        db.captures.push(row); return { rows: [row] };
      }
      if (/^SELECT .* FROM "fx".camera_captures WHERE device_id = \$1 ORDER BY captured_at DESC LIMIT 1/.test(s)) {
        const list = db.captures.filter(c => c.device_id === args[0]).sort((a, b) => b.captured_at.localeCompare(a.captured_at));
        return { rows: list.slice(0, 1) };
      }
      if (/^SELECT .* FROM "fx".camera_captures WHERE id = \$1/.test(s)) return { rows: db.captures.filter(c => c.id === args[0]) };
      throw new Error(`fixture pool: unexpected SQL ${s.slice(0, 80)}`);
    },
  };
  return pool;
}

const JPEG = Buffer.concat([Buffer.from([0xFF, 0xD8, 0xFF, 0xE0]), crypto.randomBytes(64), Buffer.from([0xFF, 0xD9])]);

test("push flow: capture-now -> command poll -> frame upload -> command completed -> latest frame", async () => {
  const saved = { ...process.env };
  const key = crypto.randomBytes(32).toString("hex");
  Object.assign(process.env, { CAMERA_DELIVERY_MODE: "PUSH", IOT_DEPLOYMENT_MODE: "CLOUD",
    CAMERA_DEVICE_BINDINGS: JSON.stringify({ "cam-1": { key, farmId: "farm-1" } }) });
  const uploadsDir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-push-"));
  // Any outbound HTTP from the backend in this test would be a pull: fail loudly instead.
  const realGet = http.get;
  http.get = () => { throw new Error("backend must not connect to the camera in PUSH mode"); };
  const pool = memoryPool();
  pool.db.cameras.set("cam-1", { device_id: "cam-1", farm_id: "farm-1", zone_id: null, tree_id: null,
    ip_address: "192.168.1.50", port: 80, protocol: "http", online: false, last_seen: null });
  const { createRegistrationRouter } = require("../src/camera/registryRuntime");
  const { createCameraRouter } = require("../src/camera/cameraRoutes");
  const app = express();
  app.use(express.json({ limit: "32kb" }));
  app.use("/api/v1/public/cameras", createRegistrationRouter({ pool, schema: "fx", serverBaseUrl: "http://gw",
    uploadsDir, aiServiceUrl: "" }));
  app.use("/api/v1/camera", createCameraRouter({ pool, schema: "fx", serverBaseUrl: "http://gw", uploadsDir,
    aiServiceUrl: "", farmAccessClient: { canReadIot: async () => true, canManageIot: async () => true } }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const user = { "X-Auth-User-Id": "farmer-1", "X-Auth-Role": "FARMER" };
  const cam = { "X-Camera-Key": key };
  try {
    // Old firmware (no "push" capability) cannot serve capture-now in PUSH mode.
    const heartbeat = (extra = {}) => fetch(`${base}/api/v1/public/cameras/heartbeat`, { method: "POST",
      headers: { ...cam, "Content-Type": "application/json" },
      body: JSON.stringify({ cameraId: "cam-1", ipAddress: "192.168.1.50", port: 80, protocol: "http",
        announcedAt: Date.now(), cameraReady: true, ...extra }) });
    assert.equal((await heartbeat()).status, 200);
    assert.equal((await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user })).status, 409);

    const hb = await (await heartbeat({ capabilities: ["push", "commands"],
      diagnostics: { wifiRssi: -84, vdd33: 3300, resetReason: "POWERON", freeHeap: 90000, bogus: "ignored" } })).json();
    assert.equal(hb.device.status, "DEGRADED");
    assert.deepEqual(hb.device.statusReasons, ["WIFI_WEAK"]);
    assert.equal(hb.commandsPending, 0);
    assert.equal(pool.db.cameras.get("cam-1").diagnostics.bogus, undefined);

    // Farmer: list hides the LAN address.
    const list = await (await fetch(`${base}/api/v1/camera/devices`, { headers: user })).json();
    assert.equal(list.devices[0].ip_address, undefined);
    assert.equal(list.devices[0].deliveryMode, "PUSH");

    assert.equal((await fetch(`${base}/api/v1/public/cameras/cam-1/commands`, { headers: cam })).status, 204);
    assert.equal((await fetch(`${base}/api/v1/camera/snapshot?device_id=cam-1`, { headers: user })).status, 404);

    const queued = await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user });
    assert.equal(queued.status, 202);
    const { commandId } = await queued.json();
    // A double click reuses the open command.
    assert.equal((await (await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user })).json()).commandId, commandId);

    assert.equal((await fetch(`${base}/api/v1/public/cameras/cam-1/commands`, { headers: { "X-Camera-Key": "wrong" } })).status, 401);
    const polled = await (await fetch(`${base}/api/v1/public/cameras/cam-1/commands`, { headers: cam })).json();
    assert.equal(polled.commands[0].id, commandId);
    assert.equal(polled.commands[0].reason, "ON_DEMAND");

    const upload = (query, body = JPEG, type = "image/jpeg") => fetch(`${base}/api/v1/public/cameras/cam-1/frames?${query}`,
      { method: "POST", headers: { ...cam, "Content-Type": type }, body });
    const t = Date.now();
    assert.equal((await upload(`capturedAt=${t}&reason=NOPE`)).status, 400);
    assert.equal((await upload(`capturedAt=${t - 3600_000}&reason=ON_DEMAND`)).status, 400);
    assert.equal((await upload(`capturedAt=${t}&reason=ON_DEMAND`, Buffer.from("not a jpeg"))).status, 400);
    assert.equal((await upload(`capturedAt=${t}&reason=ON_DEMAND&commandId=${crypto.randomUUID()}`)).status, 409);
    assert.equal((await upload(`capturedAt=${t}&reason=ON_DEMAND`, JPEG, "application/json")).status, 400);
    const stored = await upload(`capturedAt=${t}&reason=ON_DEMAND&commandId=${commandId}`);
    assert.equal(stored.status, 201);
    const frame = await stored.json();
    assert.match(frame.imageUrl, /^\/api\/v1\/camera\/image\/cam_\d+\.jpg$/);

    const progress = await (await fetch(`${base}/api/v1/camera/commands/${commandId}?device_id=cam-1`, { headers: user })).json();
    assert.equal(progress.status, "COMPLETED");
    assert.equal(progress.capture.id, frame.captureId);
    assert.equal(pool.db.captures[0].capture_type, "MANUAL");
    assert.equal(pool.db.captures[0].delivery, "PUSH");

    const latest = await fetch(`${base}/api/v1/camera/snapshot?device_id=cam-1`, { headers: user });
    assert.equal(latest.status, 200);
    assert.ok(latest.headers.get("x-frame-captured-at"));
    assert.deepEqual(Buffer.from(await latest.arrayBuffer()), JPEG);

    // Camera-reported failure closes a command.
    const again = await (await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user })).json();
    const fail = await fetch(`${base}/api/v1/public/cameras/cam-1/commands/${again.commandId}/result`, { method: "POST",
      headers: { ...cam, "Content-Type": "application/json" }, body: JSON.stringify({ status: "FAILED", error: "CAMERA_CAPTURE_FAILED" }) });
    assert.equal(fail.status, 204);
    assert.equal((await (await fetch(`${base}/api/v1/camera/commands/${again.commandId}?device_id=cam-1`, { headers: user })).json()).status, "FAILED");
  } finally {
    http.get = realGet;
    await new Promise(resolve => server.close(resolve));
    for (const k of Object.keys(process.env)) if (!(k in saved)) delete process.env[k];
    Object.assign(process.env, saved);
    fs.rmSync(uploadsDir, { recursive: true, force: true });
  }
});

test("live view: preview frames stay in memory, never in history, and win over an older stored frame", async () => {
  const saved = { ...process.env };
  const key = crypto.randomBytes(32).toString("hex");
  Object.assign(process.env, { CAMERA_DELIVERY_MODE: "PUSH", IOT_DEPLOYMENT_MODE: "CLOUD",
    CAMERA_DEVICE_BINDINGS: JSON.stringify({ "cam-1": { key, farmId: "farm-1" } }) });
  const uploadsDir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-live-"));
  const livePreview = require("../src/camera/livePreview");
  livePreview.clear();
  const pool = memoryPool();
  pool.db.cameras.set("cam-1", { device_id: "cam-1", farm_id: "farm-1", zone_id: null, tree_id: null,
    ip_address: "192.168.1.50", port: 80, protocol: "http", online: false, last_seen: null });
  const { createRegistrationRouter } = require("../src/camera/registryRuntime");
  const { createCameraRouter } = require("../src/camera/cameraRoutes");
  const app = express();
  app.use(express.json({ limit: "32kb" }));
  app.use("/api/v1/public/cameras", createRegistrationRouter({ pool, schema: "fx", serverBaseUrl: "http://gw",
    uploadsDir, aiServiceUrl: "http://ai.invalid" }));
  app.use("/api/v1/camera", createCameraRouter({ pool, schema: "fx", serverBaseUrl: "http://gw", uploadsDir,
    aiServiceUrl: "http://ai.invalid", farmAccessClient: { canReadIot: async () => true, canManageIot: async () => true } }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const user = { "X-Auth-User-Id": "farmer-1", "X-Auth-Role": "FARMER", "Content-Type": "application/json" };
  const cam = { "X-Camera-Key": key };
  const upload = (query, body) => fetch(`${base}/api/v1/public/cameras/cam-1/frames?${query}`,
    { method: "POST", headers: { ...cam, "Content-Type": "image/jpeg" }, body });
  const snapshot = () => fetch(`${base}/api/v1/camera/snapshot?device_id=cam-1`, { headers: user });
  try {
    await fetch(`${base}/api/v1/public/cameras/heartbeat`, { method: "POST", headers: { ...cam, "Content-Type": "application/json" },
      body: JSON.stringify({ cameraId: "cam-1", ipAddress: "192.168.1.50", port: 80, protocol: "http", announcedAt: Date.now(),
        cameraReady: true, capabilities: ["push", "commands"] }) });

    // An old stored photo (the dark frame the farmer saw) exists first.
    const first = await (await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user })).json();
    await fetch(`${base}/api/v1/public/cameras/cam-1/commands`, { headers: cam });
    const oldAt = Date.now() - 60_000;
    assert.equal((await upload(`capturedAt=${oldAt}&reason=ON_DEMAND&commandId=${first.commandId}`, JPEG)).status, 201);
    assert.equal(pool.db.captures.length, 1);

    const live = await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user,
      body: JSON.stringify({ device_id: "cam-1", preview: true }) });
    assert.equal(live.status, 202);
    const { commandId } = await live.json();
    const polled = await (await fetch(`${base}/api/v1/public/cameras/cam-1/commands`, { headers: cam })).json();
    assert.equal(polled.commands[0].preview, true);
    assert.equal(polled.pollAfterMs, 2000, "camera polls faster while someone watches live");

    const LIVE = Buffer.concat([Buffer.from([0xFF, 0xD8, 0xFF, 0xE0]), crypto.randomBytes(48), Buffer.from([0xFF, 0xD9])]);
    const res = await upload(`capturedAt=${Date.now()}&reason=ON_DEMAND&commandId=${commandId}`, LIVE);
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.preview, true);
    assert.equal(body.captureId, null);
    assert.equal(pool.db.captures.length, 1, "preview frames are not history");
    assert.equal(pool.db.commands.some(c => c.id === commandId), false, "finished preview command row is removed");
    assert.equal(fs.readdirSync(path.join(uploadsDir, "cameras")).length, 1, "preview frames are not written to disk");

    const shown = await snapshot();
    assert.equal(shown.status, 200);
    assert.equal(shown.headers.get("x-frame-live"), "1");
    assert.deepEqual(Buffer.from(await shown.arrayBuffer()), LIVE);

    // A newer real photo replaces the live frame.
    const photo = await (await fetch(`${base}/api/v1/camera/capture-now?device_id=cam-1`, { method: "POST", headers: user })).json();
    await fetch(`${base}/api/v1/public/cameras/cam-1/commands`, { headers: cam });
    assert.equal((await upload(`capturedAt=${Date.now() + 1000}&reason=ON_DEMAND&commandId=${photo.commandId}`, JPEG)).status, 201);
    const after = await snapshot();
    assert.equal(after.headers.get("x-frame-live"), null);
    assert.deepEqual(Buffer.from(await after.arrayBuffer()), JPEG);
  } finally {
    livePreview.clear();
    await new Promise(resolve => server.close(resolve));
    for (const k of Object.keys(process.env)) if (!(k in saved)) delete process.env[k];
    Object.assign(process.env, saved);
    fs.rmSync(uploadsDir, { recursive: true, force: true });
  }
});

test("livePreview keeps the newest frame and drops stale ones", () => {
  const livePreview = require("../src/camera/livePreview");
  livePreview.clear();
  const t = Date.now();
  assert.equal(livePreview.setFrame("c", Buffer.from("a"), new Date(t)), true);
  assert.equal(livePreview.setFrame("c", Buffer.from("old"), new Date(t - 1000)), false);
  assert.equal(livePreview.getFrame("c", t).buffer.toString(), "a");
  assert.equal(livePreview.getFrame("c", t + livePreview.MAX_AGE_MS + 1), null);
  livePreview.clear();
});
