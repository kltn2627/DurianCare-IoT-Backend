"use strict";

// Device lifecycle on top of the existing iot_devices registry:
//   provisioning (ADMIN registers a UID and gets a one-time claim code)
//   → claim + assignment (farmer proves possession with the code and picks farm/zone)
//   → connection status (derived from last_seen_at, unchanged).
// Plus per-device configuration (delivered over the HTTP ingestion response) and alert rules.
const crypto = require("node:crypto");

const DEVICE_TYPES = new Set(["SENSOR", "CAMERA"]);
const METRICS = {
  TEMPERATURE: { field: "temperature", unit: "°C", min: -20, max: 70 },
  HUMIDITY: { field: "humidity", unit: "%", min: 0, max: 100 },
  SOIL_MOISTURE: { field: "soilMoisture", unit: "%", min: 0, max: 100 },
  LIGHT: { field: "light", unit: "raw", min: 0, max: 100000 },
};
const SEVERITIES = new Set(["INFO", "WARNING", "CRITICAL"]);
// Crockford base32 without I, L, O, U: unambiguous when read aloud or typed from a label.
const CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const CODE_LENGTH = 10; // 50 bits of entropy
const MAX_CLAIM_ATTEMPTS = 5;
const CONFIG_LIMITS = { publishIntervalSeconds: [5, 3600] };

function tables(schema) {
  return {
    devices: `"${schema}".iot_devices`,
    claims: `"${schema}".iot_device_claims`,
    configs: `"${schema}".iot_device_configs`,
    rules: `"${schema}".iot_alert_rules`,
    alerts: `"${schema}".iot_alerts`,
    telemetry: `"${schema}".telemetry`,
  };
}

function lifecycleStatements(schema) {
  const t = tables(schema);
  return [
    `ALTER TABLE ${t.devices} ALTER COLUMN farm_id DROP NOT NULL`,
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS device_type VARCHAR(20) NOT NULL DEFAULT 'SENSOR'`,
    // Rows registered before claims existed were bound to a farm by its manager: they count as CLAIMED.
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS provisioning_status VARCHAR(20) NOT NULL DEFAULT 'CLAIMED'`,
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(40)`,
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS provisioned_by VARCHAR(150)`,
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(150)`,
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ`,
    // Test/simulated devices: their telemetry is kept but excluded from cross-device aggregates.
    `ALTER TABLE ${t.devices} ADD COLUMN IF NOT EXISTS is_test BOOLEAN NOT NULL DEFAULT FALSE`,
    `DO $$ BEGIN
       ALTER TABLE ${t.devices} ADD CONSTRAINT ck_iot_devices_type CHECK (device_type IN ('SENSOR', 'CAMERA'));
     EXCEPTION WHEN duplicate_object THEN NULL; END $$`,
    `DO $$ BEGIN
       ALTER TABLE ${t.devices} ADD CONSTRAINT ck_iot_devices_provisioning CHECK (provisioning_status IN ('PROVISIONED', 'CLAIMED'));
     EXCEPTION WHEN duplicate_object THEN NULL; END $$`,
    `DO $$ BEGIN
       ALTER TABLE ${t.devices} ADD CONSTRAINT ck_iot_devices_claimed_scope CHECK (provisioning_status <> 'CLAIMED' OR farm_id IS NOT NULL);
     EXCEPTION WHEN duplicate_object THEN NULL; END $$`,
    `CREATE TABLE IF NOT EXISTS ${t.claims} (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      device_id UUID NOT NULL REFERENCES ${t.devices}(id),
      code_hash CHAR(64) NOT NULL,
      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      expires_at TIMESTAMPTZ NOT NULL,
      failed_attempts INTEGER NOT NULL DEFAULT 0,
      created_by VARCHAR(150) NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      claimed_by VARCHAR(150),
      claimed_at TIMESTAMPTZ,
      CONSTRAINT ck_iot_device_claims_status CHECK (status IN ('PENDING', 'CLAIMED', 'REVOKED'))
    )`,
    `CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_device_claims_pending ON ${t.claims} (device_id) WHERE status = 'PENDING'`,
    `CREATE TABLE IF NOT EXISTS ${t.configs} (
      device_id UUID PRIMARY KEY REFERENCES ${t.devices}(id),
      publish_interval_seconds INTEGER NOT NULL DEFAULT 10,
      alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      config_version INTEGER NOT NULL DEFAULT 1,
      delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      delivered_at TIMESTAMPTZ,
      applied_version INTEGER,
      applied_at TIMESTAMPTZ,
      updated_by VARCHAR(150),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT ck_iot_device_configs_publish CHECK (publish_interval_seconds BETWEEN 5 AND 3600),
      CONSTRAINT ck_iot_device_configs_delivery CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'APPLIED'))
    )`,
    `CREATE TABLE IF NOT EXISTS ${t.rules} (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      farm_id VARCHAR(150) NOT NULL,
      cultivation_area_id VARCHAR(150),
      device_id UUID REFERENCES ${t.devices}(id),
      metric VARCHAR(30) NOT NULL,
      min_value DOUBLE PRECISION,
      max_value DOUBLE PRECISION,
      hysteresis DOUBLE PRECISION NOT NULL DEFAULT 0,
      severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
      enabled BOOLEAN NOT NULL DEFAULT TRUE,
      notify BOOLEAN NOT NULL DEFAULT TRUE,
      created_by VARCHAR(150) NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT ck_iot_alert_rules_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY', 'SOIL_MOISTURE', 'LIGHT')),
      CONSTRAINT ck_iot_alert_rules_bounds CHECK (
        (min_value IS NOT NULL OR max_value IS NOT NULL) AND (min_value IS NULL OR max_value IS NULL OR min_value < max_value)),
      CONSTRAINT ck_iot_alert_rules_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
      CONSTRAINT ck_iot_alert_rules_hysteresis CHECK (hysteresis >= 0)
    )`,
    `CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_alert_rules_scope ON ${t.rules}
       (farm_id, COALESCE(cultivation_area_id, ''), COALESCE(device_id::text, ''), metric)`,
    // Zone/farm attribution captured when the reading arrives (not re-derived after a device moves).
    `ALTER TABLE ${t.telemetry} ADD COLUMN IF NOT EXISTS farm_id VARCHAR(150)`,
    `ALTER TABLE ${t.telemetry} ADD COLUMN IF NOT EXISTS cultivation_area_id VARCHAR(150)`,
    `ALTER TABLE ${t.alerts} ADD COLUMN IF NOT EXISTS severity VARCHAR(20)`,
    `ALTER TABLE ${t.alerts} ADD COLUMN IF NOT EXISTS rule_id UUID`,
    // An acknowledged alert is still the same open episode: one active (ALERTING or ACKNOWLEDGED) row per device+type.
    `CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_alerts_open_device_type ON ${t.alerts} (device_uid, type)
       WHERE status IN ('ALERTING', 'ACKNOWLEDGED')`,
  ];
}

