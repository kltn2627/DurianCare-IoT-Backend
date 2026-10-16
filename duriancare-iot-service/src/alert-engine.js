const { randomUUID } = require("node:crypto");

const CONNECTIVITY_BASIS = "INFERRED_FROM_LAST_TELEMETRY";
const ALERT_STATUS = {
  ACKNOWLEDGED: "ACKNOWLEDGED",
  ALERTING: "ALERTING",
  RECOVERED: "RECOVERED"
};

const THRESHOLD_RULES = [
  { field: "temperature", kind: "HIGH", thresholdKey: "temperatureHigh", type: "TEMPERATURE_HIGH" },
  { field: "temperature", kind: "LOW", thresholdKey: "temperatureLow", type: "TEMPERATURE_LOW" },
  { field: "humidity", kind: "HIGH", thresholdKey: "humidityHigh", type: "HUMIDITY_HIGH" },
  { field: "humidity", kind: "LOW", thresholdKey: "humidityLow", type: "HUMIDITY_LOW" },
  { field: "light", kind: "HIGH", thresholdKey: "lightHigh", type: "LIGHT_HIGH" },
  { field: "light", kind: "LOW", thresholdKey: "lightLow", type: "LIGHT_LOW" }
];

function connectivityForDevice(device, healthConfig, now = new Date()) {
  if (!device || device.status === "DELETED") {
    return {
      basis: CONNECTIVITY_BASIS,
      lastTelemetryAt: null,
      status: "UNKNOWN"
    };
  }
  if (device.status === "INACTIVE" || device.status === "MAINTENANCE") {
    return {
      basis: CONNECTIVITY_BASIS,
      lastTelemetryAt: toIsoOrNull(device.last_seen_at),
      status: "UNKNOWN",
      suppressesOfflineAlert: true
    };
  }
  if (!device.last_seen_at) {
    return {
      basis: CONNECTIVITY_BASIS,
      lastTelemetryAt: null,
      status: "UNKNOWN"
    };
  }
  const lastSeen = new Date(device.last_seen_at);
  const ageSeconds = Math.max(0, Math.floor((now.getTime() - lastSeen.getTime()) / 1000));
  const expected = Math.max(1, Number(healthConfig.expectedTelemetryIntervalSeconds || 60));
  const staleAfterSeconds = expected * Math.max(1, Number(healthConfig.staleAfterIntervals || 3));
  const offlineAfterSeconds = expected * Math.max(1, Number(healthConfig.offlineAfterIntervals || 10));
  let status = "ONLINE";
  if (ageSeconds > offlineAfterSeconds) {
    status = "OFFLINE";
  } else if (ageSeconds > staleAfterSeconds) {
    status = "STALE";
  }
  return {
    ageSeconds,
    basis: CONNECTIVITY_BASIS,
    expectedTelemetryIntervalSeconds: expected,
    lastTelemetryAt: lastSeen.toISOString(),
    offlineAfterSeconds,
    staleAfterSeconds,
    status
  };
}

