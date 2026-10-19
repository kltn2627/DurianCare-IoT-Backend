"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { EventEmitter } = require("node:events");
const express = require("express");
const { captureAndStore } = require("../src/camera/cameraService");
const { createCameraRouter } = require("../src/camera/cameraRoutes");

for (const [label, status, type, body] of [
  ["empty 200", 200, "image/jpeg", Buffer.alloc(0)],
  ["HTML 200", 200, "text/html", Buffer.from("<html>camera error</html>")],
  ["truncated JPEG", 200, "image/jpeg", Buffer.from([255, 216, 255, 0])],
  // Firmware app_httpd.cpp is_valid_jpeg mirrors this rule: EOI must be the final two bytes.
  ["JPEG with bytes after EOI", 200, "image/jpeg", Buffer.from([255, 216, 255, 224, 255, 217, 0, 0])],
  ["upstream HTTP failure", 503, "text/plain", Buffer.from("offline")],
]) {
  test(`capture rejects ${label} before file or database persistence`, async () => {
    const server = http.createServer((req, res) => { res.writeHead(status, { "Content-Type": type }); res.end(body); });
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-camera-test-"));
    let queries = 0;
    try {
      await assert.rejects(captureAndStore({ pool: { query: async () => { queries++; return { rows: [{}] }; } },
        schema: "fixture", deviceId: "fixture-camera", captureType: "MANUAL", uploadsDir: dir,
        esp32CameraUrl: `http://127.0.0.1:${server.address().port}`, esp32CapturePath: "/capture",
        serverBaseUrl: "http://127.0.0.1", aiServiceUrl: "" }));
      assert.equal(queries, 0);
      assert.deepEqual(fs.readdirSync(path.join(dir, "cameras")), []);
    } finally {
      await new Promise(resolve => server.close(resolve));
      fs.rmSync(dir, { recursive: true });
    }
  });
}

test("capture timeout rejects without database writes", async (t) => {
  const request = new EventEmitter();
  let destroyed = false;
  request.destroy = () => { destroyed = true; };
  t.mock.method(http, "get", () => {
    queueMicrotask(() => request.emit("timeout"));
    return request;
  });
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-camera-test-"));
  try {
    await assert.rejects(captureAndStore({ pool: { query: () => assert.fail("unexpected persistence") },
      schema: "fixture", deviceId: "fixture-camera", uploadsDir: dir,
      esp32CameraUrl: "http://127.0.0.1", esp32CapturePath: "/capture" }), /timed out/);
    assert.equal(destroyed, true);
  } finally { fs.rmSync(dir, { recursive: true }); }
});

test("camera HTTP route resolves async URL before capture and never caches invalid image bodies", async () => {
  // Marker-only transport fixture, not a decoded camera image or physical hardware evidence.
  let body = Buffer.from([255, 216, 255, 217]);
  const upstream = http.createServer((req, res) => { res.writeHead(200, { "Content-Type": "image/jpeg" }); res.end(body); });
  await new Promise(resolve => upstream.listen(0, "127.0.0.1", resolve));
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-camera-test-"));
  let writes = 0;
  const pool = { query: async sql => {
    if (sql.includes("INSERT INTO")) { writes++; return { rows: [{ id: "fixture-capture" }] }; }
    return { rows: [{ device_id: "fixture-camera", ip_address: "127.0.0.1",
      port: upstream.address().port, protocol: "http", online: true, last_seen: new Date().toISOString(), farm_id: "fixture-farm" }] };
  } };
  const app = express();
  app.use(express.json());
  app.use((req, res, next) => { req.headers["x-auth-user-id"] = "fixture"; req.headers["x-auth-role"] = "FARMER"; next(); });
  app.use(createCameraRouter({ pool, schema: "fixture", uploadsDir: dir, serverBaseUrl: "http://localhost", aiServiceUrl: "",
    farmAccessClient: { canReadIot: async () => true, canManageIot: async () => true } }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const capture = await fetch(base + "/capture-now?device_id=fixture-camera", { method: "POST" });
    assert.equal(capture.status, 201);
    assert.equal(writes, 1);
    const filename = fs.readdirSync(path.join(dir, "cameras"))[0];
    const storedImage = await fetch(base + "/image/" + filename);
    assert.equal(storedImage.status, 200);
    assert.deepEqual(Buffer.from(await storedImage.arrayBuffer()), body);
    body = Buffer.from("<html>upstream error</html>");
    assert.equal((await fetch(base + "/snapshot?device_id=fixture-camera")).status, 502);
    assert.equal((await fetch(base + "/snapshot?device_id=fixture-camera")).status, 502);
    body = Buffer.from([255, 216, 255, 217]);
    assert.equal((await fetch(base + "/snapshot?device_id=fixture-camera")).status, 200);
    const cached = await fetch(base + "/snapshot?device_id=fixture-camera");
    assert.equal(cached.status, 200);
    assert.equal(cached.headers.get("x-frame-cached"), "1");
  } finally {
    await new Promise(resolve => server.close(resolve));
    await new Promise(resolve => upstream.close(resolve));
    fs.rmSync(dir, { recursive: true });
  }
});