async function ensureDeviceLifecycleSchema(pool, schema) {
  for (const statement of lifecycleStatements(schema)) await pool.query(statement);
}

// ── Claim codes ──────────────────────────────────────────────────────────────

function generateClaimCode(randomBytes = crypto.randomBytes) {
  const bytes = randomBytes(CODE_LENGTH);
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[bytes[i] & 31];
  return `${code.slice(0, 5)}-${code.slice(5)}`;
}

function normalizeClaimCode(value) {
  if (typeof value !== "string") return null;
  const text = value.toUpperCase().replace(/[\s-]/g, "").replace(/O/g, "0").replace(/[IL]/g, "1");
  return /^[0-9A-HJKMNP-TV-Z]{10}$/.test(text) ? text : null;
}

function hashClaimCode(deviceId, code) {
  return crypto.createHash("sha256").update(`${deviceId}:${normalizeClaimCode(code)}`).digest("hex");
}

function sameHash(a, b) {
  const left = Buffer.from(String(a), "hex");
  const right = Buffer.from(String(b), "hex");
  return left.length === right.length && left.length > 0 && crypto.timingSafeEqual(left, right);
}

// ── Effective alert rules ────────────────────────────────────────────────────

const DEFAULT_RULES = (thresholds) => [
  { metric: "TEMPERATURE", min: thresholds.temperatureLow, max: thresholds.temperatureHigh },
  { metric: "HUMIDITY", min: thresholds.humidityLow, max: thresholds.humidityHigh },
  { metric: "LIGHT", min: thresholds.lightLow, max: thresholds.lightHigh },
];

/**
 * Per metric, the most specific rule wins: device > zone > farm > service default (env).
 * A disabled specific rule switches that metric off for the device. Returns threshold checks
 * in the alert engine's {field, kind, type, threshold, hysteresis, severity, notify, ruleId} shape.
 */
