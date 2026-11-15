"use strict";

const { createHash } = require("node:crypto");

/**
 * Delivery state of the in-app notifications of an alert episode, kept apart from the alert's own `status`
 * (ALERTING / ACKNOWLEDGED / RECOVERED). Each episode has two independent deliveries:
 *   OPEN      — "a threshold was breached / device went offline"   (columns notification_*)
 *   RECOVERED — "back to normal"                                     (columns recovery_notification_*)
 * Delivery status values:
 *   PENDING   — must be published; nobody has tried yet (or a crashed attempt whose lease expired)
 *   FAILED    — last attempt failed; retried at *_next_attempt_at with exponential backoff
 *   SENT      — Kafka acknowledged the events for every recipient
 *   ABANDONED — gave up after maxAttempts (kept for diagnosis, never retried automatically)
 *   SKIPPED   — intentionally not sent (test device, rule notify=false, no recipient, stale) — see *_detail
 *   NULL      — alert created before delivery tracking existed (never retried, and its recovery is not
 *               notified either: nothing proves the opening notification reached anyone)
 * Event ids are stable per (alert, phase, recipient), so a retry or a Kafka redelivery that the consumer
 * already processed is dropped by notification-service's unique sourceEventId.
 */
const PHASES = {
  OPEN: { prefix: "notification", label: "OPEN" },
  RECOVERED: { prefix: "recovery_notification", label: "RECOVERED" },
};
const DELIVERY_STATUSES = ["PENDING", "FAILED", "SENT", "ABANDONED", "SKIPPED"];
const RETRYABLE = ["PENDING", "FAILED"];

const DEFAULT_POLICY = Object.freeze({
  maxAttempts: 8,
  baseDelayMs: 30_000,
  maxDelayMs: 30 * 60_000,
  // While one attempt is in flight its row is invisible to the retry scan for this long.
  leaseMs: 120_000,
  batchSize: 50,
  // When set, notifications of is_test devices go to this receiver only (a test account); otherwise none.
  testReceiverId: "",
});

function policyFrom(partial) {
  return { ...DEFAULT_POLICY, ...(partial || {}) };
}

function deliveryStatements(alertsTable) {
  const statusCheck = (column) => `${column} IS NULL OR ${column} IN (${DELIVERY_STATUSES.map((s) => `'${s}'`).join(", ")})`;
  const statements = [];
  for (const { prefix } of Object.values(PHASES)) {
    statements.push(
      `ALTER TABLE ${alertsTable} ADD COLUMN IF NOT EXISTS ${prefix}_status VARCHAR(20)`,
      `ALTER TABLE ${alertsTable} ADD COLUMN IF NOT EXISTS ${prefix}_attempts INTEGER NOT NULL DEFAULT 0`,
      `ALTER TABLE ${alertsTable} ADD COLUMN IF NOT EXISTS ${prefix}_next_attempt_at TIMESTAMPTZ`,
      `ALTER TABLE ${alertsTable} ADD COLUMN IF NOT EXISTS ${prefix}_detail TEXT`,
      `ALTER TABLE ${alertsTable} ADD COLUMN IF NOT EXISTS ${prefix}_sent_at TIMESTAMPTZ`,
      `DO $$ BEGIN
         ALTER TABLE ${alertsTable} ADD CONSTRAINT ck_iot_alerts_${prefix}_status CHECK (${statusCheck(`${prefix}_status`)});
       EXCEPTION WHEN duplicate_object THEN NULL; END $$`,
      `CREATE INDEX IF NOT EXISTS idx_iot_alerts_${prefix}_due
         ON ${alertsTable} (${prefix}_next_attempt_at) WHERE ${prefix}_status IN ('PENDING', 'FAILED')`,
    );
  }
  return statements;
}

async function ensureAlertDeliverySchema(pool, alertsTable) {
  for (const sql of deliveryStatements(alertsTable)) await pool.query(sql);
}

