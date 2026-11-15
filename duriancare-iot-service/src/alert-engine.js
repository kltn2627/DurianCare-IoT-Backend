const { randomUUID } = require("node:crypto");
const {
  attemptDelivery,
  ensureAlertDeliverySchema,
  initialOpenDelivery,
  policyFrom,
  retryAlertNotifications
} = require("./alertNotifications");

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
  await ensureAlertDeliverySchema(pool, alertsTable);
}

const ALERT_LABELS = {
  TEMPERATURE_HIGH: "Nhiệt độ cao", TEMPERATURE_LOW: "Nhiệt độ thấp",
  HUMIDITY_HIGH: "Độ ẩm không khí cao", HUMIDITY_LOW: "Độ ẩm không khí thấp",
  SOIL_MOISTURE_HIGH: "Đất quá ẩm", SOIL_MOISTURE_LOW: "Đất quá khô",
  LIGHT_HIGH: "Ánh sáng mạnh", LIGHT_LOW: "Ánh sáng yếu",
  DEVICE_OFFLINE: "Thiết bị mất kết nối",
};
const SEVERITY_TITLES = { CRITICAL: "Cảnh báo nghiêm trọng", WARNING: "Cảnh báo", INFO: "Thông báo" };

/** Service-default checks (env thresholds), used when no per-farm/zone/device rule applies. */
function defaultChecks(thresholds) {
  return THRESHOLD_RULES.map((rule) => ({
    ...rule, threshold: thresholds[rule.thresholdKey], hysteresis: 0, severity: "WARNING", notify: true, ruleId: null
  }));
}

/**
 * `checks` (from deviceLifecycle.effectiveChecks) take precedence over raw `thresholds`.
 * `disabledTypes` are alert types switched off by a rule: their open alerts are closed silently.
 * An ACKNOWLEDGED alert is still the open episode: it is touched while the breach lasts and
 * recovers like an ALERTING one, so acknowledging never spawns a duplicate alert/notification.
 * Recovery needs the value back inside the threshold by the rule's hysteresis.
 */
