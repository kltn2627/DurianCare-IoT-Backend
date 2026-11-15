const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  generateClaimCode, normalizeClaimCode, hashClaimCode, effectiveChecks, parseRuleBody, lifecycleStatements, toConfigResponse,
} = require("../src/devices/deviceLifecycle");
const { evaluateTelemetryAlerts } = require("../src/alert-engine");

const apiError = (status, message) => Object.assign(new Error(message), { status });

test("claim codes are 50-bit, unambiguous, normalized and bound to one device", () => {
  const codes = new Set(Array.from({ length: 500 }, () => generateClaimCode()));
  assert.equal(codes.size, 500);
  for (const code of codes) assert.match(code, /^[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}$/);
  assert.equal(normalizeClaimCode(" abcde-fghjk "), "ABCDEFGHJK");
  assert.equal(normalizeClaimCode("ABCDE-FGH0K"), normalizeClaimCode("abcde fghok")); // O read as 0
  assert.equal(normalizeClaimCode("too-short"), null);
  assert.equal(normalizeClaimCode(42), null);
  assert.notEqual(hashClaimCode("dev-a", "ABCDE-FGHJK"), hashClaimCode("dev-b", "ABCDE-FGHJK"));
  assert.equal(hashClaimCode("dev-a", "abcde fghjk"), hashClaimCode("dev-a", "ABCDE-FGHJK"));
});

const device = { id: "dev-1", device_uid: "esp32-01", farm_id: "farm-1", cultivation_area_id: "zone-a", status: "ACTIVE" };
const thresholds = { temperatureHigh: 38, temperatureLow: 15, humidityHigh: 95, humidityLow: 45, lightHigh: 4095, lightLow: 0 };
const rule = (over) => ({ id: over.id || "r", farm_id: "farm-1", cultivation_area_id: null, device_id: null, enabled: true,
  notify: true, hysteresis: 0, severity: "WARNING", min_value: null, max_value: null, ...over });

test("most specific rule wins per metric (device > zone > farm > default) and disabled rules switch a metric off", () => {
  const rules = [
    rule({ id: "farm-temp", metric: "TEMPERATURE", max_value: 36 }),
    rule({ id: "zone-temp", metric: "TEMPERATURE", cultivation_area_id: "zone-a", max_value: 35, severity: "CRITICAL" }),
    rule({ id: "other-zone", metric: "TEMPERATURE", cultivation_area_id: "zone-b", max_value: 30 }),
    rule({ id: "dev-soil", metric: "SOIL_MOISTURE", device_id: "dev-1", min_value: 55, max_value: 90, hysteresis: 3 }),
    rule({ id: "farm-soil", metric: "SOIL_MOISTURE", min_value: 40 }),
    rule({ id: "farm-hum-off", metric: "HUMIDITY", max_value: 90, enabled: false }),
  ];
  const { checks, disabledMetrics, explicit } = effectiveChecks(device, rules, thresholds);
  assert.equal(explicit, true);
  const byType = Object.fromEntries(checks.map((c) => [c.type, c]));
  assert.equal(byType.TEMPERATURE_HIGH.threshold, 35);
  assert.equal(byType.TEMPERATURE_HIGH.severity, "CRITICAL");
  assert.equal(byType.TEMPERATURE_LOW, undefined, "zone rule defines only an upper bound");
  assert.deepEqual([byType.SOIL_MOISTURE_LOW.threshold, byType.SOIL_MOISTURE_HIGH.threshold], [55, 90]);
  assert.equal(byType.SOIL_MOISTURE_LOW.field, "soilMoisture");
  assert.equal(byType.SOIL_MOISTURE_LOW.hysteresis, 3);
  assert.equal(byType.HUMIDITY_HIGH, undefined);
  assert.deepEqual(disabledMetrics, ["HUMIDITY"]);
  assert.equal(byType.LIGHT_HIGH.threshold, 4095, "service default still applies to metrics without rules");
  const none = effectiveChecks(device, [], thresholds);
  assert.equal(none.explicit, false);
  assert.equal(none.checks.find((c) => c.type === "TEMPERATURE_HIGH").threshold, 38);
  assert.equal(none.checks.some((c) => c.type.startsWith("SOIL")), false, "no default soil rule");
  const foreign = effectiveChecks({ ...device, farm_id: "farm-2" }, rules, thresholds);
  assert.equal(foreign.explicit, false, "rules of another farm never apply");
});

test("rule validation rejects bad metrics, ranges, hysteresis and flags", () => {
  const ok = parseRuleBody({ metric: "soil_moisture", minValue: 55, maxValue: 90, hysteresis: 3, severity: "critical" }, apiError);
  assert.deepEqual(ok, { metric: "SOIL_MOISTURE", min: 55, max: 90, hysteresis: 3, severity: "CRITICAL", enabled: true, notify: true });
  for (const bad of [
    { metric: "CO2", maxValue: 1 }, { metric: "HUMIDITY" }, { metric: "HUMIDITY", minValue: 80, maxValue: 70 },
    { metric: "HUMIDITY", maxValue: 120 }, { metric: "TEMPERATURE", maxValue: "hot" },
    { metric: "HUMIDITY", minValue: 40, maxValue: 50, hysteresis: 6 }, { metric: "HUMIDITY", maxValue: 90, hysteresis: -1 },
    { metric: "HUMIDITY", maxValue: 90, severity: "PANIC" }, { metric: "HUMIDITY", maxValue: 90, enabled: "yes" },
  ]) {
    assert.throws(() => parseRuleBody(bad, apiError), (e) => e.status === 400, JSON.stringify(bad));
  }
});

