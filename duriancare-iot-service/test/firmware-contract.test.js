"use strict";
// Pins the HTTP contract sent by the ESP32-CAM firmware (CameraWebServer.ino postRegistry)
// against the real public router mount used by main.js (/api/v1/public).
const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const express = require("express");
const { createPublicRouter } = require("../src/public/publicRoutes");

function withEnv(vars, fn) {
  const saved = Object.fromEntries(Object.keys(vars).map(k => [k, process.env[k]]));
  Object.assign(process.env, vars);
  return Promise.resolve().then(fn).finally(() => {
    for (const [k, v] of Object.entries(saved)) if (v === undefined) delete process.env[k]; else process.env[k] = v;
  });
}

async function startPublicApp(pool) {
  const app = express();
  app.use(express.json({ limit: "32kb" }));
  app.use("/api/v1/public", createPublicRouter({ pool, schema: "fixture" }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  return { server, base: `http://127.0.0.1:${server.address().port}/api/v1/public` };
}

function fixturePool(initial) {
  let row = initial && { ...initial };
  const pool = { writes: 0, query: async (sql, args) => {
    if (sql.trimStart().startsWith("SELECT")) return { rows: row ? [{ ...row }] : [] };
    if (!row || row.farm_id !== args[5] || (row.last_seen && new Date(row.last_seen) >= new Date(args[3]))) return { rows: [] };
    row = { ...row, ip_address: args[1], port: args[2], last_seen: args[3], protocol: args[4] ?? row.protocol, online: true };
    pool.writes++;
    return { rows: [{ ...row }] };
  } };
  pool.row = () => row;
  return pool;
}

const provisioned = { device_id: "ESP32-CAM-001", farm_id: "farm-khoa-luan-2026", zone_id: null, tree_id: null,
  protocol: "http", port: 80, ip_address: "192.168.1.10", last_seen: null, online: false };

// Exact field set built by firmware 2.0.0 (no farmId/zoneId/treeId).
const firmwareBody = (announcedAt, ipAddress = "192.168.1.3") => ({ cameraId: "ESP32-CAM-001", ipAddress,
  port: 80, protocol: "http", announcedAt, firmwareVersion: "2.0.0" });

test("firmware 2.0.0 register + heartbeat payload is accepted through /api/v1/public mount", async () => {
  const key = crypto.randomBytes(24).toString("base64url");
  await withEnv({ CAMERA_DEVICE_BINDINGS: JSON.stringify({ "ESP32-CAM-001": { key, farmId: "farm-khoa-luan-2026" } }) }, async () => {
    const pool = fixturePool(provisioned);
    const { server, base } = await startPublicApp(pool);
    const post = (route, body, credential = key) => fetch(`${base}/cameras/${route}`, { method: "POST",
      headers: { "Content-Type": "application/json", "X-Camera-Key": credential }, body: JSON.stringify(body) });
    try {
      const t0 = Date.now();
      const reg = await post("register", firmwareBody(t0));
      assert.equal(reg.status, 200);
      const regBody = await reg.json();
      assert.equal(regBody.status, "registered");
      assert.equal(regBody.device.status, "ONLINE");
      assert.equal(regBody.device.farm_id, "farm-khoa-luan-2026", "scope comes from server provisioning");
      assert.equal(JSON.stringify(regBody).includes(key), false, "response never echoes the device key");

      // DHCP change: heartbeat with a later timestamp moves the origin.
      const hb = await post("heartbeat", firmwareBody(t0 + 1, "192.168.1.4"));
      assert.equal(hb.status, 200);
      assert.equal((await hb.json()).status, "ok");
      assert.equal(pool.row().ip_address, "192.168.1.4");
      assert.equal(pool.writes, 2);
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
});

test("heartbeat whose response was lost: committed once, retry advances, late original cannot overwrite", async () => {
  // Firmware 2.0.3 keeps registration on HTTPC_ERROR_READ_TIMEOUT (-11) and retries with a newer announcedAt.
  const key = crypto.randomBytes(24).toString("base64url");
  await withEnv({ CAMERA_DEVICE_BINDINGS: JSON.stringify({ "ESP32-CAM-001": { key, farmId: "farm-khoa-luan-2026" } }) }, async () => {
    const pool = fixturePool({ ...provisioned, online: true, last_seen: new Date(Date.now() - 60000).toISOString() });
    const { server, base } = await startPublicApp(pool);
    const post = body => fetch(`${base}/cameras/heartbeat`, { method: "POST",
      headers: { "Content-Type": "application/json", "X-Camera-Key": key }, body: JSON.stringify(body) });
    try {
      const t1 = Date.now() - 2000;
      const lost = firmwareBody(t1);
      // 1. Server commits the heartbeat; the client never sees this response (read timeout).
      assert.equal((await post(lost)).status, 200);
      assert.equal(new Date(pool.row().last_seen).getTime(), t1);
      // 2. Retry after backoff with a newer timestamp succeeds and advances last_seen.
      const t2 = t1 + 1500;
      const retry = await post(firmwareBody(t2));
      assert.equal(retry.status, 200);
      const body = await retry.json();
      assert.equal(body.status, "ok");
      assert.equal(body.device.cameraId, "ESP32-CAM-001", "firmware validates device.cameraId");
      assert.ok(Buffer.byteLength(JSON.stringify(body)) <= 4096, "fits REGISTRY_MAX_RESPONSE_BYTES");
      assert.equal(new Date(pool.row().last_seen).getTime(), t2);
      // 3. A delayed duplicate of the lost request (TCP retransmit) cannot move last_seen backwards.
      assert.equal((await post(lost)).status, 409);
      assert.equal(new Date(pool.row().last_seen).getTime(), t2);
      assert.equal(pool.writes, 2, "no extra writes; heartbeat has no capture side effects");
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
});

test("registry handler answers well inside the firmware response timeout", async () => {
  const key = crypto.randomBytes(24).toString("base64url");
  await withEnv({ CAMERA_DEVICE_BINDINGS: JSON.stringify({ "ESP32-CAM-001": { key, farmId: "farm-khoa-luan-2026" } }) }, async () => {
    const { server, base } = await startPublicApp(fixturePool(provisioned));
    try {
      const started = Date.now();
      const res = await fetch(`${base}/cameras/heartbeat`, { method: "POST",
        headers: { "Content-Type": "application/json", "X-Camera-Key": key }, body: JSON.stringify(firmwareBody(Date.now())) });
      assert.equal(res.status, 200);
      assert.ok(Date.now() - started < 1000, "handler is not the bottleneck vs the 15 s client timeout");
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
});

test("legacy firmware shapes are rejected: dev key, missing/ISO/seconds/stale announcedAt", async () => {
  const key = crypto.randomBytes(24).toString("base64url");
  await withEnv({ CAMERA_DEVICE_BINDINGS: JSON.stringify({ "ESP32-CAM-001": { key, farmId: "farm-khoa-luan-2026" } }) }, async () => {
    const pool = fixturePool(provisioned);
    const { server, base } = await startPublicApp(pool);
    const post = (body, credential = key) => fetch(`${base}/cameras/register`, { method: "POST",
      headers: { "Content-Type": "application/json", "X-Camera-Key": credential }, body: JSON.stringify(body) });
    try {
      const { announcedAt: _omit, ...noTimestamp } = firmwareBody(0);
      // arduino/DurianCare_ESP32_CAM.ino 1.0.0 shape with its shared dev key.
      assert.equal((await post({ ...noTimestamp, macAddress: "68:09:47:2D:8E:60", ssid: "x" }, "duriancare-esp32-dev-key")).status, 401);
      assert.equal((await post(noTimestamp)).status, 400);
      assert.equal((await post(firmwareBody(new Date().toISOString()))).status, 400, "ISO-8601 string is not epoch ms");
      assert.equal((await post(firmwareBody(Math.floor(Date.now() / 1000)))).status, 400, "epoch seconds rejected");
      assert.equal((await post(firmwareBody(Date.now() - 60000))).status, 400, "older than 30 s rejected");
      assert.equal((await post(firmwareBody(12345))).status, 400, "uptime millis rejected");
      assert.equal((await post({ ...firmwareBody(Date.now()), cameraId: "ESP32-CAM-999" })).status, 401, "unbound camera");
      assert.equal((await post(firmwareBody(Date.now()), "")).status, 401, "missing key");
      assert.equal(pool.writes, 0);
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
});

test("bound but unprovisioned camera gets 404; missing bindings gives 503", async () => {
  const key = crypto.randomBytes(24).toString("base64url");
  await withEnv({ CAMERA_DEVICE_BINDINGS: JSON.stringify({ "ESP32-CAM-001": { key, farmId: "farm-khoa-luan-2026" } }) }, async () => {
    const { server, base } = await startPublicApp(fixturePool(null));
    try {
      const res = await fetch(`${base}/cameras/register`, { method: "POST",
        headers: { "Content-Type": "application/json", "X-Camera-Key": key }, body: JSON.stringify(firmwareBody(Date.now())) });
      assert.equal(res.status, 404);
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
  await withEnv({ CAMERA_DEVICE_BINDINGS: "" }, async () => {
    const { server, base } = await startPublicApp(fixturePool(provisioned));
    try {
      const res = await fetch(`${base}/cameras/register`, { method: "POST",
        headers: { "Content-Type": "application/json", "X-Camera-Key": key }, body: JSON.stringify(firmwareBody(Date.now())) });
      assert.equal(res.status, 503);
      assert.equal((await res.json()).code, "CAMERA_BINDINGS_UNAVAILABLE");
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
});

test("responses carry an RFC 7231 Date header the firmware time fallback can parse", async () => {
  // CameraWebServer.ino syncTimeFromBackend(): strptime(date, "%a, %d %b %Y %H:%M:%S GMT").
  const { server, base } = await startPublicApp(fixturePool(null));
  try {
    const res = await fetch(`${base}/devices/mqtt-config`);
    const date = res.headers.get("date");
    assert.match(date, /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \d{2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{4} \d{2}:\d{2}:\d{2} GMT$/);
    assert.ok(Math.abs(Date.parse(date) - Date.now()) < 2000, "within the 30 s announcedAt window with margin");
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});

test("mqtt-config is mounted at /api/v1/public/devices and validates configuration", async () => {
  await withEnv({ MQTT_PUBLIC_HOST: "192.168.1.8", MQTT_PUBLIC_PORT: "1883", MQTT_REGISTER_TOPIC: "esp32/camera/register" }, async () => {
    const { server, base } = await startPublicApp(fixturePool(null));
    try {
      const res = await fetch(`${base}/devices/mqtt-config`);
      assert.equal(res.status, 200);
      assert.match(res.headers.get("content-type"), /application\/json/);
      assert.deepEqual(await res.json(), { mqtt_server: "192.168.1.8", mqtt_port: 1883, mqtt_topic: "esp32/camera/register" });
    } finally {
      await new Promise(resolve => server.close(resolve));
    }
  });
});