function effectiveChecks(device, storedRules, thresholds) {
  const rank = (rule) => (rule.device_id ? 3 : rule.cultivation_area_id ? 2 : 1);
  const applicable = storedRules.filter((rule) =>
    rule.farm_id === device.farm_id &&
    (!rule.cultivation_area_id || rule.cultivation_area_id === device.cultivation_area_id) &&
    (!rule.device_id || rule.device_id === device.id));
  const chosen = new Map();
  for (const rule of applicable) {
    const current = chosen.get(rule.metric);
    if (!current || rank(rule) > rank(current)) chosen.set(rule.metric, rule);
  }
  const checks = [];
  const add = (metric, min, max, extra) => {
    const field = METRICS[metric].field;
    if (Number.isFinite(max)) checks.push({ field, kind: "HIGH", type: `${metric}_HIGH`, threshold: max, ...extra });
    if (Number.isFinite(min)) checks.push({ field, kind: "LOW", type: `${metric}_LOW`, threshold: min, ...extra });
  };
  for (const metric of Object.keys(METRICS)) {
    const rule = chosen.get(metric);
    if (rule) {
      if (!rule.enabled) continue;
      add(metric, numberOrNull(rule.min_value), numberOrNull(rule.max_value), {
        hysteresis: Number(rule.hysteresis) || 0, severity: rule.severity, notify: rule.notify !== false, ruleId: rule.id });
      continue;
    }
    const fallback = DEFAULT_RULES(thresholds).find((d) => d.metric === metric);
    if (fallback) add(metric, fallback.min, fallback.max, { hysteresis: 0, severity: "WARNING", notify: true, ruleId: null });
  }
  return { checks, disabledMetrics: [...chosen.values()].filter((r) => !r.enabled).map((r) => r.metric), explicit: chosen.size > 0 };
}

async function loadRulesForDevice(pool, schema, device) {
  if (!device || !device.farm_id) return [];
  const { rules } = tables(schema);
  const result = await pool.query(
    `SELECT * FROM ${rules} WHERE farm_id = $1 AND (cultivation_area_id IS NULL OR cultivation_area_id = $2)
       AND (device_id IS NULL OR device_id = $3)`,
    [device.farm_id, device.cultivation_area_id, device.id]);
  return result.rows;
}

/** SQL condition on telemetry rows: the reporting device is neither a test device nor removed. */
function excludeTestAndDeletedDevices(devicesTable, telemetryTable) {
  return `NOT EXISTS (SELECT 1 FROM ${devicesTable} excluded
    WHERE LOWER(excluded.device_uid) = LOWER(${telemetryTable}.device_id)
      AND (excluded.is_test OR excluded.status = 'DELETED'))`;
}

// ── Configuration delivery (HTTP ingestion piggyback) ────────────────────────

/** Config the device has not applied yet, to return in the ingestion response; marks it DELIVERED. */
async function pendingConfigFor(pool, schema, device, reportedVersion) {
  if (!device) return null;
  const { configs } = tables(schema);
  const row = (await pool.query(`SELECT * FROM ${configs} WHERE device_id = $1`, [device.id])).rows[0];
  if (!row) return null;
  if (Number.isInteger(reportedVersion) && reportedVersion >= row.config_version) {
    if (row.applied_version !== row.config_version) {
      await pool.query(
        `UPDATE ${configs} SET delivery_status = 'APPLIED', applied_version = $2, applied_at = CURRENT_TIMESTAMP
         WHERE device_id = $1 AND config_version = $2`, [device.id, row.config_version]);
    }
    return null;
  }
  if (row.delivery_status === "PENDING") {
    await pool.query(
      `UPDATE ${configs} SET delivery_status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP
       WHERE device_id = $1 AND config_version = $2 AND delivery_status = 'PENDING'`, [device.id, row.config_version]);
  }
  return { version: row.config_version, publish_interval_seconds: row.publish_interval_seconds };
}

async function deviceAlertsEnabled(pool, schema, device) {
  if (!device) return false;
  const { configs } = tables(schema);
  const row = (await pool.query(`SELECT alerts_enabled FROM ${configs} WHERE device_id = $1`, [device.id])).rows[0];
  return row ? row.alerts_enabled !== false : true;
}

// ── HTTP API ─────────────────────────────────────────────────────────────────

