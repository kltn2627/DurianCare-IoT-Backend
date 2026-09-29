const assert = require("node:assert/strict");
const { describe, it } = require("node:test");

const {
  connectivityForDevice,
  evaluateOfflineDevices,
  evaluateTelemetryAlerts
} = require("../src/alert-engine");

const HEALTH = {
  expectedTelemetryIntervalSeconds: 60,
  offlineAfterIntervals: 10,
  staleAfterIntervals: 3
};

describe("IoT connectivity model", () => {
  it("returns UNKNOWN for never reported active devices", () => {
    const status = connectivityForDevice(device({ last_seen_at: null }), HEALTH, dateAt(0));
    assert.equal(status.status, "UNKNOWN");
  });

  it("uses configured cadence boundaries for online, stale and offline", () => {
    assert.equal(connectivityForDevice(device({ last_seen_at: dateAt(-179) }), HEALTH, dateAt(0)).status, "ONLINE");
    assert.equal(connectivityForDevice(device({ last_seen_at: dateAt(-181) }), HEALTH, dateAt(0)).status, "STALE");
    assert.equal(connectivityForDevice(device({ last_seen_at: dateAt(-601) }), HEALTH, dateAt(0)).status, "OFFLINE");
  });

  it("does not report inactive or maintenance devices as offline", () => {
    assert.equal(connectivityForDevice(device({ last_seen_at: dateAt(-9999), status: "INACTIVE" }), HEALTH, dateAt(0)).status, "UNKNOWN");
    assert.equal(connectivityForDevice(device({ last_seen_at: dateAt(-9999), status: "MAINTENANCE" }), HEALTH, dateAt(0)).status, "UNKNOWN");
  });
});

describe("IoT alert evaluation", () => {
  it("creates one threshold alert and deduplicates continued violation", async () => {
    const pool = createAlertPool();
    const notifications = [];
    const context = {
      alertsTable: "iot_alerts",
      device: device(),
      farmAccessClient: { alertRecipients: async () => ["owner-1"] },
      notificationPublisher: { publish: async (event) => notifications.push(event) },
      pool,
      thresholds: { temperatureHigh: 38 }
    };
    await evaluateTelemetryAlerts({ ...context, telemetry: telemetry({ temperature: 40 }) });
    await evaluateTelemetryAlerts({ ...context, telemetry: telemetry({ temperature: 41 }) });
    assert.equal(pool.alerts.filter((alert) => alert.status === "ALERTING").length, 1);
    assert.equal(notifications.length, 1);
  });

  it("recovers an active threshold alert when telemetry returns to normal", async () => {
    const pool = createAlertPool();
    await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts",
      device: device(),
      farmAccessClient: { alertRecipients: async () => [] },
      notificationPublisher: { publish: async () => undefined },
      pool,
      telemetry: telemetry({ temperature: 40 }),
      thresholds: { temperatureHigh: 38 }
    });
    const result = await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts",
      device: device(),
      farmAccessClient: { alertRecipients: async () => [] },
      notificationPublisher: { publish: async () => undefined },
      pool,
      telemetry: telemetry({ temperature: 30 }),
      thresholds: { temperatureHigh: 38 }
    });
    assert.equal(result.recovered.length, 1);
    assert.equal(pool.alerts[0].status, "RECOVERED");
  });

  it("does not create threshold alerts for unregistered or inactive devices", async () => {
    const pool = createAlertPool();
    await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts",
      device: null,
      farmAccessClient: { alertRecipients: async () => ["owner-1"] },
      notificationPublisher: { publish: async () => undefined },
      pool,
      telemetry: telemetry({ temperature: 40 }),
      thresholds: { temperatureHigh: 38 }
    });
    await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts",
      device: device({ status: "INACTIVE" }),
      farmAccessClient: { alertRecipients: async () => ["owner-1"] },
      notificationPublisher: { publish: async () => undefined },
      pool,
      telemetry: telemetry({ temperature: 40 }),
      thresholds: { temperatureHigh: 38 }
    });
    assert.equal(pool.alerts.length, 0);
  });

  it("offline watchdog deduplicates and recovers DEVICE_OFFLINE", async () => {
    const pool = createAlertPool({
      devices: [device({ last_seen_at: dateAt(-601), status: "ACTIVE" })]
    });
    const context = {
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      farmAccessClient: { alertRecipients: async () => [] },
      healthConfig: HEALTH,
      notificationPublisher: { publish: async () => undefined },
      now: dateAt(0),
      pool
    };
    await evaluateOfflineDevices(context);
    await evaluateOfflineDevices(context);
    assert.equal(pool.alerts.filter((alert) => alert.type === "DEVICE_OFFLINE" && alert.status === "ALERTING").length, 1);
    pool.devices[0].last_seen_at = dateAt(-10);
    const recovered = await evaluateOfflineDevices(context);
    assert.equal(recovered.recovered.length, 1);
    assert.equal(pool.alerts[0].status, "RECOVERED");
  });

  it("does not mark never-reported active devices offline", async () => {
    const pool = createAlertPool({
      devices: [device({ last_seen_at: null, status: "ACTIVE" })]
    });
    const result = await evaluateOfflineDevices({
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      farmAccessClient: { alertRecipients: async () => ["owner-1"] },
      healthConfig: HEALTH,
      notificationPublisher: { publish: async () => undefined },
      now: dateAt(0),
      pool
    });
    assert.equal(result.created.length, 0);
    assert.equal(pool.alerts.length, 0);
  });

  it("recovers DEVICE_OFFLINE when valid telemetry resumes", async () => {
    const pool = createAlertPool();
    pool.alerts.push(alert({ type: "DEVICE_OFFLINE" }));
    const notifications = [];
    const result = await evaluateTelemetryAlerts({
      alertsTable: "iot_alerts",
      device: device(),
      farmAccessClient: { alertRecipients: async () => ["owner-1"] },
      notificationPublisher: { publish: async (event) => notifications.push(event) },
      pool,
      telemetry: telemetry({ temperature: 30 }),
      thresholds: { temperatureHigh: 38 }
    });
    assert.equal(result.recovered.length, 1);
    assert.equal(pool.alerts[0].status, "RECOVERED");
    assert.equal(notifications[0].eventType, "IOT_ALERT_RECOVERED");
  });

  it("recovers active offline alerts for inactive or maintenance devices", async () => {
    const pool = createAlertPool({
      devices: [device({ last_seen_at: dateAt(-999), status: "MAINTENANCE" })]
    });
    pool.alerts.push(alert({ type: "DEVICE_OFFLINE" }));
    const result = await evaluateOfflineDevices({
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      farmAccessClient: { alertRecipients: async () => [] },
      healthConfig: HEALTH,
      notificationPublisher: { publish: async () => undefined },
      now: dateAt(0),
      pool
    });
    assert.equal(result.created.length, 0);
    assert.equal(result.recovered.length, 1);
    assert.equal(pool.alerts[0].status, "RECOVERED");
  });

  it("keeps persisted alert when recipient lookup or notification publish fails", async () => {
    const pool = createAlertPool({
      devices: [device({ last_seen_at: dateAt(-601), status: "ACTIVE" })]
    });
    const logs = [];
    const result = await evaluateOfflineDevices({
      alertsTable: "iot_alerts",
      devicesTable: "iot_devices",
      farmAccessClient: { alertRecipients: async () => { throw new Error("farm unavailable"); } },
      healthConfig: HEALTH,
      logger: { error: (...args) => logs.push(args), info: () => undefined },
      notificationPublisher: { publish: async () => { throw new Error("kafka unavailable"); } },
      now: dateAt(0),
      pool
    });
    assert.equal(result.created.length, 1);
    assert.equal(pool.alerts.length, 1);
    assert.equal(logs.length, 1);
  });
});

