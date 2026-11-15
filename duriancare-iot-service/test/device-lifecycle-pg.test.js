const { test } = require("node:test");
const assert = require("node:assert/strict");
const express = require("express");
const { Pool } = require("pg");
const { registerReadApi, ensureReadSchema } = require("../src/read-api");
const { ensureAlertSchema } = require("../src/alert-engine");
const { ensureTelemetryProvenanceSchema } = require("../src/telemetryProvenance");
const { ensureSensorQualitySchema } = require("../src/sensorQuality");
const { ensureDeviceLifecycleSchema, pendingConfigFor } = require("../src/devices/deviceLifecycle");

// Real PostgreSQL, zero lasting effect: a scratch schema inside ONE outer transaction that is
// always rolled back. Route transactions (BEGIN/COMMIT/ROLLBACK) become savepoints.
const uri = process.env.IOT_FIXTURE_PG_URI || process.env.IOT_BACKFILL_FIXTURE_PG_URI;
const SCHEMA = "iot_lifecycle_fixture";

function poolOver(client) {
  const wrap = {
    query: (sql, params) => {
      if (sql === "BEGIN") return client.query("SAVEPOINT route_tx");
      if (sql === "COMMIT") return client.query("RELEASE SAVEPOINT route_tx");
      if (sql === "ROLLBACK") return client.query("ROLLBACK TO SAVEPOINT route_tx");
      return client.query(sql, params);
    },
    release() {},
  };
  // Outside route transactions every statement behaves like autocommit: a failing one (e.g. a
  // unique violation mapped to 409) must not abort the rest of the scenario.
  const autocommit = async (sql, params) => {
    await client.query("SAVEPOINT stmt");
    try {
      const result = await client.query(sql, params);
      await client.query("RELEASE SAVEPOINT stmt");
      return result;
    } catch (error) {
      await client.query("ROLLBACK TO SAVEPOINT stmt");
      throw error;
    }
  };
  return { query: autocommit, connect: async () => wrap };
}

const farmAccessClient = {
  // owner-1 manages farm-1 (zones zone-a, zone-b); everyone else has no farm access.
  async canReadIot(actor, farmId, areaId) { return this.canManageIot(actor, farmId, areaId); },
  async canManageIot(actor, farmId, areaId) {
    return actor.userId === "owner-1" && farmId === "farm-1" && (!areaId || ["zone-a", "zone-b"].includes(areaId));
  },
  async alertRecipients() { return ["owner-1"]; },
};

async function call(server, path, { method = "GET", body, user = "owner-1", role = "FARMER" } = {}) {
  const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, {
    method, headers: { "Content-Type": "application/json", "X-Auth-User-Id": user, "X-Auth-Role": role },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: response.status, body: await response.json().catch(() => null) };
}