function registerDeviceLifecycleApi(app, deps) {
  const { pool, schema, farmAccessClient, helpers, now = () => new Date(), claimTtlHours = 72 } = deps;
  const { apiError, requireActor, writeApiError, normalizeDeviceUid, requireFarmManagement, requireDeviceAccess,
    findDevice, toDeviceResponse, healthConfig } = helpers;
  const t = tables(schema);
  const isAdmin = (actor) => String(actor.role).toUpperCase() === "ADMIN";
  const requireAdmin = (actor) => { if (!isAdmin(actor)) throw apiError(403, "Only administrators can provision devices"); };

  async function issueCode(client, deviceId, actor) {
    const code = generateClaimCode();
    const expiresAt = new Date(now().getTime() + claimTtlHours * 3600_000);
    await client.query(`UPDATE ${t.claims} SET status = 'REVOKED' WHERE device_id = $1 AND status = 'PENDING'`, [deviceId]);
    await client.query(
      `INSERT INTO ${t.claims} (device_id, code_hash, expires_at, created_by) VALUES ($1, $2, $3, $4)`,
      [deviceId, hashClaimCode(deviceId, code), expiresAt.toISOString(), actor.userId]);
    return { claimCode: code, expiresAt: expiresAt.toISOString() };
  }

  async function inTransaction(work) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const result = await work(client);
      await client.query("COMMIT");
      return result;
    } catch (error) {
      await client.query("ROLLBACK").catch(() => {});
      throw error;
    } finally {
      client.release();
    }
  }

  const deviceColumns = `id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at,
    device_type, provisioning_status, firmware_version, claimed_at, last_wifi_rssi`;

  // UC79/UC83 — provision a device and issue its one-time claim code (shown once, stored hashed).
  app.post("/api/iot/devices/provision", async (request, response) => {
    try {
      const actor = requireActor(request);
      requireAdmin(actor);
      const body = request.body && typeof request.body === "object" ? request.body : {};
      const deviceUid = normalizeDeviceUid(body.deviceUid);
      const deviceType = String(body.deviceType || "SENSOR").toUpperCase();
      if (!DEVICE_TYPES.has(deviceType)) throw apiError(400, "deviceType must be SENSOR or CAMERA");
      const name = typeof body.name === "string" && body.name.trim() ? body.name.trim().slice(0, 160) : null;
      if (body.isTest !== undefined && typeof body.isTest !== "boolean") throw apiError(400, "isTest must be true or false");
      const isTest = body.isTest === true;
      const result = await inTransaction(async (client) => {
        const existing = (await client.query(
          `SELECT id, status FROM ${t.devices} WHERE LOWER(device_uid) = LOWER($1) FOR UPDATE`, [deviceUid])).rows[0];
        let device;
        if (existing && existing.status !== "DELETED") {
          throw apiError(409, "Device is already registered");
        } else if (existing) {
          // A removed device can be re-provisioned for a new owner; its history stays attached to its old scope.
          device = (await client.query(
            `UPDATE ${t.devices} SET name = $2, device_type = $3, status = 'ACTIVE', provisioning_status = 'PROVISIONED',
               farm_id = NULL, cultivation_area_id = NULL, claimed_by = NULL, claimed_at = NULL, provisioned_by = $4,
               is_test = $5, updated_at = CURRENT_TIMESTAMP WHERE id = $1 RETURNING ${deviceColumns}, is_test`,
            [existing.id, name, deviceType, actor.userId, isTest])).rows[0];
        } else {
          device = (await client.query(
            `INSERT INTO ${t.devices} (device_uid, name, farm_id, status, device_type, provisioning_status, provisioned_by, is_test)
             VALUES ($1, $2, NULL, 'ACTIVE', $3, 'PROVISIONED', $4, $5) RETURNING ${deviceColumns}, is_test`,
            [deviceUid, name, deviceType, actor.userId, isTest])).rows[0];
        }
        return { device, ...(await issueCode(client, device.id, actor)) };
      });
      response.status(201).json({ device: { ...toDeviceResponse(result.device, healthConfig), isTest: result.device.is_test === true },
        claimCode: result.claimCode, claimCodeExpiresAt: result.expiresAt });
    } catch (error) {
      writeApiError(response, error && error.code === "23505" ? apiError(409, "Device is already registered") : error);
    }
  });

  // Admin inventory of devices that are provisioned but not claimed yet.
  app.get("/api/iot/admin/devices", async (request, response) => {
    try {
      requireAdmin(requireActor(request));
      const rows = (await pool.query(
        `SELECT d.id, d.device_uid, d.name, d.device_type, d.provisioning_status, d.status, d.created_at,
                d.farm_id, d.cultivation_area_id, d.claimed_at, d.is_test, c.expires_at AS claim_expires_at, c.status AS claim_status
         FROM ${t.devices} d
         LEFT JOIN LATERAL (SELECT expires_at, status FROM ${t.claims} WHERE device_id = d.id
                            ORDER BY created_at DESC LIMIT 1) c ON TRUE
         WHERE d.status <> 'DELETED' ORDER BY d.created_at DESC LIMIT 200`)).rows;
      response.json({ devices: rows.map((row) => ({
        id: row.id, deviceUid: row.device_uid, name: row.name || row.device_uid, deviceType: row.device_type,
        provisioningStatus: row.provisioning_status, status: row.status, farmId: row.farm_id,
        cultivationAreaId: row.cultivation_area_id, claimedAt: iso(row.claimed_at), createdAt: iso(row.created_at),
        claimCodeStatus: row.claim_status || null, claimCodeExpiresAt: iso(row.claim_expires_at),
        isTest: row.is_test === true,
      })) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  // New claim code for a device that is still unclaimed (the old one is revoked).
  app.post("/api/iot/devices/:identifier/claim-code", async (request, response) => {
    try {
      const actor = requireActor(request);
      requireAdmin(actor);
      const result = await inTransaction(async (client) => {
        const device = (await client.query(
          `SELECT id, provisioning_status FROM ${t.devices}
           WHERE (id::text = $1 OR LOWER(device_uid) = LOWER($1)) AND status <> 'DELETED' FOR UPDATE`,
          [String(request.params.identifier || "").trim()])).rows[0];
        if (!device) throw apiError(404, "Device was not found");
        // A claimed device can get a fresh code only to (re)activate its device token/camera key
        // (POST /api/v1/public/devices/activate); it does not reopen the farm claim.
        const forActivation = request.body?.purpose === "ACTIVATION";
        if (device.provisioning_status !== "PROVISIONED" && !forActivation) throw apiError(409, "Device is already claimed");
        return issueCode(client, device.id, actor);
      });
      response.status(201).json({ claimCode: result.claimCode, claimCodeExpiresAt: result.expiresAt });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  // UC82/UC86 — claim with the code and assign to a farm/zone the actor manages.
  app.post("/api/iot/devices/claim", async (request, response) => {
    try {
      const actor = requireActor(request);
      const body = request.body && typeof request.body === "object" ? request.body : {};
      const deviceUid = normalizeDeviceUid(body.deviceUid);
      const code = normalizeClaimCode(body.claimCode);
      if (!code) throw apiError(400, "claimCode is invalid");
      const farmId = typeof body.farmId === "string" && body.farmId.trim() ? body.farmId.trim() : null;
      if (!farmId) throw apiError(400, "farmId is required");
      const areaId = typeof body.cultivationAreaId === "string" && body.cultivationAreaId.trim()
        ? body.cultivationAreaId.trim() : null;
      const name = typeof body.name === "string" && body.name.trim() ? body.name.trim().slice(0, 160) : null;
      // Ownership of the target scope is checked before the code is touched (no attempt is burnt on a 403).
      await requireFarmManagement(farmAccessClient, actor, farmId, areaId);
      const invalid = () => apiError(400, "Device UID or claim code is not valid");
      const claimed = await inTransaction(async (client) => {
        const device = (await client.query(
          `SELECT id, provisioning_status FROM ${t.devices}
           WHERE LOWER(device_uid) = LOWER($1) AND status <> 'DELETED' FOR UPDATE`, [deviceUid])).rows[0];
        if (!device) throw invalid();
        const hash = hashClaimCode(device.id, code);
        const pending = (await client.query(
          `SELECT * FROM ${t.claims} WHERE device_id = $1 AND status = 'PENDING' FOR UPDATE`, [device.id])).rows[0];
        if (!pending) {
          const used = (await client.query(
            `SELECT code_hash FROM ${t.claims} WHERE device_id = $1 AND status = 'CLAIMED'`, [device.id])).rows;
          if (device.provisioning_status === "CLAIMED" && used.some((row) => sameHash(row.code_hash, hash))) {
            throw apiError(409, "Device is already claimed");
          }
          throw invalid();
        }
        if (pending.failed_attempts >= MAX_CLAIM_ATTEMPTS) {
          throw apiError(429, "Too many wrong claim codes; ask for a new code");
        }
        if (!sameHash(pending.code_hash, hash)) {
          await client.query(`UPDATE ${t.claims} SET failed_attempts = failed_attempts + 1 WHERE id = $1`, [pending.id]);
          return { error: invalid() };
        }
        if (new Date(pending.expires_at).getTime() <= now().getTime()) {
          throw apiError(410, "Claim code has expired; ask for a new code");
        }
        const updated = (await client.query(
          `UPDATE ${t.devices} SET farm_id = $2, cultivation_area_id = $3, provisioning_status = 'CLAIMED',
             claimed_by = $4, claimed_at = CURRENT_TIMESTAMP, name = COALESCE($5, name), status = 'ACTIVE',
             updated_at = CURRENT_TIMESTAMP
           WHERE id = $1 AND provisioning_status = 'PROVISIONED' RETURNING ${deviceColumns}`,
          [device.id, farmId, areaId, actor.userId, name])).rows[0];
        if (!updated) throw apiError(409, "Device is already claimed");
        await client.query(
          `UPDATE ${t.claims} SET status = 'CLAIMED', claimed_by = $2, claimed_at = CURRENT_TIMESTAMP WHERE id = $1`,
          [pending.id, actor.userId]);
        return { device: updated };
      });
      if (claimed.error) throw claimed.error; // the failed-attempt counter above is committed
      response.status(200).json({ device: toDeviceResponse(claimed.device, healthConfig) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  // UC81/UC85 — device configuration (read: farm readers; write: farm managers).
  async function loadConfig(device) {
    const row = (await pool.query(`SELECT * FROM ${t.configs} WHERE device_id = $1`, [device.id])).rows[0];
    return toConfigResponse(device, row);
  }

  app.get("/api/iot/devices/:identifier/config", async (request, response) => {
    try {
      const actor = requireActor(request);
      const device = await findDevice(request.params.identifier);
      await requireDeviceAccess(farmAccessClient, actor, device);
      response.json({ config: await loadConfig(device) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.put("/api/iot/devices/:identifier/config", async (request, response) => {
    try {
      const actor = requireActor(request);
      const device = await findDevice(request.params.identifier);
      await requireFarmManagement(farmAccessClient, actor, device.farm_id, device.cultivation_area_id);
      const body = request.body && typeof request.body === "object" ? request.body : {};
      const publish = Number(body.publishIntervalSeconds);
      const [lo, hi] = CONFIG_LIMITS.publishIntervalSeconds;
      if (!Number.isInteger(publish) || publish < lo || publish > hi) {
        throw apiError(400, `publishIntervalSeconds must be an integer between ${lo} and ${hi}`);
      }
      if (typeof body.alertsEnabled !== "boolean") throw apiError(400, "alertsEnabled must be true or false");
      const current = (await pool.query(`SELECT * FROM ${t.configs} WHERE device_id = $1`, [device.id])).rows[0];
      // Only a change of what the device itself applies bumps the version and is (re)delivered.
      const deviceChanged = !current || current.publish_interval_seconds !== publish;
      await pool.query(
        `INSERT INTO ${t.configs} (device_id, publish_interval_seconds, alerts_enabled, config_version, delivery_status, updated_by)
         VALUES ($1, $2, $3, 1, 'PENDING', $4)
         ON CONFLICT (device_id) DO UPDATE SET
           publish_interval_seconds = EXCLUDED.publish_interval_seconds,
           alerts_enabled = EXCLUDED.alerts_enabled,
           config_version = ${t.configs}.config_version + CASE WHEN $5 THEN 1 ELSE 0 END,
           delivery_status = CASE WHEN $5 THEN 'PENDING' ELSE ${t.configs}.delivery_status END,
           delivered_at = CASE WHEN $5 THEN NULL ELSE ${t.configs}.delivered_at END,
           updated_by = EXCLUDED.updated_by, updated_at = CURRENT_TIMESTAMP`,
        [device.id, publish, body.alertsEnabled, actor.userId, deviceChanged]);
      response.json({ config: await loadConfig(device) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  // UC85 — alert rules (thresholds) per farm / zone / device.
  app.get("/api/iot/alert-rules", async (request, response) => {
    try {
      const actor = requireActor(request);
      const farmId = typeof request.query.farmId === "string" ? request.query.farmId.trim() : "";
      if (!farmId) throw apiError(400, "farmId is required");
      const areaId = typeof request.query.cultivationAreaId === "string" && request.query.cultivationAreaId.trim()
        ? request.query.cultivationAreaId.trim() : null;
      if (!(await farmAccessClient.canReadIot(actor, farmId, areaId))) throw apiError(403, "Access denied for IoT alert rules");
      const rows = (await pool.query(
        `SELECT * FROM ${t.rules} WHERE farm_id = $1 AND ($2::text IS NULL OR cultivation_area_id IS NULL OR cultivation_area_id = $2)
         ORDER BY metric, cultivation_area_id NULLS FIRST, device_id NULLS FIRST`, [farmId, areaId])).rows;
      response.json({ rules: rows.map(toRuleResponse), defaults: defaultRulesResponse(deps.thresholds) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  async function resolveRuleScope(actor, body) {
    const farmId = typeof body.farmId === "string" && body.farmId.trim() ? body.farmId.trim() : null;
    if (!farmId) throw apiError(400, "farmId is required");
    const areaId = typeof body.cultivationAreaId === "string" && body.cultivationAreaId.trim() ? body.cultivationAreaId.trim() : null;
    let deviceId = null;
    if (body.deviceId) {
      const device = await findDevice(String(body.deviceId));
      if (device.farm_id !== farmId || (areaId && device.cultivation_area_id !== areaId)) {
        throw apiError(400, "deviceId does not belong to the given farm/zone");
      }
      deviceId = device.id;
    }
    await requireFarmManagement(farmAccessClient, actor, farmId, areaId);
    return { farmId, areaId, deviceId };
  }

  app.post("/api/iot/alert-rules", async (request, response) => {
    try {
      const actor = requireActor(request);
      const body = request.body && typeof request.body === "object" ? request.body : {};
      const scope = await resolveRuleScope(actor, body);
      const rule = parseRuleBody(body, apiError);
      const row = (await pool.query(
        `INSERT INTO ${t.rules} (farm_id, cultivation_area_id, device_id, metric, min_value, max_value, hysteresis,
           severity, enabled, notify, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING *`,
        [scope.farmId, scope.areaId, scope.deviceId, rule.metric, rule.min, rule.max, rule.hysteresis,
          rule.severity, rule.enabled, rule.notify, actor.userId])).rows[0];
      response.status(201).json({ rule: toRuleResponse(row) });
    } catch (error) {
      writeApiError(response, error && error.code === "23505"
        ? apiError(409, "A rule for this metric already exists in this scope") : error);
    }
  });

  async function findRuleForManagement(actor, id) {
    const row = (await pool.query(`SELECT * FROM ${t.rules} WHERE id::text = $1`, [String(id || "").trim()])).rows[0];
    if (!row) throw apiError(404, "Alert rule was not found");
    await requireFarmManagement(farmAccessClient, actor, row.farm_id, row.cultivation_area_id);
    return row;
  }

  app.patch("/api/iot/alert-rules/:id", async (request, response) => {
    try {
      const actor = requireActor(request);
      const current = await findRuleForManagement(actor, request.params.id);
      const body = request.body && typeof request.body === "object" ? request.body : {};
      const rule = parseRuleBody({
        metric: current.metric,
        minValue: "minValue" in body ? body.minValue : current.min_value,
        maxValue: "maxValue" in body ? body.maxValue : current.max_value,
        hysteresis: "hysteresis" in body ? body.hysteresis : current.hysteresis,
        severity: body.severity ?? current.severity,
        enabled: "enabled" in body ? body.enabled : current.enabled,
        notify: "notify" in body ? body.notify : current.notify,
      }, apiError);
      const row = (await pool.query(
        `UPDATE ${t.rules} SET min_value = $2, max_value = $3, hysteresis = $4, severity = $5, enabled = $6, notify = $7,
           updated_at = CURRENT_TIMESTAMP WHERE id = $1 RETURNING *`,
        [current.id, rule.min, rule.max, rule.hysteresis, rule.severity, rule.enabled, rule.notify])).rows[0];
      response.json({ rule: toRuleResponse(row) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.delete("/api/iot/alert-rules/:id", async (request, response) => {
    try {
      const actor = requireActor(request);
      const current = await findRuleForManagement(actor, request.params.id);
      await pool.query(`DELETE FROM ${t.rules} WHERE id = $1`, [current.id]);
      response.json({ deleted: true, id: current.id });
    } catch (error) {
      writeApiError(response, error);
    }
  });
}

function parseRuleBody(body, apiError) {
  const metric = String(body.metric || "").toUpperCase();
  if (!METRICS[metric]) throw apiError(400, "metric must be TEMPERATURE, HUMIDITY, SOIL_MOISTURE or LIGHT");
  const bound = (value, name) => {
    if (value === null || value === undefined || value === "") return null;
    const n = Number(value);
    const { min, max } = METRICS[metric];
    if (!Number.isFinite(n) || n < min || n > max) throw apiError(400, `${name} must be between ${min} and ${max}`);
    return n;
  };
  const minValue = bound(body.minValue, "minValue");
  const maxValue = bound(body.maxValue, "maxValue");
  if (minValue === null && maxValue === null) throw apiError(400, "minValue or maxValue is required");
  if (minValue !== null && maxValue !== null && minValue >= maxValue) throw apiError(400, "minValue must be lower than maxValue");
  const hysteresis = body.hysteresis === undefined || body.hysteresis === null || body.hysteresis === "" ? 0 : Number(body.hysteresis);
  if (!Number.isFinite(hysteresis) || hysteresis < 0) throw apiError(400, "hysteresis must be zero or positive");
  if (minValue !== null && maxValue !== null && hysteresis * 2 >= maxValue - minValue) {
    throw apiError(400, "hysteresis is too large for this range");
  }
  const severity = String(body.severity || "WARNING").toUpperCase();
  if (!SEVERITIES.has(severity)) throw apiError(400, "severity must be INFO, WARNING or CRITICAL");
  const flag = (value, name, fallback) => {
    if (value === undefined) return fallback;
    if (typeof value !== "boolean") throw apiError(400, `${name} must be true or false`);
    return value;
  };
  return { metric, min: minValue, max: maxValue, hysteresis, severity,
    enabled: flag(body.enabled, "enabled", true), notify: flag(body.notify, "notify", true) };
}

function toRuleResponse(row) {
  return {
    id: row.id, farmId: row.farm_id, cultivationAreaId: row.cultivation_area_id, deviceId: row.device_id,
    metric: row.metric, unit: METRICS[row.metric]?.unit ?? null,
    minValue: numberOrNull(row.min_value), maxValue: numberOrNull(row.max_value), hysteresis: Number(row.hysteresis) || 0,
    severity: row.severity, enabled: row.enabled, notify: row.notify,
    scope: row.device_id ? "DEVICE" : row.cultivation_area_id ? "ZONE" : "FARM",
    updatedAt: iso(row.updated_at),
  };
}

function defaultRulesResponse(thresholds) {
  return DEFAULT_RULES(thresholds || {}).map((rule) => ({
    metric: rule.metric, unit: METRICS[rule.metric].unit,
    minValue: Number.isFinite(rule.min) ? rule.min : null, maxValue: Number.isFinite(rule.max) ? rule.max : null,
  }));
}

function toConfigResponse(device, row) {
  return {
    deviceId: device.id,
    publishIntervalSeconds: row ? row.publish_interval_seconds : null,
    alertsEnabled: row ? row.alerts_enabled : true,
    configVersion: row ? row.config_version : null,
    // PENDING: saved, not yet sent · DELIVERED: sent in an ingestion response · APPLIED: device reported this version.
    deliveryStatus: row ? row.delivery_status : "NOT_CONFIGURED",
    deliveredAt: row ? iso(row.delivered_at) : null,
    appliedVersion: row ? row.applied_version : null,
    appliedAt: row ? iso(row.applied_at) : null,
    updatedAt: row ? iso(row.updated_at) : null,
    deviceSupportsRemoteConfig: (device.device_type || "SENSOR") === "SENSOR",
  };
}

function numberOrNull(value) {
  if (value === null || value === undefined) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function iso(value) {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

module.exports = {
  METRICS, MAX_CLAIM_ATTEMPTS,
  lifecycleStatements, ensureDeviceLifecycleSchema,
  generateClaimCode, normalizeClaimCode, hashClaimCode,
  effectiveChecks, excludeTestAndDeletedDevices, loadRulesForDevice, pendingConfigFor, deviceAlertsEnabled,
  registerDeviceLifecycleApi, parseRuleBody, toConfigResponse,
};
