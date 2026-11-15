// Delivery tracking and bounded retry of alert notifications, and test-device isolation.
// A fake Kafka + a fake notification-service (dedup on eventId, like the real unique sourceEventId) make the
// end-to-end effect observable: how many in-app notifications each user really ends up with.
const assert = require("node:assert/strict");
const { describe, it } = require("node:test");

const { evaluateOfflineDevices, evaluateTelemetryAlerts, retryNotifications } = require("../src/alert-engine");
const { stableEventId } = require("../src/alertNotifications");
const { alertSql } = require("../test-support/alert-sql-emulator.cjs");

const POLICY = { maxAttempts: 3, baseDelayMs: 30_000, maxDelayMs: 600_000, leaseMs: 120_000, batchSize: 50, testReceiverId: "" };
const HEALTH = { expectedTelemetryIntervalSeconds: 60, offlineAfterIntervals: 10, staleAfterIntervals: 3 };
const T0 = Date.UTC(2026, 9, 29, 8, 0, 0);
const at = (seconds) => new Date(T0 + seconds * 1000);
const quiet = { info: () => undefined, error: () => undefined };

function device(overrides = {}) {
  return { id: "dev-1", device_uid: "field-1", name: "Trạm", farm_id: "farm-1", cultivation_area_id: "zone-a",
    status: "ACTIVE", last_seen_at: at(-10), is_test: false, ...overrides };
}

function world({ devices = [device()], recipients = ["owner-1", "agro-2"], farmAccessClient: lookup } = {}) {
  const alerts = [];
  const inbox = new Map(); // notification-service: one notification per eventId
  const sent = []; // every message that reached Kafka (including duplicates)
  const kafka = { mode: "up", failFor: new Set(), ackLost: false };
  const pool = {
    async query(sql, params = []) {
      if (sql.includes("FROM iot_devices") && sql.includes("WHERE id = $1")) {
        return { rows: devices.filter((d) => d.id === params[0]) };
      }
      if (sql.includes("FROM iot_devices")) return { rows: devices };
      return alertSql(alerts, sql, params) || { rowCount: 0, rows: [] };
    },
  };
  const notificationPublisher = {
    async publish(event) {
      if (kafka.mode === "down" || kafka.failFor.has(event.receiverId)) throw new Error("KafkaJSConnectionError: broker down");
      sent.push(event);
      if (!inbox.has(event.eventId)) inbox.set(event.eventId, event);
      if (kafka.ackLost) throw new Error("KafkaJSRequestTimeoutError: ack not received");
    },
  };
  const farmAccessClient = lookup || { alertRecipients: async () => recipients };
  const common = { alertsTable: "iot_alerts", devicesTable: "iot_devices", farmAccessClient, notificationPublisher, pool, logger: quiet };
  return {
    alerts, inbox, sent, kafka, devices,
    reading: (values, now = at(0), dev = devices[0]) => evaluateTelemetryAlerts({
      ...common, device: dev, notificationPolicy: POLICY, now,
      checks: [{ field: "temperature", kind: "HIGH", type: "TEMPERATURE_HIGH", threshold: 35, hysteresis: 0, severity: "WARNING", notify: true, ruleId: null }],
      telemetry: { temperature: null, humidity: null, light: null, soilMoisture: null, receivedAt: now.toISOString(), ...values },
    }),
    watchdog: (now) => evaluateOfflineDevices({ ...common, healthConfig: HEALTH, notificationPolicy: POLICY, now }),
    retry: (now, policy = POLICY) => retryNotifications({ ...common, policy, now }),
    inboxFor: (receiverId) => [...inbox.values()].filter((n) => n.receiverId === receiverId),
  };
}

