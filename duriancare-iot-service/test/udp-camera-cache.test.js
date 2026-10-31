"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const express = require("express");
const { createCameraRouter } = require("../src/camera/cameraRoutes");

test("snapshot resolves DB origin before cache and rejects stale heartbeat", async () => {
  const frames = [Buffer.from([255,216,255,1,255,217]), Buffer.from([255,216,255,2,255,217])];
  const upstreams = frames.map(frame => http.createServer((request, response) => {
    response.writeHead(200, { "content-type": "image/jpeg" }); response.end(frame);
  }));
  for (const server of upstreams) await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  let port = upstreams[0].address().port;
  let online = true;
  const pool = { query: async () => ({ rows: [{ device_id: "fixture", ip_address: "127.0.0.1", port, protocol: "http", online,
    last_seen: new Date().toISOString(), farm_id: "fixture-farm" }] }) };
  const app = express();
  app.use((req, res, next) => { req.headers["x-auth-user-id"] = "fixture"; req.headers["x-auth-role"] = "FARMER"; next(); });
  app.use(createCameraRouter({ pool, schema: "fixture",
    farmAccessClient: { canReadIot: async () => true } }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const url = `http://127.0.0.1:${server.address().port}/snapshot?device_id=fixture`;
  try {
    assert.deepEqual(Buffer.from(await (await fetch(url)).arrayBuffer()), frames[0]);
    const cached = await fetch(url); assert.equal(cached.headers.get("x-frame-cached"), "1");
    await cached.arrayBuffer();
    port = upstreams[1].address().port;
    const changed = await fetch(url);
    assert.equal(changed.headers.get("x-frame-cached"), null);
    assert.deepEqual(Buffer.from(await changed.arrayBuffer()), frames[1]);
    assert.deepEqual(Buffer.from(await (await fetch(url)).arrayBuffer()), frames[1]);
    online = false;
    assert.equal((await fetch(url)).status, 503);
  } finally {
    await new Promise(resolve => server.close(resolve));
    for (const upstream of upstreams) await new Promise(resolve => upstream.close(resolve));
  }
});
