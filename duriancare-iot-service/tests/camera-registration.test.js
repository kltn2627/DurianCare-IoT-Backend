"use strict";

/**
 * Camera Self-Registration + Heartbeat — integration tests (simulated).
 *
 * These tests exercise the full registration/heartbeat logic through the
 * deviceService module against a real PostgreSQL schema.  No real ESP32 is
 * needed — all HTTP calls are simulated by calling the service functions
 * directly.
 *
 * Requirements:
 *   POSTGRES_HOST / POSTGRES_PORT / POSTGRES_DB / POSTGRES_USER /
 *   POSTGRES_PASSWORD / POSTGRES_SCHEMA must be set in the environment
 *   (or use the defaults that match docker-compose.yml).
 *   V9 migration must have been applied before running these tests.
 *
 * Run:
 *   node tests/camera-registration.test.js
 */

// dotenv is optional — env vars can also be set by the caller before running.
try { require("dotenv").config({ path: "../infrastructure/.env" }); } catch (_) {}

const assert = require("assert/strict");
const { Pool } = require("pg");
const {
  upsertCamera,
  heartbeat,
  getCamera,
  listCameras,
  assignTree,
  markOfflineStaleCameras,
  resolveCameraUrl,
} = require("../src/camera/deviceService");

const schema = process.env.POSTGRES_SCHEMA || "duriancare_iot";

const pool = new Pool({
  host:     process.env.POSTGRES_HOST     || "localhost",
  port:     Number(process.env.POSTGRES_PORT || 5432),
  database: process.env.POSTGRES_DB       || "duriancare",
  user:     process.env.POSTGRES_USER     || "duriancare",
  password: process.env.POSTGRES_PASSWORD || "",
});

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
    `DELETE FROM "${schema}".camera_devices WHERE device_id LIKE $1`,
    [`${PREFIX}%`],
  );
}

// ── Test suite ─────────────────────────────────────────────────────────────

async function main() {
  console.log("\nCamera Registration + Heartbeat Tests\n");

  try {
    await pool.query("SELECT 1"); // connection smoke-test
  } catch (err) {
    console.error("Cannot connect to PostgreSQL:", err.message);
    process.exit(1);
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

  // ── Cleanup ─────────────────────────────────────────────────────────────────
  await cleanup();
  await pool.end();

  console.log(`\nResults: ${passed} passed, ${failed} failed\n`);
  process.exit(failed > 0 ? 1 : 0);
}

main().catch((err) => {
  console.error("Unexpected error:", err);
  process.exit(1);
});