describe("alert notification delivery tracking", () => {
  it("publish succeeds: delivery SENT on first attempt, one notification per recipient, retry finds nothing", async () => {
    const w = world();
    await w.reading({ temperature: 40 });
    const [alert] = w.alerts;
    assert.equal(alert.status, "ALERTING");
    assert.equal(alert.notification_status, "SENT");
    assert.equal(alert.notification_attempts, 1);
    assert.equal(w.inboxFor("owner-1").length, 1);
    assert.equal(w.inboxFor("agro-2").length, 1, "second recipient is not swallowed by dedup");
    assert.equal((await w.retry(at(3600))).attempted, 0);
    assert.equal(w.sent.length, 2);
  });

  it("publish fails: alert is kept, delivery FAILED with backoff; the retry delivers the same event ids once", async () => {
    const w = world();
    w.kafka.mode = "down";
    await w.reading({ temperature: 40 });
    const [alert] = w.alerts;
    assert.equal(alert.status, "ALERTING", "alert state is independent of delivery");
    assert.equal(alert.notification_status, "FAILED");
    assert.equal(alert.notification_attempts, 1);
    assert.match(alert.notification_detail, /broker down/);
    assert.equal(alert.notification_next_attempt_at.getTime(), at(30).getTime());
    assert.equal((await w.retry(at(10))).attempted, 0, "not before the backoff elapsed");
    w.kafka.mode = "up";
    const summary = await w.retry(at(31));
    assert.deepEqual({ attempted: summary.attempted, sent: summary.sent }, { attempted: 1, sent: 1 });
    assert.equal(alert.notification_status, "SENT");
    assert.equal(alert.notification_attempts, 2);
    assert.deepEqual(w.sent.map((e) => e.eventId).sort(),
      ["owner-1", "agro-2"].map((r) => stableEventId(alert.id, "OPEN", r)).sort());
    assert.equal((await w.retry(at(5000))).attempted, 0, "a SENT delivery is never replayed");
  });

  it("retry keeps failing: exponential backoff, then ABANDONED after maxAttempts and never retried again", async () => {
    const w = world();
    w.kafka.mode = "down";
    await w.reading({ temperature: 40 });
    const [alert] = w.alerts;
    await w.retry(at(31));
    assert.equal(alert.notification_attempts, 2);
    assert.equal(alert.notification_next_attempt_at.getTime(), at(31 + 60).getTime(), "30 s, then 60 s");
    await w.retry(at(92));
    assert.equal(alert.notification_status, "ABANDONED");
    assert.equal(alert.notification_attempts, 3);
    assert.equal(alert.notification_next_attempt_at, null);
    w.kafka.mode = "up";
    assert.equal((await w.retry(at(99999))).attempted, 0);
    assert.equal(w.inbox.size, 0);
  });

  it("partial failure and lost acks never produce duplicate notifications", async () => {
    const w = world();
    w.kafka.failFor.add("agro-2"); // owner's message goes through, agronomist's fails
    await w.reading({ temperature: 40 });
    assert.equal(w.alerts[0].notification_status, "FAILED");
    w.kafka.failFor.clear();
    w.kafka.ackLost = true; // consumer gets it, producer sees a timeout
    await w.retry(at(31));
    assert.equal(w.alerts[0].notification_status, "FAILED");
    w.kafka.ackLost = false;
    await w.retry(at(200));
    assert.equal(w.alerts[0].notification_status, "SENT");
    assert.ok(w.sent.length > 2, "the same events were re-sent");
    assert.equal(w.inboxFor("owner-1").length, 1);
    assert.equal(w.inboxFor("agro-2").length, 1);
  });

  it("an attempt that never recorded its outcome (crash) is picked up once its lease expires", async () => {
    const w = world();
    const hang = w.kafka;
    hang.mode = "up";
    await w.reading({ temperature: 40 });
    // Simulate a crash right after the INSERT: delivery still PENDING with its lease.
    Object.assign(w.alerts[0], { notification_status: "PENDING", notification_attempts: 0, notification_next_attempt_at: at(120) });
    w.inbox.clear();
    assert.equal((await w.retry(at(60))).attempted, 0, "lease still held");
    await w.retry(at(121));
    assert.equal(w.alerts[0].notification_status, "SENT");
    assert.equal(w.inbox.size, 2);
  });

  it("recovery: delivered with its own stable ids; a failed recovery delivery is retried", async () => {
    const w = world();
    await w.reading({ temperature: 40 });
    w.kafka.mode = "down";
    await w.reading({ temperature: 30 }, at(10));
    const [alert] = w.alerts;
    assert.equal(alert.status, "RECOVERED");
    assert.equal(alert.notification_status, "SENT");
    assert.equal(alert.recovery_notification_status, "FAILED");
    w.kafka.mode = "up";
    await w.retry(at(41));
    assert.equal(alert.recovery_notification_status, "SENT");
    const recoveries = [...w.inbox.values()].filter((n) => n.eventType === "IOT_ALERT_RECOVERED");
    assert.deepEqual(recoveries.map((e) => e.eventId).sort(),
      ["owner-1", "agro-2"].map((r) => stableEventId(alert.id, "RECOVERED", r)).sort());
  });

  it("an alert that recovered before its opening notification was delivered notifies nobody, and stops retrying", async () => {
    const w = world();
    w.kafka.mode = "down";
    await w.reading({ temperature: 40 });
    w.kafka.mode = "up";
    await w.reading({ temperature: 30 }, at(5));
    const [alert] = w.alerts;
    assert.equal(alert.notification_status, "SKIPPED");
    assert.equal(alert.notification_detail, "RECOVERED_BEFORE_DELIVERY");
    assert.equal(alert.recovery_notification_status, "SKIPPED");
    assert.equal(alert.recovery_notification_detail, "OPEN_NOT_DELIVERED");
    assert.equal((await w.retry(at(9999))).attempted, 0);
    assert.equal(w.inbox.size, 0);
  });

  it("alerts created before delivery tracking (NULL): recovery is not notified, the alert still recovers", async () => {
    // e.g. esp32-01's ACKNOWLEDGED DEVICE_OFFLINE whose opening event never reached notification-service.
    const w = world();
    w.alerts.push({ id: "legacy-1", device_uid: "field-1", device_registry_id: "dev-1", type: "DEVICE_OFFLINE", status: "ACKNOWLEDGED",
      farm_id: "farm-1", message: "offline", notification_status: null, notification_attempts: 0 });
    await w.reading({ temperature: 30 });
    const [legacy] = w.alerts;
    assert.equal(legacy.status, "RECOVERED");
    assert.equal(legacy.notification_status, null, "history is not rewritten");
    assert.equal(legacy.recovery_notification_status, "SKIPPED");
    assert.equal(legacy.recovery_notification_detail, "OPEN_UNTRACKED");
    assert.equal((await w.retry(at(99999))).attempted, 0);
    assert.equal(w.sent.length, 0, "no 'back to normal' without a known opening notification");
  });

  it("recovery is notified only after a delivered opening notification, with its own stable ids", async () => {
    const w = world();
    await w.reading({ temperature: 40 }, at(0));
    await w.reading({ temperature: 30 }, at(10));
    const [alert] = w.alerts;
    assert.equal(alert.recovery_notification_status, "SENT");
    assert.deepEqual(w.inboxFor("owner-1").map((n) => n.eventId),
      [stableEventId(alert.id, "OPEN", "owner-1"), stableEventId(alert.id, "RECOVERED", "owner-1")]);
  });
});

