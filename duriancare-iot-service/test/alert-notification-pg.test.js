const { test } = require("node:test");
const assert = require("node:assert/strict");
const { Pool } = require("pg");
const { ensureReadSchema } = require("../src/read-api");
const { ensureAlertSchema, evaluateTelemetryAlerts, evaluateOfflineDevices, retryNotifications } = require("../src/alert-engine");
const { ensureTelemetryProvenanceSchema } = require("../src/telemetryProvenance");
const { ensureDeviceLifecycleSchema } = require("../src/devices/deviceLifecycle");
const { stableEventId } = require("../src/alertNotifications");

// Real PostgreSQL, zero lasting effect: scratch schema inside ONE outer transaction that is always rolled back.
const uri = process.env.IOT_FIXTURE_PG_URI || process.env.IOT_BACKFILL_FIXTURE_PG_URI;
const SCHEMA = "iot_alert_delivery_fixture";
const POLICY = { maxAttempts: 3, baseDelayMs: 30_000, maxDelayMs: 600_000, leaseMs: 120_000, batchSize: 50, testReceiverId: "" };
const quiet = { info: () => undefined, error: () => undefined };

test("alert delivery tracking on real PostgreSQL: migration on a copy of live alerts, retry SQL, test devices (rolled back)", {
  skip: !uri && "set IOT_FIXTURE_PG_URI to run; the outer transaction is always rolled back",
}, async () => {
  const pg = new Pool({ connectionString: uri, max: 1 });
  const client = await pg.connect();
  try {
    await client.query("BEGIN");
    await client.query(`CREATE SCHEMA ${SCHEMA}`);
    const alertsTable = `"${SCHEMA}".iot_alerts`;
    const devicesTable = `"${SCHEMA}".iot_devices`;
    // Copy of the live tables as they are now (structure and rows), then the new migration on top.
    await client.query(`CREATE TABLE ${alertsTable} (LIKE duriancare_iot.iot_alerts INCLUDING ALL)`);
    await client.query(`INSERT INTO ${alertsTable} SELECT * FROM duriancare_iot.iot_alerts`);
    const liveAlerts = (await client.query(`SELECT count(*)::int AS n FROM ${alertsTable}`)).rows[0].n;
    await ensureReadSchema(client, devicesTable);
    await client.query(`CREATE TABLE ${SCHEMA}.telemetry (LIKE duriancare_iot.telemetry INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)`);
    await ensureTelemetryProvenanceSchema(client, SCHEMA);
    await ensureAlertSchema(client, alertsTable);
    await ensureAlertSchema(client, alertsTable); // idempotent
    await ensureDeviceLifecycleSchema(client, SCHEMA);

    const legacy = (await client.query(
      `SELECT count(*)::int AS n FROM ${alertsTable} WHERE notification_status IS NULL AND recovery_notification_status IS NULL
         AND notification_attempts = 0`)).rows[0].n;
    assert.equal(legacy, liveAlerts, "every existing alert keeps NULL delivery state (never auto-replayed)");
    const events = [];
    let kafkaUp = false;
    const publisher = { publish: async (e) => { if (!kafkaUp) throw new Error("broker down"); events.push(e); } };
    const farmAccessClient = { alertRecipients: async () => ["owner-1"] };
    const common = { alertsTable, devicesTable, pool: client, farmAccessClient, notificationPublisher: publisher, logger: quiet };
    const firstScan = await retryNotifications({ ...common, policy: POLICY, now: new Date(Date.now() + 86_400_000) });
    assert.equal(firstScan.attempted, 0, "existing alerts are not picked up by the retry scan");

    const real = (await client.query(
      `INSERT INTO ${devicesTable} (device_uid, name, farm_id, cultivation_area_id, status, last_seen_at)
       VALUES ('fx-real', 'Real', 'farm-1', 'zone-a', 'ACTIVE', now()) RETURNING *`)).rows[0];
    const sim = (await client.query(
      `INSERT INTO ${devicesTable} (device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, is_test)
       VALUES ('fx-sim', 'Sim', 'farm-1', 'zone-a', 'ACTIVE', now() - interval '1 hour', TRUE) RETURNING *`)).rows[0];
    const checks = [{ field: "temperature", kind: "HIGH", type: "TEMPERATURE_HIGH", threshold: 35, hysteresis: 0, severity: "WARNING", notify: true }];
    const t0 = new Date();
    const read = (dev, temperature, now) => evaluateTelemetryAlerts({
      ...common, device: dev, checks, notificationPolicy: POLICY, now,
      telemetry: { temperature, humidity: null, light: null, soilMoisture: null, receivedAt: now.toISOString() },
    });

    // Kafka down: alert stored, delivery FAILED; test device: stored, SKIPPED.
    await read(real, 40, t0);
    await read(sim, 40, t0);
    const row = async (uid, type = "TEMPERATURE_HIGH") => (await client.query(
      `SELECT * FROM ${alertsTable} WHERE device_uid = $1 AND type = $2 ORDER BY created_at DESC LIMIT 1`, [uid, type])).rows[0];
    let a = await row("fx-real");
    assert.equal(a.status, "ALERTING");
    assert.equal(a.notification_status, "FAILED");
    assert.equal(a.notification_attempts, 1);
    assert.equal(new Date(a.notification_next_attempt_at).getTime(), t0.getTime() + 30_000);
    const s = await row("fx-sim");
    assert.equal(s.status, "ALERTING");
    assert.equal(s.notification_status, "SKIPPED");
    assert.equal(s.notification_detail, "TEST_DEVICE");

    // Retry: before due → nothing; after due with Kafka up → SENT with the stable id.
    assert.equal((await retryNotifications({ ...common, policy: POLICY, now: new Date(t0.getTime() + 10_000) })).attempted, 0);
    kafkaUp = true;
    const sent = await retryNotifications({ ...common, policy: POLICY, now: new Date(t0.getTime() + 31_000) });
    assert.equal(sent.sent, 1);
    a = await row("fx-real");
    assert.equal(a.notification_status, "SENT");
    assert.ok(a.notification_sent_at);
    assert.deepEqual(events.map((e) => e.eventId), [stableEventId(a.id, "OPEN", "owner-1")]);

    // Recovery: real device → recovery SENT; test device → SKIPPED; DEVICE_OFFLINE of the test device → SKIPPED.
    const t1 = new Date(t0.getTime() + 60_000);
    // An untracked (NULL) open episode, shaped like esp32-01's legacy DEVICE_OFFLINE, on the same device.
    const legacyOffline = (await client.query(
      `INSERT INTO ${alertsTable} (device_registry_id, device_uid, farm_id, cultivation_area_id, type, status, message, acknowledged_at)
       VALUES ($1, 'fx-real', 'farm-1', 'zone-a', 'DEVICE_OFFLINE', 'ACKNOWLEDGED', 'legacy offline', now()) RETURNING id`,
      [real.id])).rows[0].id;
    const eventsBefore = events.length;
    await read(real, 30, t1);
    await read(sim, 30, t1);
    a = await row("fx-real");
    assert.equal(a.status, "RECOVERED");
    assert.equal(a.recovery_notification_status, "SENT");
    assert.equal(events.at(-1).eventId, stableEventId(a.id, "RECOVERED", "owner-1"));
    const untracked = (await client.query(`SELECT * FROM ${alertsTable} WHERE id = $1`, [legacyOffline])).rows[0];
    assert.equal(untracked.status, "RECOVERED", "the legacy episode itself still recovers");
    assert.equal(untracked.notification_status, null);
    assert.equal(untracked.recovery_notification_status, "SKIPPED");
    assert.equal(untracked.recovery_notification_detail, "OPEN_UNTRACKED");
    assert.equal(events.length, eventsBefore + 1, "only the tracked TEMPERATURE_HIGH recovery was published");
    assert.equal((await row("fx-sim")).recovery_notification_status, "SKIPPED");
    await evaluateOfflineDevices({ ...common, healthConfig: { expectedTelemetryIntervalSeconds: 60, offlineAfterIntervals: 10, staleAfterIntervals: 3 },
      notificationPolicy: POLICY, now: new Date() });
    const offline = await row("fx-sim", "DEVICE_OFFLINE");
    assert.equal(offline.status, "ALERTING");
    assert.equal(offline.notification_status, "SKIPPED");
    assert.ok(events.every((e) => e.metadata.deviceUid === "fx-real"), "no event left for the test device");
    assert.equal(sim.is_test, true);

    // Constraint keeps the delivery state vocabulary closed.
    await client.query("SAVEPOINT bad");
    await assert.rejects(client.query(`UPDATE ${alertsTable} SET notification_status = 'DONE' WHERE id = $1`, [a.id]));
    await client.query("ROLLBACK TO SAVEPOINT bad");
  } finally {
    await client.query("ROLLBACK").catch(() => {});
    client.release();
    await pg.end();
  }
});