async function evaluateTelemetryAlerts({
  alertsTable,
  checks,
  device,
  disabledTypes = [],
  farmAccessClient,
  logger = console,
  notificationPolicy,
  notificationPublisher,
  now = new Date(),
  pool,
  telemetry,
  thresholds
}) {
  if (!device || device.status !== "ACTIVE") {
    return { created: [], recovered: [] };
  }
  const policy = policyFrom(notificationPolicy);
  const deliver = (alert, phase) => attemptDelivery({
    alert, device, phase, farmAccessClient, notificationPublisher, pool, alertsTable, policy, logger,
    labels: ALERT_LABELS, titles: SEVERITY_TITLES, now
  });
  const activeChecks = Array.isArray(checks) ? checks : defaultChecks(thresholds || {});
  const created = [];
  const recovered = [];
  const offlineAlert = await recoverAlert({
    alertsTable,
    device,
    now,
    policy,
    pool,
    recoveredAt: telemetry.receivedAt,
    type: "DEVICE_OFFLINE"
  });
  if (offlineAlert) {
    recovered.push(offlineAlert);
    if (offlineAlert.recovery_notification_status === "PENDING") await deliver(offlineAlert, "RECOVERED");
  }
  for (const type of disabledTypes) {
    const closed = await recoverAlert({
      alertsTable, device, now, policy, pool, recoveredAt: telemetry.receivedAt, type, skipReason: "RULE_DISABLED"
    });
    if (closed) recovered.push(closed);
  }
  for (const rule of activeChecks) {
    const value = telemetry[rule.field];
    const threshold = rule.threshold;
    if (!Number.isFinite(value) || !Number.isFinite(threshold)) continue;
    const violated = rule.kind === "HIGH" ? value > threshold : value < threshold;
    const hysteresis = Number(rule.hysteresis) || 0;
    const backToNormal = rule.kind === "HIGH" ? value <= threshold - hysteresis : value >= threshold + hysteresis;
    if (violated) {
      const alert = await openOrTouchAlert({
        alertsTable,
        device,
        now,
        policy,
        pool,
        rule,
        telemetry,
        threshold,
        value
      });
      if (alert.created) {
        created.push(alert.row);
        if (alert.row.notification_status === "PENDING") await deliver(alert.row, "OPEN");
      }
    } else if (backToNormal) {
      const alert = await recoverAlert({
        alertsTable,
        device,
        now,
        policy,
        pool,
        recoveredAt: telemetry.receivedAt,
        skipReason: rule.notify === false ? "NOTIFY_DISABLED" : null,
        type: rule.type
      });
      if (alert) {
        recovered.push(alert);
        if (alert.recovery_notification_status === "PENDING") await deliver(alert, "RECOVERED");
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
  notificationPolicy,
  notificationPublisher,
  pool,
  devicesTable,
  now = new Date()
}) {
  const policy = policyFrom(notificationPolicy);
  const result = await pool.query(
    `SELECT id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, is_test
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
    const deliver = (alert, phase) => attemptDelivery({
      alert, device, phase, farmAccessClient, notificationPublisher, pool, alertsTable, policy, logger,
      labels: ALERT_LABELS, titles: SEVERITY_TITLES, now
    });
    if (device.status === "ACTIVE" && connectivity.status === "OFFLINE") {
      const alert = await openOrTouchAlert({
        alertsTable,
        device,
        now,
        policy,
        pool,
        rule: { field: "last_seen_at", type: "DEVICE_OFFLINE" },
        telemetry: { receivedAt: now.toISOString() },
        threshold: connectivity.offlineAfterSeconds,
        value: connectivity.ageSeconds
      });
      if (alert.created) {
        created.push(alert.row);
        if (alert.row.notification_status === "PENDING") await deliver(alert.row, "OPEN");
      }
    } else {
      const alert = await recoverAlert({
        alertsTable,
        device,
        now,
        policy,
        pool,
        recoveredAt: now.toISOString(),
        type: "DEVICE_OFFLINE"
      });
      if (alert) {
        recovered.push(alert);
        if (alert.recovery_notification_status === "PENDING") await deliver(alert, "RECOVERED");
      }
    }
  }
  logger.info("iot.offlineWatchdog.devicesEvaluated", { evaluated });
  return { created, evaluated, recovered };
}

async function openOrTouchAlert({ alertsTable, device, now = new Date(), policy, pool, rule, telemetry, threshold, value }) {
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
       AND status IN ('ALERTING', 'ACKNOWLEDGED')
     RETURNING *`,
    [device.device_uid, rule.type, value, threshold, message, telemetry.receivedAt]
  );
  if (existing.rowCount > 0) {
    return { created: false, row: existing.rows[0] };
  }
  const eventId = randomUUID();
  const delivery = initialOpenDelivery({ device, notify: rule.notify, policy: policyFrom(policy), now });
  const inserted = await pool.query(
    `INSERT INTO ${alertsTable}
      (device_registry_id, device_uid, farm_id, cultivation_area_id, type, status,
       measured_value, threshold_value, message, started_at, last_observed_at,
       notification_event_id, severity, rule_id, created_at, updated_at,
       notification_status, notification_next_attempt_at, notification_detail)
     VALUES ($1, $2, $3, $4, $5, 'ALERTING', $6, $7, $8, $9, $9, $10, $11, $12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       $13, $14, $15)
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
      eventId,
      rule.severity || (rule.type === "DEVICE_OFFLINE" ? "WARNING" : null),
      rule.ruleId || null,
      delivery.status,
      delivery.nextAt,
      delivery.detail
    ]
  );
  return { created: true, row: inserted.rows[0] };
}

/**
 * Closes the open episode. In the same statement it decides the recovery notification:
 * PENDING only when the opening notification was delivered (SENT) or predates delivery tracking (NULL);
 * otherwise SKIPPED, so nobody gets "back to normal" for an alert they never saw. An opening
 * notification still waiting for delivery is cancelled (SKIPPED) since it is stale now.
 */
async function recoverAlert({ alertsTable, device, now = new Date(), policy, pool, recoveredAt, skipReason = null, type }) {
  const effectivePolicy = policyFrom(policy);
  const reason = skipReason || (device && device.is_test === true && !effectivePolicy.testReceiverId ? "TEST_DEVICE" : null);
  const lease = new Date(now.getTime() + effectivePolicy.leaseMs);
  const result = await pool.query(
    `UPDATE ${alertsTable}
     SET status = 'RECOVERED',
         recovered_at = $3,
         last_observed_at = $3,
         updated_at = CURRENT_TIMESTAMP,
         -- "Back to normal" only follows an opening notification known to be delivered. Untracked (NULL,
         -- created before delivery tracking) openings may never have reached anyone, so they are not followed up.
         recovery_notification_status = CASE
           WHEN $4::text IS NOT NULL THEN 'SKIPPED'
           WHEN notification_status = 'SENT' THEN 'PENDING'
           ELSE 'SKIPPED' END,
         recovery_notification_detail = CASE
           WHEN $4::text IS NOT NULL THEN $4::text
           WHEN notification_status = 'SENT' THEN NULL
           WHEN notification_status IS NULL THEN 'OPEN_UNTRACKED'
           ELSE 'OPEN_NOT_DELIVERED' END,
         recovery_notification_next_attempt_at = CASE
           WHEN $4::text IS NULL AND notification_status = 'SENT' THEN $5::timestamptz
           ELSE NULL END,
         notification_status = CASE WHEN notification_status IN ('PENDING', 'FAILED') THEN 'SKIPPED' ELSE notification_status END,
         notification_detail = CASE WHEN notification_status IN ('PENDING', 'FAILED') THEN 'RECOVERED_BEFORE_DELIVERY'
           ELSE notification_detail END,
         notification_next_attempt_at = CASE WHEN notification_status IN ('PENDING', 'FAILED') THEN NULL
           ELSE notification_next_attempt_at END
     WHERE device_uid = $1
       AND type = $2
       AND status IN ('ALERTING', 'ACKNOWLEDGED')
     RETURNING *`,
    [device.device_uid, type, recoveredAt, reason, lease]
  );
  return result.rows[0] || null;
}

function alertMessage(type, device, value, threshold) {
  const name = device.name || device.device_uid;
  if (type === "DEVICE_OFFLINE") {
    return `${name} mất kết nối hơn ${Math.round(threshold / 60)} phút.`;
  }
  const unit = /^(HUMIDITY|SOIL_MOISTURE)/.test(type) ? "%" : /^TEMPERATURE/.test(type) ? "°C" : "";
  return `${name}: ${ALERT_LABELS[type] || type} (${value}${unit}, ngưỡng ${threshold}${unit}).`;
}

function toIsoOrNull(value) {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** Retry scan for alert notifications (runs with the offline watchdog tick). */
function retryNotifications(args) {
  return retryAlertNotifications({ ...args, labels: ALERT_LABELS, titles: SEVERITY_TITLES });
}

module.exports = {
  ALERT_LABELS,
  ALERT_STATUS,
  defaultChecks,
  CONNECTIVITY_BASIS,
  connectivityForDevice,
  ensureAlertSchema,
  evaluateOfflineDevices,
  evaluateTelemetryAlerts,
  retryNotifications
};