test("provision → claim → assign → config → alert rules on real PostgreSQL (rolled back)", {
  skip: !uri && "set IOT_FIXTURE_PG_URI (or IOT_BACKFILL_FIXTURE_PG_URI) to run; the outer transaction is always rolled back",
}, async () => {
  const pg = new Pool({ connectionString: uri, max: 1 });
  const client = await pg.connect();
  let server;
  try {
    await client.query("BEGIN");
    await client.query(`CREATE SCHEMA ${SCHEMA}`);
    const pool = poolOver(client);
    const devicesTable = `"${SCHEMA}".iot_devices`;
    await ensureReadSchema(pool, devicesTable);
    await ensureAlertSchema(pool, `"${SCHEMA}".iot_alerts`);
    await client.query(`CREATE TABLE ${SCHEMA}.telemetry (LIKE duriancare_iot.telemetry INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)`);
    await ensureTelemetryProvenanceSchema(pool, SCHEMA);
    await ensureSensorQualitySchema(pool, SCHEMA);
    await ensureDeviceLifecycleSchema(pool, SCHEMA);
    await ensureDeviceLifecycleSchema(pool, SCHEMA); // idempotent

    const app = express();
    app.use(express.json());
    registerReadApi(app, {
      alertsTable: `"${SCHEMA}".iot_alerts`, devicesTable, farmAccessClient, pool, schema: SCHEMA,
      telemetryTable: `"${SCHEMA}".telemetry`, claimTtlHours: 72,
      healthConfig: { expectedTelemetryIntervalSeconds: 10, staleAfterIntervals: 3, offlineAfterIntervals: 10 },
      thresholds: { temperatureHigh: 38, temperatureLow: 15, humidityHigh: 95, humidityLow: 45, lightHigh: 4095, lightLow: 0 },
    });
    server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });

    // UC79 — provisioning is an administrator action; the code is returned once.
    assert.equal((await call(server, "/api/iot/devices/provision", { method: "POST", body: { deviceUid: "esp32-77" } })).status, 403);
    const prov = await call(server, "/api/iot/devices/provision", { method: "POST", role: "ADMIN", user: "admin-1",
      body: { deviceUid: "esp32-77", deviceType: "SENSOR", name: "Trạm khu A" } });
    assert.equal(prov.status, 201);
    assert.match(prov.body.claimCode, /^[0-9A-Z]{5}-[0-9A-Z]{5}$/);
    assert.equal(prov.body.device.provisioningStatus, "PROVISIONED");
    assert.equal(prov.body.device.farmId, null);
    const stored = (await client.query(`SELECT code_hash FROM ${SCHEMA}.iot_device_claims`)).rows[0];
    assert.ok(!JSON.stringify(stored).includes(prov.body.claimCode.replace("-", "")), "code is stored hashed only");
    assert.equal((await call(server, "/api/iot/devices/provision", { method: "POST", role: "ADMIN", user: "admin-1",
      body: { deviceUid: "ESP32-77" } })).status, 409, "duplicate UID (case-insensitive)");
    assert.equal((await call(server, "/api/iot/devices/provision", { method: "POST", role: "ADMIN", user: "admin-1",
      body: { deviceUid: "x" } })).status, 400, "missing/invalid UID");

    // Unclaimed devices are invisible to farmers.
    assert.equal((await call(server, "/api/iot/devices")).body.devices.length, 0);
    assert.equal((await call(server, "/api/iot/devices/esp32-77")).status, 404);

    // UC82 — claim. Target scope is checked before the code, and a 403 does not burn an attempt.
    const claim = (over) => call(server, "/api/iot/devices/claim", { method: "POST",
      body: { deviceUid: "esp32-77", claimCode: prov.body.claimCode, farmId: "farm-1", cultivationAreaId: "zone-a", ...over.body },
      user: over.user || "owner-1" });
    assert.equal((await claim({ body: { farmId: "farm-2" } })).status, 403);
    assert.equal((await claim({ user: "stranger" })).status, 403);
    assert.equal((await claim({ body: { cultivationAreaId: "zone-x" } })).status, 403, "zone outside the farm");
    const wrong = await claim({ body: { claimCode: "AAAAA-AAAAA" } });
    assert.equal(wrong.status, 400);
    assert.equal((await client.query(`SELECT failed_attempts FROM ${SCHEMA}.iot_device_claims WHERE status='PENDING'`)).rows[0].failed_attempts, 1);
    assert.equal((await claim({ body: { deviceUid: "esp32-unknown" } })).status, 400, "unknown UID looks like a wrong code");
    const ok = await claim({ body: {} });
    assert.equal(ok.status, 200);
    assert.equal(ok.body.device.provisioningStatus, "CLAIMED");
    assert.equal(ok.body.device.farmId, "farm-1");
    assert.equal(ok.body.device.cultivationAreaId, "zone-a");
    assert.equal((await claim({ body: {} })).status, 409, "code already used");
    assert.equal((await claim({ body: { claimCode: "BBBBB-BBBBB" } })).status, 400, "wrong code on a claimed device reveals nothing");
    const listed = (await call(server, "/api/iot/devices")).body.devices;
    assert.deepEqual(listed.map((d) => [d.deviceUid, d.deviceType, d.provisioningStatus]), [["esp32-77", "SENSOR", "CLAIMED"]]);
    assert.equal((await call(server, "/api/iot/devices", { user: "stranger" })).body.devices.length, 0);
    assert.equal((await call(server, "/api/iot/devices/esp32-77/claim-code", { method: "POST", role: "ADMIN", user: "admin-1" })).status, 409);

    // Lockout and expiry on a second device; a regenerated code revokes the old one.
    const second = await call(server, "/api/iot/devices/provision", { method: "POST", role: "ADMIN", user: "admin-1", body: { deviceUid: "esp32-78" } });
    for (let i = 0; i < 5; i++) {
      assert.equal((await claim({ body: { deviceUid: "esp32-78", claimCode: "CCCCC-CCCCC" } })).status, 400);
    }
    assert.equal((await claim({ body: { deviceUid: "esp32-78", claimCode: second.body.claimCode } })).status, 429);
    const fresh = await call(server, "/api/iot/devices/esp32-78/claim-code", { method: "POST", role: "ADMIN", user: "admin-1" });
    assert.equal(fresh.status, 201);
    assert.equal((await claim({ body: { deviceUid: "esp32-78", claimCode: second.body.claimCode } })).status, 400, "old code revoked");
    await client.query(`UPDATE ${SCHEMA}.iot_device_claims SET expires_at = now() - interval '1 minute' WHERE status = 'PENDING'`);
    assert.equal((await claim({ body: { deviceUid: "esp32-78", claimCode: fresh.body.claimCode } })).status, 410);

    // UC81 — configuration and its honest delivery states.
    assert.equal((await call(server, "/api/iot/devices/esp32-77/config")).body.config.deliveryStatus, "NOT_CONFIGURED");
    assert.equal((await call(server, "/api/iot/devices/esp32-77/config", { method: "PUT", body: { publishIntervalSeconds: 2, alertsEnabled: true } })).status, 400);
    assert.equal((await call(server, "/api/iot/devices/esp32-77/config", { method: "PUT", user: "stranger", body: { publishIntervalSeconds: 30, alertsEnabled: true } })).status, 403);
    const saved = await call(server, "/api/iot/devices/esp32-77/config", { method: "PUT", body: { publishIntervalSeconds: 30, alertsEnabled: true } });
    assert.deepEqual([saved.body.config.configVersion, saved.body.config.deliveryStatus], [1, "PENDING"]);
    const device = (await client.query(`SELECT * FROM ${devicesTable} WHERE device_uid = 'esp32-77'`)).rows[0];
    assert.deepEqual(await pendingConfigFor(pool, SCHEMA, device, null), { version: 1, publish_interval_seconds: 30 });
    assert.equal((await call(server, "/api/iot/devices/esp32-77/config")).body.config.deliveryStatus, "DELIVERED");
    assert.equal(await pendingConfigFor(pool, SCHEMA, device, 1), null);
    const applied = (await call(server, "/api/iot/devices/esp32-77/config")).body.config;
    assert.deepEqual([applied.deliveryStatus, applied.appliedVersion], ["APPLIED", 1]);
    const alertsOnly = await call(server, "/api/iot/devices/esp32-77/config", { method: "PUT", body: { publishIntervalSeconds: 30, alertsEnabled: false } });
    assert.deepEqual([alertsOnly.body.config.configVersion, alertsOnly.body.config.deliveryStatus], [1, "APPLIED"], "server-side switch needs no delivery");
    const changed = await call(server, "/api/iot/devices/esp32-77/config", { method: "PUT", body: { publishIntervalSeconds: 60, alertsEnabled: false } });
    assert.deepEqual([changed.body.config.configVersion, changed.body.config.deliveryStatus], [2, "PENDING"]);

    // UC85 — alert rules per farm / zone / device.
    const created = await call(server, "/api/iot/alert-rules", { method: "POST",
      body: { farmId: "farm-1", cultivationAreaId: "zone-a", metric: "SOIL_MOISTURE", minValue: 55, maxValue: 90, hysteresis: 3, severity: "CRITICAL" } });
    assert.equal(created.status, 201);
    assert.equal(created.body.rule.scope, "ZONE");
    assert.equal((await call(server, "/api/iot/alert-rules", { method: "POST",
      body: { farmId: "farm-1", cultivationAreaId: "zone-a", metric: "SOIL_MOISTURE", minValue: 50 } })).status, 409);
    assert.equal((await call(server, "/api/iot/alert-rules", { method: "POST", user: "stranger",
      body: { farmId: "farm-1", metric: "TEMPERATURE", maxValue: 36 } })).status, 403);
    assert.equal((await call(server, "/api/iot/alert-rules", { method: "POST",
      body: { farmId: "farm-1", cultivationAreaId: "zone-b", deviceId: "esp32-77", metric: "TEMPERATURE", maxValue: 36 } })).status, 400,
      "device outside the given zone");
    const deviceRule = await call(server, "/api/iot/alert-rules", { method: "POST",
      body: { farmId: "farm-1", deviceId: "esp32-77", metric: "TEMPERATURE", maxValue: 36 } });
    assert.equal(deviceRule.body.rule.scope, "DEVICE");
    const listedRules = await call(server, "/api/iot/alert-rules?farmId=farm-1&cultivationAreaId=zone-a");
    assert.equal(listedRules.body.rules.length, 2);
    assert.ok(listedRules.body.defaults.some((d) => d.metric === "TEMPERATURE" && d.maxValue === 38));
    assert.equal((await call(server, "/api/iot/alert-rules?farmId=farm-1", { user: "stranger" })).status, 403);
    const disabled = await call(server, `/api/iot/alert-rules/${created.body.rule.id}`, { method: "PATCH", body: { enabled: false } });
    assert.equal(disabled.body.rule.enabled, false);
    assert.equal(disabled.body.rule.minValue, 55, "untouched fields kept");
    assert.equal((await call(server, `/api/iot/alert-rules/${created.body.rule.id}`, { method: "DELETE", user: "stranger" })).status, 403);
    assert.equal((await call(server, `/api/iot/alert-rules/${created.body.rule.id}`, { method: "DELETE" })).status, 200);

    // Device detail exposes type, firmware, latest per metric and open alerts.
    await client.query(`INSERT INTO ${SCHEMA}.telemetry (device_id, temperature, humidity, soil_moisture, "timestamp", received_at)
      VALUES ('esp32-77', 31.5, NULL, 61, now() - interval '20 seconds', now()), ('esp32-77', NULL, 80, NULL, now() - interval '10 seconds', now())`);
    const detail = await call(server, "/api/iot/devices/esp32-77");
    assert.equal(detail.status, 200);
    assert.equal(detail.body.device.latestByMetric.temperature.value, 31.5);
    assert.equal(detail.body.device.latestByMetric.humidity.value, 80);
    assert.equal(detail.body.device.latestByMetric.soilMoisture.value, 61);
    assert.equal(detail.body.device.latestByMetric.light, null);
    assert.deepEqual(detail.body.openAlerts, []);

    // UC88 — a removed device can be provisioned again for a new owner.
    assert.equal((await call(server, "/api/iot/devices/esp32-77", { method: "DELETE" })).status, 200);
    const again = await call(server, "/api/iot/devices/provision", { method: "POST", role: "ADMIN", user: "admin-1", body: { deviceUid: "esp32-77" } });
    assert.equal(again.status, 201);
    assert.equal(again.body.device.provisioningStatus, "PROVISIONED");
    assert.equal(again.body.device.farmId, null);
  } finally {
    if (server) await new Promise((resolve) => server.close(resolve));
    await client.query("ROLLBACK").catch(() => {});
    client.release();
    await pg.end();
  }
});