test("schema keeps claimed devices scoped to a farm and one open alert episode per device/type", () => {
  const sql = lifecycleStatements("duriancare_iot").join("\n");
  assert.match(sql, /provisioning_status <> 'CLAIMED' OR farm_id IS NOT NULL/);
  assert.match(sql, /uq_iot_device_claims_pending[\s\S]*WHERE status = 'PENDING'/);
  assert.match(sql, /WHERE status IN \('ALERTING', 'ACKNOWLEDGED'\)/);
  assert.match(sql, /ADD COLUMN IF NOT EXISTS provisioning_status VARCHAR\(20\) NOT NULL DEFAULT 'CLAIMED'/);
  assert.doesNotMatch(sql, /DROP TABLE|DELETE FROM|TRUNCATE/);
});

test("config response never claims a device applied a version it did not report", () => {
  const sensor = { id: "dev-1", device_type: "SENSOR" };
  assert.equal(toConfigResponse(sensor, null).deliveryStatus, "NOT_CONFIGURED");
  const pending = toConfigResponse(sensor, { publish_interval_seconds: 30, alerts_enabled: true, config_version: 2,
    delivery_status: "DELIVERED", applied_version: 1, delivered_at: new Date(), applied_at: new Date(), updated_at: new Date() });
  assert.equal(pending.deliveryStatus, "DELIVERED");
  assert.equal(pending.appliedVersion, 1);
  assert.equal(toConfigResponse({ id: "cam", device_type: "CAMERA" }, null).deviceSupportsRemoteConfig, false);
});

const { alertSql } = require("../test-support/alert-sql-emulator.cjs");

// ── Alert engine against an in-memory alerts table ──────────────────────────
function memoryAlerts() {
  const rows = [];
  const published = [];
  const pool = {
    async query(sql, params) {
      const handled = alertSql(rows, sql, params);
      if (handled) return handled;
      return { rowCount: 0, rows: [] };
    },
  };
  const farmAccessClient = { alertRecipients: async () => ["owner-1"] };
  const notificationPublisher = { publish: async (event) => { published.push(event); } };
  return { rows, published, pool, farmAccessClient, notificationPublisher };
}

const soilChecks = [{ field: "soilMoisture", kind: "LOW", type: "SOIL_MOISTURE_LOW", threshold: 55, hysteresis: 3,
  severity: "CRITICAL", notify: true, ruleId: "rule-1" }];

async function feed(env, soilMoisture, extra = {}) {
  return evaluateTelemetryAlerts({ alertsTable: "iot_alerts", device: { ...device, name: "Trạm A" }, checks: soilChecks,
    farmAccessClient: env.farmAccessClient, notificationPublisher: env.notificationPublisher, pool: env.pool,
    telemetry: { soilMoisture, receivedAt: new Date().toISOString() }, logger: { error() {}, info() {} }, ...extra });
}

test("an acknowledged alert stays the same episode: no duplicate alert or notification while the breach lasts", async () => {
  const env = memoryAlerts();
  await feed(env, 40);
  assert.equal(env.rows.length, 1);
  assert.equal(env.rows[0].severity, "CRITICAL");
  assert.equal(env.rows[0].rule_id, "rule-1");
  assert.equal(env.published.length, 1);
  assert.equal(env.published[0].title, "Cảnh báo nghiêm trọng");
  assert.match(env.published[0].message, /Trạm A: Đất quá khô \(40%, ngưỡng 55%\)/);
  env.rows[0].status = "ACKNOWLEDGED"; // farmer pressed "Đã xem"
  await feed(env, 41);
  await feed(env, 39);
  assert.equal(env.rows.length, 1, "no second alert after acknowledgement");
  assert.equal(env.published.length, 1, "no repeated notification");
  assert.equal(env.rows[0].measured_value, 39);
  await feed(env, 60);
  assert.equal(env.rows[0].status, "RECOVERED", "acknowledged alerts still resolve when conditions recover");
  assert.equal(env.published.at(-1).title, "Đã trở lại bình thường");
});

test("hysteresis: a reading just above the threshold does not resolve the episode", async () => {
  const env = memoryAlerts();
  await feed(env, 50);
  await feed(env, 56); // above 55 but inside the 3-point band
  assert.equal(env.rows[0].status, "ALERTING");
  await feed(env, 54); // dips again: same episode
  assert.equal(env.rows.length, 1);
  await feed(env, 58); // 55 + 3 reached
  assert.equal(env.rows[0].status, "RECOVERED");
  assert.equal(env.published.length, 2);
});

test("disabled rules close open alerts silently and notify:false never publishes", async () => {
  const env = memoryAlerts();
  await feed(env, 40);
  await feed(env, 40, { checks: [], disabledTypes: ["SOIL_MOISTURE_LOW", "SOIL_MOISTURE_HIGH"] });
  assert.equal(env.rows[0].status, "RECOVERED");
  assert.equal(env.published.length, 1, "closing because a rule was switched off is not announced as recovery");
  const quiet = memoryAlerts();
  await feed(quiet, 40, { checks: [{ ...soilChecks[0], notify: false }] });
  assert.equal(quiet.rows.length, 1);
  assert.equal(quiet.published.length, 0);
});
