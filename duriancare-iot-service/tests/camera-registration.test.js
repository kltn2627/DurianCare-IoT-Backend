"use strict";

/**
 * Camera Self-Registration + Heartbeat — integration tests (simulated).
 *
 * These tests exercise the full registration/heartbeat logic through the
 * deviceService module against a real PostgreSQL schema.  No real ESP32 is
 * needed — all HTTP calls are simulated by calling the service functions
 * directly.
 *
 * Requires explicit DURIANCARE_CAMERA_FIXTURE_PG_URI, supplied securely by the operator.
 * Uses the actual V9 schema in a UUID namespace within a rollback-only transaction.
 * Never defaults to or performs a global watchdog update on the business schema.
 *
 * Run:
 *   node tests/camera-registration.test.js
 */

const assert = require("assert/strict");
const { Pool } = require("pg");
const { randomUUID } = require("node:crypto");
const { test } = require("node:test");
const { createCameraFixtureSchema } = require("../test-support/camera-schema.cjs");
const {
  upsertCamera,
  heartbeat,
  getCamera,
  listCameras,
  assignTree,
  markOfflineStaleCameras,
  resolveCameraUrl,
  updateCameraUrl,
} = require("../src/camera/deviceService");

const schema = "camera_fixture_" + randomUUID().replaceAll("-", "");
let client;
const pool = { query: (...args) => client.query(...args) };

// Unique prefix so tests don't collide with production device IDs
const PREFIX = `test-cam-${Date.now()}`;
const DEV_A = `${PREFIX}-A`;
const DEV_B = `${PREFIX}-B`;

let passed = 0;
let failed = 0;

async function run(label, fn) {
  try {
    await fn();
    console.log(`  ✓  ${label}`);
    passed++;
  } catch (err) {
    console.error(`  ✗  ${label}`);
    console.error(`     ${err.message}`);
    failed++;
  }
}

async function cleanup() {
  await pool.query(
    `DELETE FROM "${schema}".camera_devices WHERE device_id = ANY($1::text[])`,
    [[DEV_A, DEV_B]],
  );
}

// ── Test suite ─────────────────────────────────────────────────────────────