async function ensureAlertSchema(pool, alertsTable) {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS ${alertsTable} (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      device_registry_id UUID,
      device_uid VARCHAR(150) NOT NULL,
      farm_id VARCHAR(150) NOT NULL,
      cultivation_area_id VARCHAR(150),
      type VARCHAR(60) NOT NULL,
      status VARCHAR(40) NOT NULL DEFAULT 'ALERTING',
      measured_value DOUBLE PRECISION,
      threshold_value DOUBLE PRECISION,
      message TEXT NOT NULL,
      started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      last_observed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      recovered_at TIMESTAMPTZ,
      acknowledged_at TIMESTAMPTZ,
      acknowledged_by VARCHAR(150),
      notification_event_id UUID,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT ck_iot_alerts_status CHECK (
        status IN ('ALERTING', 'RECOVERED', 'ACKNOWLEDGED')
      )
    )`
  );
  await pool.query(
    `CREATE INDEX IF NOT EXISTS idx_iot_alerts_farm_status
     ON ${alertsTable} (farm_id, status, last_observed_at DESC)`
  );
  await pool.query(
    `CREATE INDEX IF NOT EXISTS idx_iot_alerts_device_status
     ON ${alertsTable} (device_uid, status, last_observed_at DESC)`
  );
  await pool.query(
    `CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_alerts_active_device_type
     ON ${alertsTable} (device_uid, type)
     WHERE status = 'ALERTING'`
  );
}

async function evaluateTelemetryAlerts({
  alertsTable,
  device,
  farmAccessClient,
  logger = console,
  notificationPublisher,
  pool,
  telemetry,
  thresholds
}) {
  if (!device || device.status !== "ACTIVE") {
    return { created: [], recovered: [] };
  }
  const created = [];
  const recovered = [];
  const offlineAlert = await recoverAlert({
    alertsTable,
    deviceUid: device.device_uid,
    pool,
    recoveredAt: telemetry.receivedAt,
    type: "DEVICE_OFFLINE"
  });
  if (offlineAlert) {
    recovered.push(offlineAlert);
    await publishAlertNotification({
      alert: offlineAlert,
      device,
      farmAccessClient,
      logger,
      notificationPublisher,
      recovered: true
    });
  }
  for (const rule of THRESHOLD_RULES) {
    const value = telemetry[rule.field];
    const threshold = thresholds[rule.thresholdKey];
    if (!Number.isFinite(value) || !Number.isFinite(threshold)) continue;
    const violated = rule.kind === "HIGH" ? value > threshold : value < threshold;
    if (violated) {
      const alert = await openOrTouchAlert({
        alertsTable,
        device,
        pool,
        rule,
        telemetry,
        threshold,
        value
      });
      if (alert.created) {
        created.push(alert.row);
        await publishAlertNotification({
          alert: alert.row,
          device,
          farmAccessClient,
          logger,
          notificationPublisher,
          recovered: false
        });
      }
    } else {
      const alert = await recoverAlert({
        alertsTable,
        deviceUid: device.device_uid,
        pool,
        recoveredAt: telemetry.receivedAt,
        type: rule.type
      });
      if (alert) {
        recovered.push(alert);
        await publishAlertNotification({
          alert,
          device,
          farmAccessClient,
          logger,
          notificationPublisher,
          recovered: true
        });
      }
    }
  }
  return { created, recovered };
}

async function evaluateOfflineDevices({
  alertsTable,
  farmAccessClient,
  healthConfig,
  logger = console,
  notificationPublisher,
  pool,
  devicesTable,
  now = new Date()
}) {
  const result = await pool.query(
    `SELECT id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at
     FROM ${devicesTable}
     WHERE status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')
       AND (
         last_seen_at IS NOT NULL
         OR status IN ('INACTIVE', 'MAINTENANCE')
       )`
  );
  const created = [];
  const recovered = [];
  let evaluated = 0;
  for (const device of result.rows) {
    evaluated += 1;
    const connectivity = connectivityForDevice(device, healthConfig, now);
    if (device.status === "ACTIVE" && connectivity.status === "OFFLINE") {
      const alert = await openOrTouchAlert({
        alertsTable,
        device,
        pool,
        rule: { field: "last_seen_at", type: "DEVICE_OFFLINE" },
        telemetry: { receivedAt: now.toISOString() },
        threshold: connectivity.offlineAfterSeconds,
        value: connectivity.ageSeconds
      });
      if (alert.created) {
        created.push(alert.row);
        await publishAlertNotification({
          alert: alert.row,
          device,
          farmAccessClient,
          logger,
          notificationPublisher,
          recovered: false
        });
      }
    } else {
      const alert = await recoverAlert({
        alertsTable,
        deviceUid: device.device_uid,
        pool,
        recoveredAt: now.toISOString(),
        type: "DEVICE_OFFLINE"
      });
      if (alert) {
        recovered.push(alert);
        await publishAlertNotification({
          alert,
          device,
          farmAccessClient,
          logger,
          notificationPublisher,
          recovered: true
        });
      }
    }
  }
  logger.info("iot.offlineWatchdog.devicesEvaluated", { evaluated });
  return { created, evaluated, recovered };
}

async function openOrTouchAlert({ alertsTable, device, pool, rule, telemetry, threshold, value }) {
  const message = alertMessage(rule.type, device, value, threshold);
  const existing = await pool.query(
    `UPDATE ${alertsTable}
     SET measured_value = $3,
         threshold_value = $4,
         message = $5,
         last_observed_at = $6,
         updated_at = CURRENT_TIMESTAMP
     WHERE device_uid = $1
       AND type = $2
       AND status = 'ALERTING'
     RETURNING *`,
    [device.device_uid, rule.type, value, threshold, message, telemetry.receivedAt]
  );
  if (existing.rowCount > 0) {
    return { created: false, row: existing.rows[0] };
  }
  const eventId = randomUUID();
  const inserted = await pool.query(
    `INSERT INTO ${alertsTable}
      (device_registry_id, device_uid, farm_id, cultivation_area_id, type, status,
       measured_value, threshold_value, message, started_at, last_observed_at,
       notification_event_id, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, 'ALERTING', $6, $7, $8, $9, $9, $10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
     RETURNING *`,
    [
      device.id,
      device.device_uid,
      device.farm_id,
      device.cultivation_area_id,
      rule.type,
      value,
      threshold,
      message,
      telemetry.receivedAt,
      eventId
    ]
  );
  return { created: true, row: inserted.rows[0] };
}

async function recoverAlert({ alertsTable, deviceUid, pool, recoveredAt, type }) {
  const result = await pool.query(
    `UPDATE ${alertsTable}
     SET status = 'RECOVERED',
         recovered_at = $3,
         last_observed_at = $3,
         updated_at = CURRENT_TIMESTAMP
     WHERE device_uid = $1
       AND type = $2
       AND status = 'ALERTING'
     RETURNING *`,
    [deviceUid, type, recoveredAt]
  );
  return result.rows[0] || null;
}

async function publishAlertNotification({ alert, device, farmAccessClient, logger = console, notificationPublisher, recovered }) {
  if (!notificationPublisher || !farmAccessClient || typeof farmAccessClient.alertRecipients !== "function") {
    return;
  }
  try {
    const recipients = await farmAccessClient.alertRecipients(device.farm_id, device.cultivation_area_id);
    await Promise.all(
      recipients.map((receiverId) =>
        notificationPublisher.publish({
          eventId: recovered ? randomUUID() : alert.notification_event_id,
          eventType: recovered ? "IOT_ALERT_RECOVERED" : alert.type,
          message: recovered ? `${device.name || device.device_uid} đã trở lại bình thường.` : alert.message,
          notificationType: "DEVICE",
          receiverId,
          title: recovered ? "IoT alert đã phục hồi" : "Cảnh báo IoT",
          metadata: {
            alertId: alert.id,
            alertType: alert.type,
            deviceId: device.id,
            deviceUid: device.device_uid,
            farmId: device.farm_id,
            cultivationAreaId: device.cultivation_area_id,
            status: alert.status
          },
          occurredAt: new Date().toISOString()
        })
      )
    );
  } catch (error) {
    logger.error("iot.alert.notificationPublishFailed", {
      alertId: alert.id,
      alertType: alert.type,
      deviceUid: device.device_uid,
      message: error.message
    });
  }
}

function alertMessage(type, device, value, threshold) {
  if (type === "DEVICE_OFFLINE") {
    return `${device.name || device.device_uid} mất kết nối quá ngưỡng ${threshold} giây.`;
  }
  return `${device.name || device.device_uid}: ${type} (${value} / ngưỡng ${threshold}).`;
}

function toIsoOrNull(value) {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

module.exports = {
  ALERT_STATUS,
  CONNECTIVITY_BASIS,
  connectivityForDevice,
  ensureAlertSchema,
  evaluateOfflineDevices,
  evaluateTelemetryAlerts
};