describe("test devices: alerts stored, no notification to real users", () => {
  it("threshold alert and recovery of an is_test device notify nobody, a real device is unaffected", async () => {
    const testDevice = device({ id: "dev-test", device_uid: "qa-sim-1", is_test: true });
    const w = world({ devices: [device(), testDevice] });
    await w.reading({ temperature: 40 }, at(0), testDevice);
    await w.reading({ temperature: 40 }, at(0), w.devices[0]);
    const testAlert = w.alerts.find((a) => a.device_uid === "qa-sim-1");
    assert.equal(testAlert.status, "ALERTING", "the episode is stored");
    assert.equal(testAlert.notification_status, "SKIPPED");
    assert.equal(testAlert.notification_detail, "TEST_DEVICE");
    await w.reading({ temperature: 30 }, at(10), testDevice);
    assert.equal(testAlert.status, "RECOVERED");
    assert.equal(testAlert.recovery_notification_status, "SKIPPED");
    assert.equal(testAlert.recovery_notification_detail, "TEST_DEVICE");
    assert.ok(w.sent.every((e) => e.metadata.deviceUid === "field-1"), "nothing from the test device reached Kafka");
    assert.equal(w.inboxFor("owner-1").length, 1, "real device still notifies");
    assert.equal((await w.retry(at(99999))).attempted, 0);
  });

  it("DEVICE_OFFLINE of an is_test device is stored without notification; a real offline device notifies", async () => {
    const w = world({ devices: [device({ last_seen_at: at(-601) }), device({ id: "dev-test", device_uid: "qa-sim-1", is_test: true, last_seen_at: at(-601) })] });
    await w.watchdog(at(0));
    const offline = Object.fromEntries(w.alerts.map((a) => [a.device_uid, a]));
    assert.equal(offline["qa-sim-1"].type, "DEVICE_OFFLINE");
    assert.equal(offline["qa-sim-1"].notification_status, "SKIPPED");
    assert.equal(offline["field-1"].notification_status, "SENT");
    assert.ok(w.sent.every((e) => e.metadata.deviceUid === "field-1"));
  });

  it("with IOT_TEST_NOTIFICATION_RECEIVER_ID set, test-device notifications go to that account only", async () => {
    const testDevice = device({ id: "dev-test", device_uid: "qa-sim-1", is_test: true });
    const w = world({ devices: [testDevice] });
    await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts", device: testDevice, pool: { query: async (sql, p) => alertSql(w.alerts, sql, p) || { rows: [] } },
      farmAccessClient: { alertRecipients: async () => ["owner-1"] }, logger: quiet,
      notificationPublisher: { publish: async (event) => w.sent.push(event) },
      notificationPolicy: { ...POLICY, testReceiverId: "qa-receiver" },
      checks: [{ field: "temperature", kind: "HIGH", type: "TEMPERATURE_HIGH", threshold: 35, hysteresis: 0, notify: true }],
      telemetry: { temperature: 40, receivedAt: at(0).toISOString() },
    });
    assert.deepEqual(w.sent.map((e) => e.receiverId), ["qa-receiver"]);
    assert.equal(w.alerts[0].notification_status, "SENT");
    assert.equal(w.alerts[0].notification_detail, "TEST_DEVICE_TEST_RECEIVER");
  });
});

