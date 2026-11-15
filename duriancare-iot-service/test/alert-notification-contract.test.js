// Contract between IoT alert episodes and the notification events they publish:
// one event per recipient when an episode opens, none while it stays open (ALERTING or ACKNOWLEDGED),
// one IOT_ALERT_RECOVERED per recipient when it closes. Event ids are what notification-service dedups on.
const assert = require("node:assert/strict");
const { describe, it } = require("node:test");

const { evaluateOfflineDevices, evaluateTelemetryAlerts } = require("../src/alert-engine");
const { alertSql } = require("../test-support/alert-sql-emulator.cjs");
const { stableEventId } = require("../src/alertNotifications");
const { effectiveChecks } = require("../src/devices/deviceLifecycle");

const OPEN = ["ALERTING", "ACKNOWLEDGED"];
const HEALTH = { expectedTelemetryIntervalSeconds: 60, offlineAfterIntervals: 10, staleAfterIntervals: 3 };
const RECIPIENTS = ["owner-1", "agronomist-2"];

/** In-memory iot_alerts honouring the open-episode semantics of the real SQL (ALERTING or ACKNOWLEDGED). */
function alertStore({ devices = [] } = {}) {
  return {
    alerts: [],
    devices,
    async query(sql, params = []) {
      if (sql.includes("FROM iot_devices")) return { rows: this.devices };
      const handled = alertSql(this.alerts, sql, params);
      if (handled) return handled;
      return { rowCount: 0, rows: [] };
    },
  };
}

const device = (overrides = {}) => ({
  id: "dev-1", device_uid: "qa-contract-01", name: "QA trạm", farm_id: "farm-1", cultivation_area_id: "zone-b",
  status: "ACTIVE", last_seen_at: at(-10), ...overrides,
});
const reading = (values) => ({ temperature: null, humidity: null, light: null, soilMoisture: null, receivedAt: at(0).toISOString(), ...values });
function at(offsetSeconds) {
  return new Date(Date.UTC(2026, 9, 28, 16, 0, 0) + offsetSeconds * 1000);
}

// Zone rule TEMPERATURE max 35 (WARNING), device rule SOIL_MOISTURE min 40 (CRITICAL) — the live-test shapes.
const RULES = [
  { id: "rule-zone", farm_id: "farm-1", cultivation_area_id: "zone-b", device_id: null, metric: "TEMPERATURE",
    min_value: null, max_value: 35, hysteresis: 1, severity: "WARNING", enabled: true, notify: true },
  { id: "rule-dev", farm_id: "farm-1", cultivation_area_id: null, device_id: "dev-1", metric: "SOIL_MOISTURE",
    min_value: 40, max_value: null, hysteresis: 2, severity: "CRITICAL", enabled: true, notify: true },
];

function harness(store = alertStore()) {
  const events = [];
  const { checks } = effectiveChecks(device(), RULES, {});
  const ctx = {
    alertsTable: "iot_alerts", checks, device: device(), pool: store,
    farmAccessClient: { alertRecipients: async () => RECIPIENTS },
    notificationPublisher: { publish: async (event) => events.push(event) },
    logger: { error: () => undefined, info: () => undefined },
  };
  return { store, events, evaluate: (values) => evaluateTelemetryAlerts({ ...ctx, telemetry: reading(values) }) };
}