async function main() {
  console.log("\nCamera Registration + Heartbeat Tests\n");

  try {
    await pool.query("SELECT 1"); // connection smoke-test
  } catch (err) {
    console.error("Cannot connect to PostgreSQL:", err.message);
    throw new Error("Fixture database unavailable");
  }

  // Clean up any leftovers from a previous interrupted run
  await cleanup();

  // TC-01: Register a new camera
  await run("TC-01 Register a new camera", async () => {
    const cam = await upsertCamera(pool, schema, {
      deviceId:  DEV_A,
      ipAddress: "192.168.1.10",
      port:      80,
      protocol:  "http",
    });
    assert.equal(cam.device_id, DEV_A);
    assert.equal(cam.ip_address, "192.168.1.10");
    assert.equal(cam.online, true);
    assert.ok(cam.last_seen);
  });

  // TC-02: Re-register same device_id with a new IP — IP must be updated
  await run("TC-02 Re-register same device_id updates IP", async () => {
    const cam = await upsertCamera(pool, schema, {
      deviceId:  DEV_A,
      ipAddress: "192.168.1.99",
      port:      81,
      protocol:  "http",
    });
    assert.equal(cam.ip_address, "192.168.1.99");
    assert.equal(cam.port, 81);
    assert.equal(cam.device_id, DEV_A);
  });

  // TC-03: Heartbeat updates last_seen and keeps online=true
  await run("TC-03 Heartbeat updates last_seen", async () => {
    const before = await getCamera(pool, schema, DEV_A);
    // Small sleep so last_seen timestamp advances
    await new Promise((r) => setTimeout(r, 50));
    const cam = await heartbeat(pool, schema, {
      deviceId:  DEV_A,
      ipAddress: "192.168.1.99",
      port:      81,
    });
    assert.ok(cam, "heartbeat returned a record");
    assert.equal(cam.online, true);
    assert.ok(
      new Date(cam.last_seen) > new Date(before.last_seen),
      "last_seen must be newer after heartbeat",
    );
  });

  // TC-04: Offline detection marks stale cameras offline
  await run("TC-04 markOfflineStaleCameras marks stale cameras offline", async () => {
    // Register camera B; then simulate it going silent by back-dating last_seen
    await upsertCamera(pool, schema, {
      deviceId:  DEV_B,
      ipAddress: "192.168.1.20",
      port:      80,
    });
    // Back-date last_seen by 10 minutes so it falls outside the 1-minute timeout
    await pool.query(
      `UPDATE "${schema}".camera_devices
       SET last_seen = NOW() - INTERVAL '10 minutes'
       WHERE device_id = $1`,
      [DEV_B],
    );
    const count = await markOfflineStaleCameras(pool, schema, 1);
    assert.ok(count >= 1, `Expected at least 1 camera marked offline, got ${count}`);
    const cam = await getCamera(pool, schema, DEV_B);
    assert.equal(cam.online, false);
  });

  // TC-05: Tree assignment — camera stores treeId without changing IP
  await run("TC-05 assignTree stores tree_id", async () => {
    const cam = await assignTree(pool, schema, DEV_A, {
      treeId: "tree-001",
      zoneId: "zone-01",
      farmId: "farm-01",
    });
    assert.equal(cam.tree_id,  "tree-001");
    assert.equal(cam.zone_id,  "zone-01");
    assert.equal(cam.farm_id,  "farm-01");
    // IP should not have changed
    assert.equal(cam.ip_address, "192.168.1.99");
  });

  // TC-06: IP change during diagnosis — resolveCameraUrl returns new IP
  await run("TC-06 IP change reflected in resolveCameraUrl", async () => {
    // Bring DEV_A back online with a different IP
    await upsertCamera(pool, schema, {
      deviceId:  DEV_A,
      ipAddress: "10.0.0.55",
      port:      80,
    });
    const { url, online, found } = await resolveCameraUrl(pool, schema, DEV_A, "http://fallback");
    assert.ok(found);
    assert.ok(online);
    assert.ok(url.includes("10.0.0.55"), `Expected url to include new IP, got: ${url}`);
  });

  // TC-07: Offline camera returns online=false from resolveCameraUrl
  await run("TC-07 Offline camera reflected in resolveCameraUrl", async () => {
    const { url, online, found } = await resolveCameraUrl(pool, schema, DEV_B, "http://fallback");
    assert.ok(found);
    assert.equal(online, false);
    assert.ok(url, "url should still be returned for offline cameras");
  });

  // TC-08: Multi-camera isolation — updates to DEV_A don't affect DEV_B
  await run("TC-08 Multi-camera isolation", async () => {
    // Give DEV_A a distinct IP
    await upsertCamera(pool, schema, {
      deviceId:  DEV_A,
      ipAddress: "172.16.0.1",
      port:      80,
    });
    const camA = await getCamera(pool, schema, DEV_A);
    const camB = await getCamera(pool, schema, DEV_B);
    assert.notEqual(camA.ip_address, camB.ip_address, "Cameras must have independent IPs");
    assert.notEqual(camA.online, camB.online, "Camera A should be online, B offline");
  });

  await run("TC-09 Guarded URL update: existing owner only, retry and stale origin CAS", async () => {
    const current = await getCamera(pool, schema, DEV_A);
    const update = { ipAddress: "192.168.9.3", port: 81, protocol: "https", expectedFarmId: "farm-01",
      expectedOrigin: { ipAddress: current.ip_address, port: current.port, protocol: current.protocol } };
    assert.equal(await updateCameraUrl(pool, schema, "unknown-fixture", update), null);
    assert.equal(await updateCameraUrl(pool, schema, DEV_A, { ...update, expectedFarmId: "foreign-farm" }), null);
    const saved = await updateCameraUrl(pool, schema, DEV_A, update);
    assert.equal(saved.ip_address, "192.168.9.3");
    assert.equal((await resolveCameraUrl(pool, schema, DEV_A)).url, "https://192.168.9.3:81");
    assert.equal(await updateCameraUrl(pool, schema, DEV_A, update), null);
    assert.equal(await updateCameraUrl(pool, schema, DEV_A, { ...update, ipAddress: "192.168.9.4" }), null);
    await assert.rejects(updateCameraUrl(pool, schema, DEV_A, { ...update, protocol: "file" }));
  });

  // ── Cleanup ─────────────────────────────────────────────────────────────────
  await cleanup();

  console.log(`\nResults: ${passed} passed, ${failed} failed\n`);
  assert.equal(failed, 0, "camera fixture cases failed");
}

test("camera registration/heartbeat: nine cases in a rollback-only isolated schema", {
  skip: !process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI && "DURIANCARE_CAMERA_FIXTURE_PG_URI required",
}, async () => {
  const connectionPool = new Pool({ connectionString: process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI });
  try {
    client = await connectionPool.connect();
    await client.query("BEGIN");
    await createCameraFixtureSchema(client, schema);
    await main();
  } finally {
    if (client) {
      await client.query("ROLLBACK");
      const result = await client.query("SELECT 1 FROM information_schema.schemata WHERE schema_name=$1", [schema]);
      assert.equal(result.rows.length, 0);
      client.release();
    }
    await connectionPool.end();
  }
});