test("test devices are flagged at provisioning and their telemetry leaves cross-device reads (rolled back)", {
  skip: !uri && "set IOT_FIXTURE_PG_URI (or IOT_BACKFILL_FIXTURE_PG_URI) to run; the outer transaction is always rolled back",
}, async () => {
  const { excludeTestAndDeletedDevices } = require("../src/devices/deviceLifecycle");
  const pg = new Pool({ connectionString: uri, max: 1 });
  const client = await pg.connect();
  let server;
  try {
    await client.query("BEGIN");
    await client.query(`CREATE SCHEMA ${SCHEMA}`);
    const pool = poolOver(client);
    const devicesTable = `"${SCHEMA}".iot_devices`;
    const telemetryTable = `"${SCHEMA}".telemetry`;
    await ensureReadSchema(pool, devicesTable);
    await ensureAlertSchema(pool, `"${SCHEMA}".iot_alerts`);
    await client.query(`CREATE TABLE ${SCHEMA}.telemetry (LIKE duriancare_iot.telemetry INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)`);
    await ensureTelemetryProvenanceSchema(pool, SCHEMA);
    await ensureSensorQualitySchema(pool, SCHEMA);
    // A device registered before the flag existed keeps is_test = false (compatible default).
    await client.query(`INSERT INTO ${devicesTable} (device_uid, name, farm_id, status) VALUES ('legacy-1', 'Legacy', 'farm-1', 'ACTIVE')`);
    await ensureDeviceLifecycleSchema(pool, SCHEMA);
    assert.equal((await client.query(`SELECT is_test FROM ${devicesTable} WHERE device_uid = 'legacy-1'`)).rows[0].is_test, false);

    const app = express();
    app.use(express.json());
    registerReadApi(app, {
      alertsTable: `"${SCHEMA}".iot_alerts`, devicesTable, farmAccessClient, pool, schema: SCHEMA, telemetryTable, claimTtlHours: 72,
      thresholds: { temperatureHigh: 38 },
    });
    server = await new Promise((resolve) => { const s = app.listen(0, "127.0.0.1", () => resolve(s)); });
    const admin = { method: "POST", role: "ADMIN", user: "admin-1" };
    assert.equal((await call(server, "/api/iot/devices/provision", { ...admin, body: { deviceUid: "qa-sim-1", isTest: "yes" } })).status, 400);
    const sim = await call(server, "/api/iot/devices/provision", { ...admin, body: { deviceUid: "qa-sim-1", isTest: true } });
    assert.equal(sim.status, 201);
    assert.equal(sim.body.device.isTest, true);
    const real = await call(server, "/api/iot/devices/provision", { ...admin, body: { deviceUid: "field-2" } });
    assert.equal(real.body.device.isTest, false);
    const inventory = await call(server, "/api/iot/admin/devices", { role: "ADMIN", user: "admin-1" });
    const flags = Object.fromEntries(inventory.body.devices.map((d) => [d.deviceUid, d.isTest]));
    assert.deepEqual(flags, { "qa-sim-1": true, "field-2": false, "legacy-1": false });

    await client.query(`INSERT INTO ${devicesTable} (device_uid, name, farm_id, status) VALUES ('removed-3', 'Removed', 'farm-1', 'DELETED')`);
    await client.query(`INSERT INTO ${telemetryTable} (device_id, temperature, "timestamp", received_at) VALUES
      ('legacy-1', 30, now(), now()), ('QA-SIM-1', 99, now(), now()), ('field-2', 31, now(), now()),
      ('removed-3', 32, now(), now()), ('never-registered', 33, now(), now())`);
    const kept = (await client.query(
      `SELECT device_id FROM ${telemetryTable} WHERE ${excludeTestAndDeletedDevices(devicesTable, telemetryTable)} ORDER BY device_id`)).rows;
    assert.deepEqual(kept.map((r) => r.device_id), ["field-2", "legacy-1", "never-registered"]);
    // Rows themselves are untouched: every reading is still stored with its original source.
    assert.equal((await client.query(`SELECT count(*)::int AS n FROM ${telemetryTable} WHERE source = 'DEVICE'`)).rows[0].n, 5);
  } finally {
    if (server) await new Promise((resolve) => server.close(resolve));
    await client.query("ROLLBACK").catch(() => {});
    client.release();
    await pg.end();
  }
});