function createAlertPool({ devices = [] } = {}) {
  return {
    alerts: [],
    devices,
    async query(sql, params = []) {
      if (sql.includes("FROM iot_devices")) {
        return { rows: this.devices };
      }
      if (sql.includes("UPDATE iot_alerts") && sql.includes("status = 'RECOVERED'")) {
        const alert = this.alerts.find((item) => item.device_uid === params[0] && item.type === params[1] && item.status === "ALERTING");
        if (!alert) return { rowCount: 0, rows: [] };
        alert.status = "RECOVERED";
        alert.recovered_at = params[2];
        return { rowCount: 1, rows: [alert] };
      }
      if (sql.includes("UPDATE iot_alerts")) {
        const alert = this.alerts.find((item) => item.device_uid === params[0] && item.type === params[1] && item.status === "ALERTING");
        if (!alert) return { rowCount: 0, rows: [] };
        alert.measured_value = params[2];
        alert.threshold_value = params[3];
        alert.message = params[4];
        alert.last_observed_at = params[5];
        return { rowCount: 1, rows: [alert] };
      }
      if (sql.includes("INSERT INTO iot_alerts")) {
        const alert = {
          cultivation_area_id: params[3],
          device_registry_id: params[0],
          device_uid: params[1],
          farm_id: params[2],
          id: `alert-${this.alerts.length + 1}`,
          measured_value: params[5],
          message: params[7],
          notification_event_id: params[9],
          started_at: params[8],
          status: "ALERTING",
          threshold_value: params[6],
          type: params[4]
        };
        this.alerts.push(alert);
        return { rowCount: 1, rows: [alert] };
      }
      return { rowCount: 0, rows: [] };
    }
  };
}

function telemetry(overrides = {}) {
  return {
    humidity: null,
    light: null,
    receivedAt: "2026-09-13T00:00:00.000Z",
    temperature: null,
    ...overrides
  };
}

function device(overrides = {}) {
  return {
    cultivation_area_id: "area-1",
    device_uid: "esp32-1",
    farm_id: "farm-1",
    id: "device-1",
    last_seen_at: dateAt(-10),
    name: "ESP32 North",
    status: "ACTIVE",
    ...overrides
  };
}

function alert(overrides = {}) {
  return {
    cultivation_area_id: "area-1",
    device_registry_id: "device-1",
    device_uid: "esp32-1",
    farm_id: "farm-1",
    id: `alert-${Math.random()}`,
    measured_value: 601,
    message: "offline",
    notification_event_id: "event-1",
    status: "ALERTING",
    threshold_value: 600,
    type: "DEVICE_OFFLINE",
    ...overrides
  };
}

function dateAt(offsetSeconds) {
  return new Date(Date.UTC(2026, 8, 13, 0, 0, 0) + offsetSeconds * 1000);
}