describe("alert → notification event contract", () => {
  it("a new episode publishes one event per recipient, carrying its alert id, scope and severity", async () => {
    const { store, events, evaluate } = harness();
    await evaluate({ temperature: 38, soilMoisture: 30 });
    assert.equal(store.alerts.length, 2);
    assert.equal(events.length, 2 * RECIPIENTS.length);
    for (const alert of store.alerts) {
      const forAlert = events.filter((e) => e.metadata.alertId === alert.id);
      assert.equal(new Set(forAlert.map((e) => e.eventId)).size, RECIPIENTS.length, "each recipient gets its own event id");
      assert.deepEqual(forAlert.map((e) => e.receiverId).sort(), [...RECIPIENTS].sort());
      for (const event of forAlert) {
        assert.equal(event.eventType, alert.type);
        assert.equal(event.eventId, stableEventId(alert.id, "OPEN", event.receiverId), "open event id is stable per alert and recipient");
        assert.equal(event.notificationType, "DEVICE");
        assert.equal(event.metadata.cultivationAreaId, "zone-b");
        assert.equal(event.metadata.status, "ALERTING");
      }
    }
    const soil = events.find((e) => e.eventType === "SOIL_MOISTURE_LOW");
    assert.equal(soil.metadata.severity, "CRITICAL");
    assert.equal(store.alerts.find((a) => a.type === "SOIL_MOISTURE_LOW").rule_id, "rule-dev");
    assert.equal(store.alerts.find((a) => a.type === "TEMPERATURE_HIGH").rule_id, "rule-zone");
  });

  it("continued violation, ALERTING or ACKNOWLEDGED, publishes nothing and opens no second episode", async () => {
    const { store, events, evaluate } = harness();
    await evaluate({ temperature: 38, soilMoisture: 30 });
    store.alerts.find((a) => a.type === "TEMPERATURE_HIGH").status = "ACKNOWLEDGED";
    for (const temperature of [38.5, 39, 36]) await evaluate({ temperature, soilMoisture: 29 });
    assert.equal(store.alerts.length, 2);
    assert.equal(events.length, 2 * RECIPIENTS.length);
  });

  it("inside the hysteresis band nothing recovers; back to normal publishes one recovery per recipient", async () => {
    const { store, events, evaluate } = harness();
    await evaluate({ temperature: 38, soilMoisture: 30 });
    store.alerts.find((a) => a.type === "TEMPERATURE_HIGH").status = "ACKNOWLEDGED";
    events.length = 0;
    await evaluate({ temperature: 34.5, soilMoisture: 41 }); // max 35 − h 1 = 34; min 40 + h 2 = 42
    assert.equal(events.length, 0);
    await evaluate({ temperature: 31, soilMoisture: 55 });
    assert.ok(store.alerts.every((a) => a.status === "RECOVERED"));
    assert.equal(events.length, 2 * RECIPIENTS.length);
    assert.ok(events.every((e) => e.eventType === "IOT_ALERT_RECOVERED" && e.metadata.status === "RECOVERED"));
    const ids = events.map((e) => e.eventId);
    assert.equal(new Set(ids).size, ids.length, "each recovery event has its own id");
    for (const event of events) {
      assert.equal(event.eventId, stableEventId(event.metadata.alertId, "RECOVERED", event.receiverId));
      assert.notEqual(event.eventId, stableEventId(event.metadata.alertId, "OPEN", event.receiverId));
    }
    await evaluate({ temperature: 30, soilMoisture: 60 });
    assert.equal(events.length, 2 * RECIPIENTS.length, "an already recovered episode publishes nothing more");
  });

  it("DEVICE_OFFLINE: one event when the watchdog opens it, none on later ticks, one recovery when data resumes", async () => {
    const store = alertStore({ devices: [device({ last_seen_at: at(-601) })] });
    const events = [];
    const common = {
      alertsTable: "iot_alerts", devicesTable: "iot_devices", healthConfig: HEALTH, pool: store,
      farmAccessClient: { alertRecipients: async () => RECIPIENTS },
      notificationPublisher: { publish: async (event) => events.push(event) },
      logger: { error: () => undefined, info: () => undefined },
    };
    await evaluateOfflineDevices({ ...common, now: at(0) });
    await evaluateOfflineDevices({ ...common, now: at(60) });
    await evaluateOfflineDevices({ ...common, now: at(120) });
    const offline = store.alerts.filter((a) => a.type === "DEVICE_OFFLINE");
    assert.equal(offline.length, 1);
    assert.equal(events.length, RECIPIENTS.length);
    assert.ok(events.every((e) => e.eventType === "DEVICE_OFFLINE" && e.eventId === stableEventId(offline[0].id, "OPEN", e.receiverId)));
    offline[0].status = "ACKNOWLEDGED";
    await evaluateTelemetryAlerts({
      ...common, checks: [], device: device(), telemetry: reading({ temperature: 30 }),
    });
    assert.equal(offline[0].status, "RECOVERED");
    const recoveries = events.filter((e) => e.eventType === "IOT_ALERT_RECOVERED");
    assert.equal(recoveries.length, RECIPIENTS.length);
    assert.ok(recoveries.every((e) => e.metadata.alertId === offline[0].id && e.metadata.alertType === "DEVICE_OFFLINE"));
  });

  it("a rule with notify=false still records the episode but publishes nothing", async () => {
    const store = alertStore();
    const events = [];
    const { checks } = effectiveChecks(device(), [{ ...RULES[0], notify: false }], {});
    await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts", checks, device: device(), pool: store, telemetry: reading({ temperature: 40 }),
      farmAccessClient: { alertRecipients: async () => RECIPIENTS },
      notificationPublisher: { publish: async (event) => events.push(event) },
    });
    assert.equal(store.alerts.length, 1);
    assert.equal(events.length, 0);
  });
});
