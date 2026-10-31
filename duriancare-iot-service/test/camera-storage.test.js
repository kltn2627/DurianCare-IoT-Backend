"use strict";
// Local persistent capture storage and authorized retrieval. Upstream "camera" is a local HTTP stub
// serving marker JPEG bytes; this is NOT physical-camera evidence.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const express = require("express");
const { createCameraRouter } = require("../src/camera/cameraRoutes");

const JPEG = Buffer.from([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0xFF, 0xD9]);

async function harness({ upstreamBody = JPEG, insertFails = false, uploadsDir } = {}) {
  const upstream = http.createServer((req, res) => {
    res.writeHead(200, { "Content-Type": "image/jpeg" });
    res.end(typeof upstreamBody === "function" ? upstreamBody() : upstreamBody);
  });
  await new Promise(r => upstream.listen(0, "127.0.0.1", r));
  const dir = uploadsDir ?? fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-storage-test-"));
  const camera = { device_id: "cam-a", farm_id: "farm-a", zone_id: null, ip_address: "127.0.0.1",
    port: upstream.address().port, protocol: "http", online: true, last_seen: new Date().toISOString() };
  const captures = [];
  const pool = { query: async (sql, args) => {
    if (sql.includes("INSERT INTO")) {
      if (insertFails) throw new Error("db down");
      const row = { id: `cap-${captures.length + 1}`, device_id: args[0], image_url: args[1], capture_type: args[2],
        captured_at: args[3], ai_status: args[4] };
      captures.push(row);
      return { rows: [row] };
    }
    if (sql.includes("right(image_url")) {
      return { rows: captures.filter(c => c.image_url.endsWith(args[0])).map(c => ({ device_id: c.device_id })) };
    }
    return { rows: [camera] };
  } };
  const app = express();
  app.use(express.json());
  app.use((req, res, next) => {  // stands in for the Gateway's JWT -> identity headers
    if (req.get("x-test-user")) { req.headers["x-auth-user-id"] = req.get("x-test-user"); req.headers["x-auth-role"] = "FARMER"; }
    next();
  });
  app.use(createCameraRouter({ pool, schema: "fixture", uploadsDir: dir, serverBaseUrl: "http://gateway.test", aiServiceUrl: "",
    farmAccessClient: { canReadIot: async a => a.userId === "owner", canManageIot: async a => a.userId === "owner" } }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(r => server.once("listening", r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const as = (user, p, init = {}) => fetch(base + p, { ...init, headers: { ...(user ? { "x-test-user": user } : {}), ...(init.headers || {}) } });
  const close = async () => {
    await new Promise(r => server.close(r));
    await new Promise(r => upstream.close(r));
    if (!uploadsDir) fs.rmSync(dir, { recursive: true, force: true });
  };
  return { as, dir, captures, close };
}

const files = dir => fs.existsSync(path.join(dir, "cameras")) ? fs.readdirSync(path.join(dir, "cameras")) : [];

test("capture is saved locally, recorded once, tagged LOCAL, and leaves no temp files", async () => {
  const h = await harness();
  try {
    const res = await h.as("owner", "/capture-now?device_id=cam-a", { method: "POST" });
    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.storage, "LOCAL", "never reported as an S3 upload");
    assert.equal(h.captures.length, 1);
    const stored = files(h.dir);
    assert.equal(stored.length, 1);
    assert.match(stored[0], /^cam_\d+\.jpg$/);
    assert.ok(body.image_url.endsWith(`/api/v1/camera/image/${stored[0]}`));
    assert.deepEqual(fs.readFileSync(path.join(h.dir, "cameras", stored[0])), JPEG);
  } finally { await h.close(); }
});

test("repeated captures get distinct files and records (no overwrite)", async () => {
  const h = await harness();
  try {
    for (let i = 0; i < 3; i++) assert.equal((await h.as("owner", "/capture-now?device_id=cam-a", { method: "POST" })).status, 201);
    assert.equal(new Set(files(h.dir)).size, 3);
    assert.equal(new Set(h.captures.map(c => c.image_url)).size, 3);
  } finally { await h.close(); }
});

test("database failure removes the saved file (no orphan) and reports failure", async () => {
  const h = await harness({ insertFails: true });
  try {
    assert.equal((await h.as("owner", "/capture-now?device_id=cam-a", { method: "POST" })).status, 502);
    assert.deepEqual(files(h.dir), []);
  } finally { await h.close(); }
});

test("local storage failure returns an explicit error and writes no record", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "duriancare-storage-test-"));
  fs.writeFileSync(path.join(dir, "cameras"), "not a directory");  // uploads/cameras unusable
  const h = await harness({ uploadsDir: dir });
  try {
    const res = await h.as("owner", "/capture-now?device_id=cam-a", { method: "POST" });
    assert.equal(res.status, 502);
    assert.equal((await res.json()).code, "CAMERA_CAPTURE_FAILED");
    assert.equal(h.captures.length, 0);
  } finally { await h.close(); fs.rmSync(dir, { recursive: true, force: true }); }
});

test("invalid upstream content and oversized frames are rejected before storage", async () => {
  for (const body of [Buffer.from("<html>error</html>"), Buffer.alloc(5 * 1024 * 1024, 0xFF)]) {
    const h = await harness({ upstreamBody: body });
    try {
      assert.equal((await h.as("owner", "/capture-now?device_id=cam-a", { method: "POST" })).status, 502);
      assert.deepEqual(files(h.dir), []);
      assert.equal(h.captures.length, 0);
    } finally { await h.close(); }
  }
});

test("image retrieval requires identity and farm scope; unknown, invalid and missing files are refused", async () => {
  const h = await harness();
  try {
    const created = await (await h.as("owner", "/capture-now?device_id=cam-a", { method: "POST" })).json();
    const name = created.image_url.split("/").pop();
    const ok = await h.as("owner", `/image/${name}`);
    assert.equal(ok.status, 200);
    assert.equal(ok.headers.get("content-type"), "image/jpeg");
    assert.equal(ok.headers.get("cache-control"), "private, max-age=86400");
    assert.deepEqual(Buffer.from(await ok.arrayBuffer()), JPEG);
    assert.equal((await h.as(null, `/image/${name}`)).status, 401, "no identity");
    assert.equal((await h.as("other-farm-user", `/image/${name}`)).status, 403, "wrong farm");
    assert.equal((await h.as("owner", "/image/cam_1.jpg")).status, 404, "no capture record");
    assert.equal((await h.as("owner", "/image/..%2F..%2Fetc%2Fpasswd")).status, 400, "path traversal");
    assert.equal((await h.as("owner", "/image/cam_1.png")).status, 400, "invalid name");
    fs.rmSync(path.join(h.dir, "cameras", name));
    assert.equal((await h.as("owner", `/image/${name}`)).status, 404, "record exists but file missing");
  } finally { await h.close(); }
});
