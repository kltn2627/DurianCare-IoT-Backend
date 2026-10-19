"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { recordDeviceSeen } = require("../src/telemetryRegistry");
const { connectivityForDevice } = require("../src/alert-engine");

const healthConfig = { expectedTelemetryIntervalSeconds: 60, staleAfterIntervals: 3, offlineAfterIntervals: 10 };

function registryPool(rows) {
  const calls = [];
  return {
    calls,
    async query(sql, args) {
      calls.push({ sql, args });
      const hit = rows.filter(r => r.device_uid.toLowerCase() === args[0].toLowerCase() && r.status !== "DELETED");
      for (const r of hit) r.last_seen_at = args[1];
      return { rowCount: hit.length, rows: hit.map(r => ({ ...r })) };
    }
  };
}

test("HTTP sensor telemetry stamps last_seen_at so connectivity is no longer UNKNOWN", async () => {
  // Reproduces the esp32-01 state: ACTIVE registry row, last_seen_at NULL, telemetry arriving via HTTP.
  const row = { id: "d1", device_uid: "esp32-01", status: "ACTIVE", farm_id: "farm-khoa-luan-2026", last_seen_at: null };
  assert.equal(connectivityForDevice(row, healthConfig).status, "UNKNOWN");
  const pool = registryPool([row]);
  const receivedAt = new Date().toISOString();
  const device = await recordDeviceSeen({ pool, devicesTable: '"iot".iot_devices', deviceId: "ESP32-01", receivedAt });
  assert.equal(device.last_seen_at, receivedAt);
  assert.equal(connectivityForDevice(device, healthConfig).status, "ONLINE");
  assert.match(pool.calls[0].sql, /LOWER\(device_uid\) = LOWER\(\$1\)/);
  assert.match(pool.calls[0].sql, /status <> 'DELETED'/);
});

test("unregistered or deleted devices are not stamped", async () => {
  const pool = registryPool([{ id: "d2", device_uid: "gone", status: "DELETED", last_seen_at: null }]);
  assert.equal(await recordDeviceSeen({ pool, devicesTable: "t", deviceId: "gone", receivedAt: new Date().toISOString() }), null);
  assert.equal(await recordDeviceSeen({ pool, devicesTable: "t", deviceId: "unknown-dev", receivedAt: new Date().toISOString() }), null);
});

test("stale and offline thresholds still apply after stamping", () => {
  const now = new Date("2026-10-18T13:30:00Z");
  const at = s => ({ status: "ACTIVE", last_seen_at: new Date(now.getTime() - s * 1000).toISOString() });
  assert.equal(connectivityForDevice(at(30), healthConfig, now).status, "ONLINE");
  assert.equal(connectivityForDevice(at(200), healthConfig, now).status, "STALE");
  assert.equal(connectivityForDevice(at(700), healthConfig, now).status, "OFFLINE");
});
