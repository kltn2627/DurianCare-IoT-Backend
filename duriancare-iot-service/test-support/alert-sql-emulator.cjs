// In-memory emulation of the iot_alerts statements issued by alert-engine.js / alertNotifications.js,
// with the same semantics as the real SQL (open episode = ALERTING or ACKNOWLEDGED, delivery CASE rules).
// Returns null for statements it does not recognise so a test pool can handle those itself.
const OPEN = ["ALERTING", "ACKNOWLEDGED"];
const RETRYABLE = ["PENDING", "FAILED"];

function alertSql(rows, sql, params = []) {
  const copy = (row) => ({ ...row });
  if (sql.includes("/* notification-delivery */")) {
    const prefix = sql.includes("SET recovery_notification_status") ? "recovery_notification" : "notification";
    const row = rows.find((r) => r.id === params[0] && RETRYABLE.includes(r[`${prefix}_status`]));
    if (!row) return { rowCount: 0, rows: [] };
    Object.assign(row, {
      [`${prefix}_status`]: params[1], [`${prefix}_attempts`]: params[2], [`${prefix}_next_attempt_at`]: params[3],
      [`${prefix}_detail`]: params[4],
    });
    if (params[1] === "SENT") row[`${prefix}_sent_at`] = params[5];
    return { rowCount: 1, rows: [] };
  }
  if (sql.includes("/* notification-retry-claim */")) {
    const prefix = sql.includes("SET recovery_notification_next_attempt_at") ? "recovery_notification" : "notification";
    const due = rows
      .filter((r) => RETRYABLE.includes(r[`${prefix}_status`]) && r[`${prefix}_next_attempt_at`]
        && new Date(r[`${prefix}_next_attempt_at`]) <= new Date(params[0]))
      .sort((a, b) => new Date(a[`${prefix}_next_attempt_at`]) - new Date(b[`${prefix}_next_attempt_at`]))
      .slice(0, params[2]);
    for (const row of due) row[`${prefix}_next_attempt_at`] = params[1];
    return { rowCount: due.length, rows: due.map(copy) };
  }
  if (/^\s*UPDATE/.test(sql) && sql.includes("SET status = 'RECOVERED'")) {
    const row = rows.find((r) => r.device_uid === params[0] && r.type === params[1] && OPEN.includes(r.status));
    if (!row) return { rowCount: 0, rows: [] };
    const reason = params[3] ?? null;
    const openDelivered = row.notification_status === "SENT";
    const pending = reason == null && openDelivered;
    Object.assign(row, {
      status: "RECOVERED", recovered_at: params[2], last_observed_at: params[2],
      recovery_notification_status: pending ? "PENDING" : "SKIPPED",
      recovery_notification_detail: reason != null ? reason
        : openDelivered ? null : row.notification_status == null ? "OPEN_UNTRACKED" : "OPEN_NOT_DELIVERED",
      recovery_notification_next_attempt_at: pending ? params[4] : null,
      recovery_notification_attempts: row.recovery_notification_attempts || 0,
    });
    if (RETRYABLE.includes(row.notification_status)) {
      Object.assign(row, { notification_status: "SKIPPED", notification_detail: "RECOVERED_BEFORE_DELIVERY", notification_next_attempt_at: null });
    }
    return { rowCount: 1, rows: [copy(row)] };
  }
  if (/^\s*UPDATE/.test(sql) && sql.includes("SET measured_value")) {
    const row = rows.find((r) => r.device_uid === params[0] && r.type === params[1] && OPEN.includes(r.status));
    if (!row) return { rowCount: 0, rows: [] };
    Object.assign(row, { measured_value: params[2], threshold_value: params[3], message: params[4], last_observed_at: params[5] });
    return { rowCount: 1, rows: [copy(row)] };
  }
  if (/^\s*INSERT INTO\s+\S*iot_alerts/.test(sql)) {
    if (rows.some((r) => r.device_uid === params[1] && r.type === params[4] && OPEN.includes(r.status))) {
      throw Object.assign(new Error("duplicate open episode"), { code: "23505" });
    }
    const row = {
      id: `alert-${rows.length + 1}`, device_registry_id: params[0], device_uid: params[1], farm_id: params[2],
      cultivation_area_id: params[3], type: params[4], status: "ALERTING", measured_value: params[5],
      threshold_value: params[6], message: params[7], started_at: params[8], last_observed_at: params[8],
      notification_event_id: params[9], severity: params[10], rule_id: params[11],
      notification_status: params[12], notification_next_attempt_at: params[13], notification_detail: params[14],
      notification_attempts: 0, recovery_notification_status: null, recovery_notification_attempts: 0,
    };
    rows.push(row);
    return { rowCount: 1, rows: [copy(row)] };
  }
  return null;
}

module.exports = { alertSql };
