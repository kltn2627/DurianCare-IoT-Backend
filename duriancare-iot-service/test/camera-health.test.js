"use strict";
// Camera self-reported health (firmware 2.2.0): a camera whose frame grab fails is DEGRADED, never ONLINE.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { cameraView, deviceHealth } = require("../src/camera/registryRuntime");

const now = Date.parse("2026-10-31T08:00:00Z");
const row = (overrides) => ({ device_id: "ESP32-CAM-001", protocol: "http", ip_address: "192.168.1.3", port: 80,
  online: true, last_seen: new Date(now - 30_000).toISOString(), camera_ready: true, last_error: null, ...overrides });

test("fresh heartbeat + frame grab OK is ONLINE with preview; failed grab is DEGRADED without preview", () => {
  const ok = cameraView(row({}), now);
  assert.equal(ok.status, "ONLINE");
  assert.equal(ok.previewAvailable, true);
  const degraded = cameraView(row({ camera_ready: false, last_error: "CAMERA_STREAM_FAILED" }), now);
  assert.equal(degraded.status, "DEGRADED");
  assert.equal(degraded.online, false);
  assert.equal(degraded.previewAvailable, false);
  assert.equal(degraded.lastError, "CAMERA_STREAM_FAILED");
});

test("heartbeat timeout is OFFLINE whatever the last self-test said; older firmware (NULL) keeps ONLINE", () => {
  const stale = cameraView(row({ last_seen: new Date(now - 6 * 60_000).toISOString() }), now);
  assert.equal(stale.status, "OFFLINE");
  assert.equal(stale.lastError, "HEARTBEAT_TIMEOUT");
  assert.equal(cameraView(row({ camera_ready: null }), now).status, "ONLINE");
});

test("health fields from the firmware are validated; ready clears the error", () => {
  assert.deepEqual(deviceHealth({}), { cameraReady: null, lastError: null, firmwareVersion: null, macAddress: null });
  assert.deepEqual(deviceHealth({ cameraReady: false, lastError: "CAMERA_INIT_FAILED", firmwareVersion: "2.2.0",
    macAddress: "aa:bb:cc:dd:ee:ff" }),
    { cameraReady: false, lastError: "CAMERA_INIT_FAILED", firmwareVersion: "2.2.0", macAddress: "AA:BB:CC:DD:EE:FF" });
  assert.equal(deviceHealth({ cameraReady: true, lastError: "CAMERA_STREAM_FAILED" }).lastError, null);
  for (const bad of [{ cameraReady: "yes" }, { lastError: "rm -rf" }, { firmwareVersion: "<script>" }, { macAddress: "0.0.0.0" }]) {
    assert.equal(deviceHealth(bad), null, JSON.stringify(bad));
  }
});