/** RFC 4122 version-5-style UUID (SHA-1, URL-safe namespace-free) — same input, same id, forever. */
function stableEventId(alertId, phase, receiverId) {
  const hash = createHash("sha1").update(`duriancare.iot.alert|${alertId}|${phase}|${receiverId}`).digest();
  const bytes = Buffer.from(hash.subarray(0, 16));
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function backoffDelayMs(attempt, policy) {
  return Math.min(policy.maxDelayMs, policy.baseDelayMs * 2 ** Math.max(0, attempt - 1));
}

/**
 * Who receives notifications for this alert. Test devices never reach real farm accounts: only the
 * configured test receiver, or nobody (the episode is still stored).
 * Recipients are the people who hold READ_IOT *now* on the farm/zone where the alert happened (the scope
 * stored on the alert row), never the device's current scope: a device removed and claimed by another farm
 * between two attempts must not carry the previous owner's alert to the new owner.
 */
async function recipientsFor(alert, device, farmAccessClient, policy) {
  if (device && device.is_test === true) {
    return { recipients: policy.testReceiverId ? [policy.testReceiverId] : [], reason: "TEST_DEVICE" };
  }
  if (!alert.farm_id) {
    return { recipients: [], reason: "NO_ALERT_SCOPE" };
  }
  if (!farmAccessClient || typeof farmAccessClient.alertRecipients !== "function") {
    return { recipients: [], reason: "NO_RECIPIENT_LOOKUP" };
  }
  return { recipients: await farmAccessClient.alertRecipients(alert.farm_id, alert.cultivation_area_id || null), reason: "NO_RECIPIENTS" };
}

function buildEvents({ alert, device, phase, recipients, labels, titles }) {
  const recovered = phase === PHASES.RECOVERED.label;
  const name = device.name || device.device_uid;
  return recipients.map((receiverId) => ({
    eventId: stableEventId(alert.id, phase, receiverId),
    eventType: recovered ? "IOT_ALERT_RECOVERED" : alert.type,
    message: recovered
      ? `${name}: ${(labels[alert.type] || alert.type).toLowerCase()} đã trở lại bình thường.`
      : alert.message,
    notificationType: "DEVICE",
    receiverId,
    title: recovered ? "Đã trở lại bình thường" : (titles[alert.severity] || "Cảnh báo"),
    metadata: {
      alertId: alert.id,
      alertType: alert.type,
      deviceId: device.id,
      deviceUid: device.device_uid,
      farmId: alert.farm_id,
      cultivationAreaId: alert.cultivation_area_id || null,
      severity: alert.severity || null,
      status: alert.status,
    },
    occurredAt: new Date().toISOString(),
  }));
}

/** Record the outcome of one attempt; only rows still waiting for delivery are changed. */
async function recordAttempt({ pool, alertsTable, alertId, phase, outcome, detail, attempts, policy, now }) {
  const { prefix } = PHASES[phase];
  let status = outcome;
  let nextAt = null;
  if (outcome === "FAILED") {
    if (attempts >= policy.maxAttempts) status = "ABANDONED";
    else nextAt = new Date(now.getTime() + backoffDelayMs(attempts, policy));
  }
  await pool.query(
    `/* notification-delivery */ UPDATE ${alertsTable}
     SET ${prefix}_status = $2::varchar, ${prefix}_attempts = $3, ${prefix}_next_attempt_at = $4::timestamptz,
         ${prefix}_detail = $5::text,
         ${prefix}_sent_at = CASE WHEN $2::varchar = 'SENT' THEN $6::timestamptz ELSE ${prefix}_sent_at END
     WHERE id = $1 AND ${prefix}_status IN ('PENDING', 'FAILED')`,
    [alertId, status, attempts, nextAt, detail ? String(detail).slice(0, 500) : null, now]);
  return status;
}

/**
 * One delivery attempt for one phase of one alert. Never throws: the alert row is already committed and
 * the outcome is recorded for the retry scan.
 */
async function attemptDelivery({
  alert, device, phase, farmAccessClient, notificationPublisher, pool, alertsTable, policy, logger = console,
  labels = {}, titles = {}, now = new Date(),
}) {
  const { prefix } = PHASES[phase];
  const attempts = Number(alert[`${prefix}_attempts`] || 0) + 1;
  let outcome;
  let detail = null;
  let recipients = [];
  try {
    const resolved = await recipientsFor(alert, device, farmAccessClient, policy);
    recipients = resolved.recipients;
    if (recipients.length === 0) {
      outcome = "SKIPPED";
      detail = resolved.reason;
    } else if (!notificationPublisher) {
      outcome = "FAILED";
      detail = "notification publisher unavailable";
    } else {
      const events = buildEvents({ alert, device, phase, recipients, labels, titles });
      await Promise.all(events.map((event) => notificationPublisher.publish(event)));
      outcome = "SENT";
      detail = device.is_test === true ? "TEST_DEVICE_TEST_RECEIVER" : null;
    }
  } catch (error) {
    outcome = "FAILED";
    detail = error && error.message ? error.message : String(error);
  }
  const status = await recordAttempt({ pool, alertsTable, alertId: alert.id, phase, outcome, detail, attempts, policy, now });
  const log = status === "SENT" || status === "SKIPPED" ? logger.info : logger.error;
  (log || logger.info || console.log).call(logger, "iot.alert.notificationDelivery", {
    alertId: alert.id, alertType: alert.type, deviceUid: device.device_uid, phase, attempt: attempts, status,
    recipients: recipients.length, detail,
  });
  return { status, attempts, recipients: recipients.length, detail };
}

/** Initial delivery fields for a newly opened episode (stored by the same INSERT). */
function initialOpenDelivery({ device, notify, policy, now }) {
  if (notify === false) return { status: "SKIPPED", detail: "NOTIFY_DISABLED", nextAt: null };
  if (device && device.is_test === true && !policy.testReceiverId) return { status: "SKIPPED", detail: "TEST_DEVICE", nextAt: null };
  // The inline attempt follows immediately; the lease keeps the retry scan away from it meanwhile,
  // and picks it up if the process dies before recording the outcome.
  return { status: "PENDING", detail: null, nextAt: new Date(now.getTime() + policy.leaseMs) };
}

/**
 * Retry scan: claims due deliveries (PENDING whose lease expired, FAILED whose backoff elapsed) with
 * FOR UPDATE SKIP LOCKED, pushes their lease forward, then attempts them. Safe with several service
 * instances; a duplicate attempt is still harmless because event ids are stable.
 */
async function retryAlertNotifications({
  pool, alertsTable, devicesTable, farmAccessClient, notificationPublisher, policy: partial, logger = console,
  labels, titles, now = new Date(),
}) {
  const policy = policyFrom(partial);
  const summary = { attempted: 0, sent: 0, failed: 0, abandoned: 0, skipped: 0 };
  for (const phase of Object.keys(PHASES)) {
    const { prefix } = PHASES[phase];
    const claimed = (await pool.query(
      `/* notification-retry-claim */ UPDATE ${alertsTable} a
       SET ${prefix}_next_attempt_at = $2
       FROM (SELECT id FROM ${alertsTable}
             WHERE ${prefix}_status IN ('PENDING', 'FAILED') AND ${prefix}_next_attempt_at <= $1
             ORDER BY ${prefix}_next_attempt_at
             LIMIT $3
             FOR UPDATE SKIP LOCKED) due
       WHERE a.id = due.id
       RETURNING a.*`,
      [now, new Date(now.getTime() + policy.leaseMs), policy.batchSize])).rows;
    for (const alert of claimed) {
      summary.attempted += 1;
      const stale = phase === "OPEN" && alert.status === "RECOVERED";
      if (stale) {
        await recordAttempt({ pool, alertsTable, alertId: alert.id, phase, outcome: "SKIPPED", detail: "RECOVERED_BEFORE_DELIVERY",
          attempts: Number(alert[`${prefix}_attempts`] || 0), policy, now });
        summary.skipped += 1;
        continue;
      }
      const device = (await pool.query(
        `SELECT id, device_uid, name, farm_id, cultivation_area_id, status, is_test FROM ${devicesTable} WHERE id = $1`,
        [alert.device_registry_id])).rows[0] || {
        id: alert.device_registry_id, device_uid: alert.device_uid, name: null, farm_id: alert.farm_id,
        cultivation_area_id: alert.cultivation_area_id, is_test: false,
      };
      const result = await attemptDelivery({
        alert, device, phase, farmAccessClient, notificationPublisher, pool, alertsTable, policy, logger, labels, titles, now,
      });
      summary[{ SENT: "sent", FAILED: "failed", ABANDONED: "abandoned", SKIPPED: "skipped" }[result.status] || "failed"] += 1;
    }
  }
  if (summary.attempted > 0) logger.info("iot.alert.notificationRetry", summary);
  return summary;
}

module.exports = {
  DEFAULT_POLICY,
  DELIVERY_STATUSES,
  PHASES,
  RETRYABLE,
  attemptDelivery,
  backoffDelayMs,
  deliveryStatements,
  ensureAlertDeliverySchema,
  initialOpenDelivery,
  policyFrom,
  retryAlertNotifications,
  stableEventId,
};