describe("recipients across retries: transient lookups and scope changes", () => {
  const { createFarmAccessClient } = require("../src/read-api");
  async function withFetch(handler, body) {
    const original = global.fetch;
    global.fetch = handler;
    try { return await body(); } finally { global.fetch = original; }
  }
  const reply = (status, recipientUserIds = []) => async () => ({ ok: status < 300, status, json: async () => ({ recipientUserIds }) });

  it("farm-service 5xx is a failed attempt (retried), not 'no recipients'; the retry delivers once it answers", async () => {
    const client = createFarmAccessClient({ farmServiceUrl: "http://farm", internalToken: "t" });
    const w = world({ farmAccessClient: client });
    await withFetch(reply(503), () => w.reading({ temperature: 40 }, at(0)));
    const [alert] = w.alerts;
    assert.equal(alert.notification_status, "FAILED");
    assert.match(alert.notification_detail, /503/);
    const asked = [];
    await withFetch(async (url, init) => { asked.push(JSON.parse(init.body)); return reply(200, ["owner-1"])(); },
      () => w.retry(at(31)));
    assert.equal(alert.notification_status, "SENT");
    assert.deepEqual(asked, [{ cultivationAreaId: "zone-a", farmId: "farm-1", permission: "READ_IOT" }]);
    assert.equal(w.inboxFor("owner-1").length, 1);
  });

  it("farm-service unreachable or timing out is also a failed attempt", async () => {
    const client = createFarmAccessClient({ farmServiceUrl: "http://farm", internalToken: "t" });
    const w = world({ farmAccessClient: client });
    await withFetch(async () => { throw new Error("The operation was aborted due to timeout"); }, () => w.reading({ temperature: 40 }));
    assert.equal(w.alerts[0].notification_status, "FAILED");
  });

  it("farm no longer exists (404): nobody to notify, SKIPPED for good", async () => {
    const client = createFarmAccessClient({ farmServiceUrl: "http://farm", internalToken: "t" });
    const w = world({ farmAccessClient: client });
    await withFetch(reply(404), () => w.reading({ temperature: 40 }));
    assert.equal(w.alerts[0].notification_status, "SKIPPED");
    assert.equal(w.alerts[0].notification_detail, "NO_RECIPIENTS");
  });

  it("device re-claimed by another farm between attempts: the retry goes to the alert's farm, never the new owner", async () => {
    const asked = [];
    const lookup = { alertRecipients: async (farmId, zoneId) => { asked.push([farmId, zoneId]); return farmId === "farm-1" ? ["owner-1"] : ["new-owner"]; } };
    const w = world({ farmAccessClient: lookup });
    w.kafka.mode = "down";
    await w.reading({ temperature: 40 }, at(0));
    Object.assign(w.devices[0], { farm_id: "farm-2", cultivation_area_id: "zone-z" }); // removed, re-provisioned, claimed elsewhere
    w.kafka.mode = "up";
    await w.retry(at(31));
    assert.equal(w.alerts[0].notification_status, "SENT");
    assert.deepEqual(asked.at(-1), ["farm-1", "zone-a"]);
    assert.equal(w.inboxFor("new-owner").length, 0, "the new owner never sees the previous owner's alert");
    assert.equal(w.inboxFor("owner-1")[0].metadata.farmId, "farm-1");
  });

  it("access revoked between attempts: the retry uses permissions as they are now", async () => {
    let members = ["owner-1", "agro-2"];
    const w = world({ farmAccessClient: { alertRecipients: async () => members } });
    w.kafka.mode = "down";
    await w.reading({ temperature: 40 }, at(0));
    members = ["owner-1"]; // agronomist's authorization revoked
    w.kafka.mode = "up";
    await w.retry(at(31));
    assert.equal(w.inboxFor("agro-2").length, 0);
    assert.equal(w.inboxFor("owner-1").length, 1);
  });

  it("an alert row without a farm scope is never sent to anyone", async () => {
    const w = world();
    w.alerts.push({ id: "noscope-1", device_uid: "field-1", device_registry_id: "dev-1", type: "TEMPERATURE_HIGH", status: "ALERTING",
      farm_id: null, message: "x", notification_status: "FAILED", notification_attempts: 1, notification_next_attempt_at: at(0) });
    await w.retry(at(31));
    assert.equal(w.alerts[0].notification_status, "SKIPPED");
    assert.equal(w.alerts[0].notification_detail, "NO_ALERT_SCOPE");
    assert.equal(w.sent.length, 0);
  });
});
