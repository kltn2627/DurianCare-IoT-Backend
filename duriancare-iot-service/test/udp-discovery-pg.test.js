"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { once } = require("node:events");
const crypto = require("node:crypto");
const dgram = require("node:dgram");
const http = require("node:http");
const express = require("express");
const { Pool } = require("pg");
const { createUdpDiscoveryService } = require("../src/discovery/udpDiscoveryService");
const { createCameraRouter } = require("../src/camera/cameraRoutes");
const { upsertCamera, getCamera, resolveCameraUrl } = require("../src/camera/deviceService");
const { createCameraFixtureSchema } = require("../test-support/camera-schema.cjs");

test("real UDP -> isolated PostgreSQL -> subsequent snapshot uses new origin without old cached frame", {
  skip: !process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI && "explicit isolated-fixture connection required",
}, async () => {
  const schema = "udp_fixture_" + crypto.randomUUID().replaceAll("-", "");
  const connections = new Pool({ connectionString: process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI });
  const client = await connections.connect();
  const pool = { query: (...args) => client.query(...args) };
  const frames = [Buffer.from([255,216,255,11,255,217]), Buffer.from([255,216,255,22,255,217])];
  const upstreams = frames.map(frame => http.createServer((req, res) => { res.writeHead(200, { "content-type": "image/jpeg" }); res.end(frame); }));
  const sender = dgram.createSocket("udp4");
  let routerServer, discovery;
  try {
    await client.query("BEGIN");
    await createCameraFixtureSchema(client, schema);
    for (const server of upstreams) await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    await upsertCamera(pool, schema, { deviceId: "fixture-cam", farmId: "fixture-farm", ipAddress: "127.0.0.1", port: upstreams[0].address().port });
    const app = express();
    app.use((req, res, next) => { req.headers["x-auth-user-id"] = "fixture-owner"; req.headers["x-auth-role"] = "FARMER"; next(); });
    app.use(createCameraRouter({ pool, schema, farmAccessClient: {
      canReadIot: async (actor, farmId) => actor.userId === "fixture-owner" && farmId === "fixture-farm"
    } }));
    routerServer = app.listen(0, "127.0.0.1"); await once(routerServer, "listening");
    const route = `http://127.0.0.1:${routerServer.address().port}/snapshot?device_id=fixture-cam`;
    assert.deepEqual(Buffer.from(await (await fetch(route)).arrayBuffer()), frames[0]);
    const key = crypto.randomBytes(32).toString("hex");
    let resolveUpdate; const updated = new Promise(resolve => { resolveUpdate = resolve; });
    const sockets = [];
    discovery = createUdpDiscoveryService({ pool, schema, isContainer: true, cameraPort: 0, bindAddress: "127.0.0.1",
      validateCameraAddress: ip => ip === "127.0.0.1", env: { UDP_DISCOVERY_CAMERA_BINDINGS: JSON.stringify({ "fixture-cam": { farmId: "fixture-farm", key } }) },
      createSocket: type => { const socket = dgram.createSocket(type); sockets.push(socket); return socket; },
      logger: { warn() {}, info() { resolveUpdate(); } } });
    await discovery.startUdpDiscovery(); if (!discovery.getState().listenerReady) await once(sockets[0], "listening");
    const timestamp = Date.now() + 1, announcement = `DURIANCARE_CAM:fixture-cam:http://127.0.0.1:${upstreams[1].address().port}`;
    const packet = Buffer.from(JSON.stringify({ timestamp, announcement,
      signature: crypto.createHmac("sha256", key).update(`${timestamp}\n${announcement}`).digest("hex") }));
    await new Promise((resolve, reject) => sender.send(packet, sockets[0].address().port, "127.0.0.1", error => error ? reject(error) : resolve()));
    let deadline;
    try { await Promise.race([updated, new Promise((_, reject) => { deadline = setTimeout(() => reject(new Error("UDP DB integration timeout")), 3000); })]); }
    finally { clearTimeout(deadline); }
    assert.equal((await getCamera(pool, schema, "fixture-cam")).farm_id, "fixture-farm");
    assert.equal((await resolveCameraUrl(pool, schema, "fixture-cam")).url, `http://127.0.0.1:${upstreams[1].address().port}`);
    assert.deepEqual(Buffer.from(await (await fetch(route)).arrayBuffer()), frames[1]);
  } finally {
    await discovery?.stopUdpDiscovery();
    try { sender.close(); } catch { /* It may not have been bound before a setup failure. */ }
    if (routerServer) await new Promise(resolve => routerServer.close(resolve));
    for (const server of upstreams) if (server.listening) await new Promise(resolve => server.close(resolve));
    try {
      await client.query("ROLLBACK");
      assert.equal((await client.query("SELECT 1 FROM information_schema.schemata WHERE schema_name=$1", [schema])).rows.length, 0);
    } finally { client.release(); await connections.end(); }
  }
});
