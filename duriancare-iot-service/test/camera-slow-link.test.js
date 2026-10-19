"use strict";
// Reproduces the 2026-10-18 field condition: the camera answers immediately but the JPEG body
// trickles in (66.5 KB in 7.1 s). Upstream is a local stub with marker bytes, NOT physical evidence.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const express = require("express");
const { createCameraRouter } = require("../src/camera/cameraRoutes");

const JPEG = Buffer.concat([Buffer.from([0xFF, 0xD8, 0xFF, 0xE0]), Buffer.alloc(2000, 0x11), Buffer.from([0xFF, 0xD9])]);

function withLimits(idle, total, fn) {
  const saved = [process.env.CAMERA_DOWNLOAD_IDLE_MS, process.env.CAMERA_DOWNLOAD_TOTAL_MS];
  process.env.CAMERA_DOWNLOAD_IDLE_MS = String(idle);
  process.env.CAMERA_DOWNLOAD_TOTAL_MS = String(total);
  return Promise.resolve().then(fn).finally(() => {
    for (const [k, v] of [["CAMERA_DOWNLOAD_IDLE_MS", saved[0]], ["CAMERA_DOWNLOAD_TOTAL_MS", saved[1]]]) {
      if (v === undefined) delete process.env[k]; else process.env[k] = v;
    }
  });
}

// mode: "trickle" (chunks every gapMs), "stall" (headers then nothing)
async function harness(behaviour) {
  const sockets = new Set();
  const upstream = http.createServer((req, res) => {
    const b = behaviour();
    res.writeHead(200, { "Content-Type": "image/jpeg", "Content-Length": JPEG.length });
    if (b.mode === "stall") { res.write(JPEG.subarray(0, 4)); return; }
    const parts = b.parts;
    const size = Math.ceil(JPEG.length / parts);
    let i = 0;
    const tick = () => {
      if (i >= parts) return res.end();
      res.write(JPEG.subarray(i * size, (i + 1) * size));
      i++;
      setTimeout(tick, b.gapMs);
    };
    tick();
  });
  upstream.on("connection", s => { sockets.add(s); s.on("close", () => sockets.delete(s)); });
  await new Promise(r => upstream.listen(0, "127.0.0.1", r));
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-slowlink-test-"));
  const camera = { device_id: "cam-a", farm_id: "farm-a", zone_id: null, ip_address: "127.0.0.1",
    port: upstream.address().port, protocol: "http", online: true, last_seen: new Date().toISOString() };
  const rows = [];
  const pool = { query: async (sql, args) => {
    if (sql.includes("INSERT INTO")) { const r = { id: `cap-${rows.length + 1}`, image_url: args[1] }; rows.push(r); return { rows: [r] }; }
    return { rows: [camera] };
  } };
  const app = express();
  app.use((req, res, next) => { req.headers["x-auth-user-id"] = "owner"; req.headers["x-auth-role"] = "FARMER"; next(); });
  app.use(createCameraRouter({ pool, schema: "fixture", uploadsDir: dir, serverBaseUrl: "http://gw", aiServiceUrl: "",
    farmAccessClient: { canReadIot: async () => true, canManageIot: async () => true } }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(r => server.once("listening", r));
  const base = `http://127.0.0.1:${server.address().port}`;
  return {
    base, dir, rows,
    files: () => fs.existsSync(path.join(dir, "cameras")) ? fs.readdirSync(path.join(dir, "cameras")) : [],
    close: async () => {
      for (const s of sockets) s.destroy();
      await new Promise(r => server.close(r));
      await new Promise(r => upstream.close(r));
      fs.rmSync(dir, { recursive: true, force: true });
    },
  };
}

test("a slow but progressing camera transfer longer than the idle limit is captured and stored", async () => {
  // 10 chunks x 100 ms = ~1 s total, every gap < 300 ms idle limit, total < 3 s cap.
  await withLimits(300, 3000, async () => {
    const h = await harness(() => ({ mode: "trickle", parts: 10, gapMs: 100 }));
    try {
      const started = Date.now();
      const res = await fetch(`${h.base}/capture-now?device_id=cam-a`, { method: "POST" });
      assert.equal(res.status, 201);
      assert.ok(Date.now() - started > 300, "transfer outlasted the idle limit, proving it is not a total cap");
      assert.equal((await res.json()).storage, "LOCAL");
      assert.equal(h.files().length, 1);
      assert.deepEqual(fs.readFileSync(path.join(h.dir, "cameras", h.files()[0])), JPEG);
    } finally { await h.close(); }
  });
});

test("the old behaviour (total cap shorter than the transfer) fails without leaving a file", async () => {
  await withLimits(300, 300, async () => {  // equivalent of the former fixed total cap
    const h = await harness(() => ({ mode: "trickle", parts: 10, gapMs: 100 }));
    try {
      assert.equal((await fetch(`${h.base}/capture-now?device_id=cam-a`, { method: "POST" })).status, 502);
      assert.deepEqual(h.files(), []);
      assert.equal(h.rows.length, 0);
    } finally { await h.close(); }
  });
});

test("a stalled camera fails at the idle limit, and the next capture still succeeds", async () => {
  await withLimits(300, 3000, async () => {
    let stall = true;
    const h = await harness(() => (stall ? { mode: "stall" } : { mode: "trickle", parts: 2, gapMs: 10 }));
    try {
      const started = Date.now();
      assert.equal((await fetch(`${h.base}/capture-now?device_id=cam-a`, { method: "POST" })).status, 502);
      assert.ok(Date.now() - started < 2500, "stall detected by the idle limit, well before the total cap");
      assert.deepEqual(h.files(), []);
      stall = false;
      assert.equal((await fetch(`${h.base}/capture-now?device_id=cam-a`, { method: "POST" })).status, 201);
      assert.equal(h.files().length, 1);
      assert.equal(h.rows.length, 1);
    } finally { await h.close(); }
  });
});

test("live snapshot uses the same idle/total rule for slow links", async () => {
  await withLimits(300, 3000, async () => {
    const h = await harness(() => ({ mode: "trickle", parts: 10, gapMs: 100 }));
    try {
      const res = await fetch(`${h.base}/snapshot?device_id=cam-a`);
      assert.equal(res.status, 200);
      assert.deepEqual(Buffer.from(await res.arrayBuffer()), JPEG);
    } finally { await h.close(); }
  });
});
