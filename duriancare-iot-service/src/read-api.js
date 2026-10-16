const MAX_HISTORY_LIMIT = 1000;
const DEFAULT_HISTORY_LIMIT = 288;
const DEVICE_UID_PATTERN = /^[A-Za-z0-9._:-]{3,150}$/;
const DEVICE_STATUSES = new Set(["ACTIVE", "INACTIVE", "MAINTENANCE"]);
const { connectivityForDevice } = require("./alert-engine");

function registerReadApi(app, dependencies) {
  const {
    alertsTable,
    devicesTable,
    farmAccessClient,
    healthConfig,
    pool,
    telemetryTable
  } = dependencies;

  app.get("/api/iot/devices", async (request, response) => {
    try {
      const actor = requireActor(request);
      const result = await pool.query(
        `SELECT
          d.id,
          d.device_uid,
          d.name,
          d.farm_id,
          d.cultivation_area_id,
          d.status,
          d.last_seen_at,
          d.created_at,
          d.updated_at,
          latest.id AS latest_id,
          latest.temperature,
          latest.humidity,
          latest.light,
          latest."timestamp" AS latest_timestamp,
          latest.received_at AS latest_received_at
        FROM ${devicesTable} d
        LEFT JOIN LATERAL (
          SELECT id, temperature, humidity, light, "timestamp", received_at
          FROM ${telemetryTable}
          WHERE device_id = d.device_uid
          ORDER BY "timestamp" DESC, received_at DESC
          LIMIT 1
        ) latest ON TRUE
        WHERE d.status <> 'DELETED'
        ORDER BY COALESCE(d.last_seen_at, d.updated_at, d.created_at) DESC
        LIMIT 100`
      );
      const readable = [];
      for (const row of result.rows) {
        if (await farmAccessClient.canReadIot(actor, row.farm_id, row.cultivation_area_id)) {
          readable.push(toDeviceResponse(row, healthConfig));
        }
      }
      response.json({ devices: readable });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.get("/api/iot/alerts", async (request, response) => {
    try {
      const actor = requireActor(request);
      const statusFilter = normalizeAlertStatusFilter(request.query.status);
      const result = await pool.query(
        `SELECT a.*, d.name AS device_name, d.status AS device_status, d.last_seen_at
         FROM ${alertsTable} a
         LEFT JOIN ${devicesTable} d ON d.id = a.device_registry_id
         WHERE ($1::text IS NULL OR a.status = $1)
         ORDER BY a.last_observed_at DESC
         LIMIT 100`,
        [statusFilter]
      );
      const readable = [];
      for (const row of result.rows) {
        if (await farmAccessClient.canReadIot(actor, row.farm_id, row.cultivation_area_id)) {
          readable.push(toAlertResponse(row));
        }
      }
      response.json({ alerts: readable });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.get("/api/iot/alerts/:id", async (request, response) => {
    try {
      const actor = requireActor(request);
      const alert = await findAlert(pool, alertsTable, devicesTable, request.params.id);
      await requireAlertAccess(farmAccessClient, actor, alert);
      response.json({ alert: toAlertResponse(alert) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.post("/api/iot/alerts/:id/acknowledge", async (request, response) => {
    try {
      const actor = requireActor(request);
      const alert = await findAlert(pool, alertsTable, devicesTable, request.params.id);
      await requireAlertAccess(farmAccessClient, actor, alert);
      const result = await pool.query(
        `UPDATE ${alertsTable}
         SET status = CASE WHEN status = 'ALERTING' THEN 'ACKNOWLEDGED' ELSE status END,
             acknowledged_at = COALESCE(acknowledged_at, CURRENT_TIMESTAMP),
             acknowledged_by = COALESCE(acknowledged_by, $2),
             updated_at = CURRENT_TIMESTAMP
         WHERE id::text = $1
         RETURNING *`,
        [request.params.id.trim(), actor.userId]
      );
      response.json({ alert: toAlertResponse(result.rows[0]) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.post("/api/iot/devices", async (request, response) => {
    try {
      const actor = requireActor(request);
      const body = parseDeviceMutationBody(request.body, { create: true });
      await requireFarmManagement(farmAccessClient, actor, body.farmId, body.cultivationAreaId);
      const result = await pool.query(
        `INSERT INTO ${devicesTable}
          (device_uid, name, farm_id, cultivation_area_id, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
         RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at`,
        [
          body.deviceUid,
          body.name,
          body.farmId,
          body.cultivationAreaId,
          body.status || "ACTIVE"
        ]
      );
      response.status(201).json({ device: toDeviceResponse(result.rows[0], healthConfig) });
    } catch (error) {
      writeApiError(response, normalizeDatabaseError(error));
    }
  });

  app.get("/api/iot/devices/:identifier", async (request, response) => {
    try {
      const actor = requireActor(request);
      const row = await findDevice(pool, devicesTable, telemetryTable, request.params.identifier);
      await requireDeviceAccess(farmAccessClient, actor, row);
      response.json({ device: toDeviceResponse(row, healthConfig) });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.patch("/api/iot/devices/:identifier", async (request, response) => {
    try {
      const actor = requireActor(request);
      const current = await findDevice(pool, devicesTable, telemetryTable, request.params.identifier, {
        includeDeleted: false
      });
      await requireFarmManagement(farmAccessClient, actor, current.farm_id, current.cultivation_area_id);
      const patch = parseDevicePatchBody(request.body);
      const targetFarmId = patch.farmId ?? current.farm_id;
      const targetAreaId = Object.prototype.hasOwnProperty.call(patch, "cultivationAreaId")
        ? patch.cultivationAreaId
        : current.cultivation_area_id;
      if (targetFarmId !== current.farm_id || targetAreaId !== current.cultivation_area_id) {
        await requireFarmManagement(farmAccessClient, actor, targetFarmId, targetAreaId);
      }
      const result = await pool.query(
        `UPDATE ${devicesTable}
         SET name = COALESCE($2, name),
             farm_id = $3,
             cultivation_area_id = $4,
             status = COALESCE($5, status),
             updated_at = CURRENT_TIMESTAMP
         WHERE id = $1
           AND status <> 'DELETED'
         RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at`,
        [
          current.id,
          Object.prototype.hasOwnProperty.call(patch, "name") ? patch.name : null,
          targetFarmId,
          targetAreaId,
          patch.status || null
        ]
      );
      if (result.rowCount === 0) {
        throw apiError(404, "Device was not found");
      }
      response.json({ device: toDeviceResponse(result.rows[0], healthConfig) });
    } catch (error) {
      writeApiError(response, normalizeDatabaseError(error));
    }
  });

  app.delete("/api/iot/devices/:identifier", async (request, response) => {
    try {
      const actor = requireActor(request);
      const current = await findDevice(pool, devicesTable, telemetryTable, request.params.identifier, {
        includeDeleted: false
      });
      await requireFarmManagement(farmAccessClient, actor, current.farm_id, current.cultivation_area_id);
      const result = await pool.query(
        `UPDATE ${devicesTable}
         SET status = 'DELETED',
             updated_at = CURRENT_TIMESTAMP
         WHERE id = $1
           AND status <> 'DELETED'
         RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at`,
        [current.id]
      );
      if (result.rowCount === 0) {
        throw apiError(404, "Device was not found");
      }
      response.json({ device: toDeviceResponse(result.rows[0], healthConfig), message: "Device registry was removed" });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.get("/api/iot/devices/:identifier/telemetry/latest", async (request, response) => {
    try {
      const actor = requireActor(request);
      const device = await findDevice(pool, devicesTable, telemetryTable, request.params.identifier);
      await requireDeviceAccess(farmAccessClient, actor, device);
      const latest = await latestTelemetry(pool, telemetryTable, device.device_uid);
      response.json({
        device: toDeviceSummary(device, healthConfig),
        telemetry: latest ? toTelemetryResponse(latest) : null
      });
    } catch (error) {
      writeApiError(response, error);
    }
  });

  app.get("/api/iot/devices/:identifier/telemetry", async (request, response) => {
    try {
      const actor = requireActor(request);
      const device = await findDevice(pool, devicesTable, telemetryTable, request.params.identifier);
      await requireDeviceAccess(farmAccessClient, actor, device);
      const range = parseHistoryRange(request.query);
      const result = await pool.query(
        `SELECT id, device_id, temperature, humidity, light, "timestamp", received_at
         FROM ${telemetryTable}
         WHERE device_id = $1
           AND "timestamp" >= $2
           AND "timestamp" <= $3
         ORDER BY "timestamp" ASC, received_at ASC
         LIMIT $4`,
        [device.device_uid, range.from, range.to, range.limit]
      );
      response.json({
        device: toDeviceSummary(device, healthConfig),
        range,
        telemetry: result.rows.map(toTelemetryResponse)
      });
    } catch (error) {
      writeApiError(response, error);
    }
  });
}

async function ensureReadSchema(pool, devicesTable) {
  await pool.query(
    `CREATE TABLE IF NOT EXISTS ${devicesTable} (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      device_uid VARCHAR(150) NOT NULL UNIQUE,
      name VARCHAR(160),
      farm_id VARCHAR(150) NOT NULL,
      cultivation_area_id VARCHAR(150),
      status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
      last_seen_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT ck_iot_devices_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'DELETED')
      )
    )`
  );
  await pool.query(
    `CREATE INDEX IF NOT EXISTS idx_iot_devices_farm_area
     ON ${devicesTable} (farm_id, cultivation_area_id)`
  );
  await pool.query(
    `CREATE INDEX IF NOT EXISTS idx_iot_devices_last_seen
     ON ${devicesTable} (last_seen_at DESC)`
  );
}

function createFarmAccessClient({ farmServiceUrl, internalToken }) {
  const baseUrl = farmServiceUrl.replace(/\/$/, "");
  return {
    async canReadIot(actor, farmId, cultivationAreaId) {
      return checkFarmAccess({ actor, baseUrl, cultivationAreaId, farmId, internalToken, permission: "READ_IOT" });
    },
    async canManageIot(actor, farmId, cultivationAreaId) {
      return checkFarmAccess({ actor, baseUrl, cultivationAreaId, farmId, internalToken, permission: "CONFIGURE_DEVICE" });
    },
    async alertRecipients(farmId, cultivationAreaId) {
      const response = await fetch(`${baseUrl}/internal/v1/farm-access/alert-recipients`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(internalToken ? { "X-Internal-Token": internalToken } : {})
        },
        body: JSON.stringify({
          cultivationAreaId,
          farmId,
          permission: "READ_IOT"
        })
      });
      if (!response.ok) {
        return [];
      }
      const body = await response.json();
      return Array.isArray(body.recipientUserIds) ? body.recipientUserIds : [];
    }
  };
}

async function checkFarmAccess({ actor, baseUrl, cultivationAreaId, farmId, internalToken, permission }) {
  const response = await fetch(`${baseUrl}/internal/v1/farm-access/check`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(internalToken ? { "X-Internal-Token": internalToken } : {})
    },
    body: JSON.stringify({
      userId: actor.userId,
      role: actor.role,
      farmId,
      cultivationAreaId,
      permission
    })
  });
  if (response.status === 401) {
    throw apiError(401, "Authenticated user is required");
  }
  if (response.status >= 500) {
    throw apiError(503, "Farm access service is unavailable");
  }
  if (!response.ok) {
    return false;
  }
  const body = await response.json();
  return body && body.allowed === true;
}

async function findDevice(pool, devicesTable, telemetryTable, identifier, options = {}) {
  if (!identifier || !identifier.trim()) {
    throw apiError(400, "Device id is required");
  }
  const deletedClause = options.includeDeleted ? "" : "AND d.status <> 'DELETED'";
  const result = await pool.query(
    `SELECT
      d.id,
      d.device_uid,
      d.name,
      d.farm_id,
      d.cultivation_area_id,
      d.status,
      d.last_seen_at,
      d.created_at,
      d.updated_at,
      latest.id AS latest_id,
      latest.temperature,
      latest.humidity,
      latest.light,
      latest."timestamp" AS latest_timestamp,
      latest.received_at AS latest_received_at
    FROM ${devicesTable} d
    LEFT JOIN LATERAL (
      SELECT id, temperature, humidity, light, "timestamp", received_at
      FROM ${telemetryTable}
      WHERE device_id = d.device_uid
      ORDER BY "timestamp" DESC, received_at DESC
      LIMIT 1
    ) latest ON TRUE
    WHERE (d.id::text = $1 OR d.device_uid = $1)
      ${deletedClause}
    LIMIT 1`,
    [identifier.trim()]
  );
  if (result.rowCount === 0) {
    throw apiError(404, "Device was not found");
  }
  return result.rows[0];
}

async function latestTelemetry(pool, telemetryTable, deviceUid) {
  const result = await pool.query(
    `SELECT id, device_id, temperature, humidity, light, "timestamp", received_at
     FROM ${telemetryTable}
     WHERE device_id = $1
     ORDER BY "timestamp" DESC, received_at DESC
     LIMIT 1`,
    [deviceUid]
  );
  return result.rows[0] || null;
}

async function requireDeviceAccess(farmAccessClient, actor, device) {
  const allowed = await farmAccessClient.canReadIot(
    actor,
    device.farm_id,
    device.cultivation_area_id
  );
  if (!allowed) {
    throw apiError(403, "Access denied for IoT device");
  }
}

async function requireFarmManagement(farmAccessClient, actor, farmId, cultivationAreaId) {
  const allowed = await farmAccessClient.canManageIot(actor, farmId, cultivationAreaId);
  if (!allowed) {
    throw apiError(403, "Access denied for IoT device management");
  }
}

async function requireAlertAccess(farmAccessClient, actor, alert) {
  const allowed = await farmAccessClient.canReadIot(actor, alert.farm_id, alert.cultivation_area_id);
  if (!allowed) {
    throw apiError(403, "Access denied for IoT alert");
  }
}

function parseDeviceMutationBody(body, { create }) {
  const normalized = normalizeDevicePatchBody(body);
  if (create && !normalized.deviceUid) {
    throw apiError(400, "deviceUid is required");
  }
  if (create && !normalized.farmId) {
    throw apiError(400, "farmId is required");
  }
  return normalized;
}

function parseDevicePatchBody(body) {
  const normalized = normalizeDevicePatchBody(body);
  if (
    !Object.prototype.hasOwnProperty.call(normalized, "name") &&
    !Object.prototype.hasOwnProperty.call(normalized, "farmId") &&
    !Object.prototype.hasOwnProperty.call(normalized, "cultivationAreaId") &&
    !Object.prototype.hasOwnProperty.call(normalized, "status")
  ) {
    throw apiError(400, "At least one mutable field is required");
  }
  return normalized;
}

function normalizeDevicePatchBody(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw apiError(400, "Request body must be an object");
  }
  const result = {};
  if (Object.prototype.hasOwnProperty.call(body, "deviceUid")) {
    result.deviceUid = normalizeDeviceUid(body.deviceUid);
  }
  if (Object.prototype.hasOwnProperty.call(body, "name")) {
    result.name = normalizeOptionalName(body.name);
  }
  if (Object.prototype.hasOwnProperty.call(body, "farmId")) {
    result.farmId = requireBodyText(body.farmId, "farmId is required");
  }
  if (Object.prototype.hasOwnProperty.call(body, "cultivationAreaId")) {
    result.cultivationAreaId = normalizeNullableText(body.cultivationAreaId, "cultivationAreaId");
  }
  if (Object.prototype.hasOwnProperty.call(body, "status")) {
    result.status = normalizeStatus(body.status);
  }
  return result;
}

function normalizeDeviceUid(value) {
  const text = requireBodyText(value, "deviceUid is required");
  if (!DEVICE_UID_PATTERN.test(text)) {
    throw apiError(400, "deviceUid must be 3-150 characters and contain only letters, numbers, dot, underscore, colon or dash");
  }
  return text;
}

function normalizeOptionalName(value) {
  const text = normalizeNullableText(value, "name");
  if (text !== null && text.length > 160) {
    throw apiError(400, "name must be at most 160 characters");
  }
  return text;
}

function normalizeStatus(value) {
  const text = requireBodyText(value, "status is required").toUpperCase();
  if (!DEVICE_STATUSES.has(text)) {
    throw apiError(400, "status must be ACTIVE, INACTIVE or MAINTENANCE");
  }
  return text;
}

function requireBodyText(value, message) {
  if (typeof value !== "string" || !value.trim()) {
    throw apiError(400, message);
  }
  return value.trim();
}

function normalizeNullableText(value, fieldName) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  if (typeof value !== "string") {
    throw apiError(400, `${fieldName} must be a string or null`);
  }
  return value.trim() || null;
}

function requireActor(request) {
  const userId = String(request.get("X-Auth-User-Id") || "").trim();
  const role = String(request.get("X-Auth-Role") || "").trim();
  const email = String(request.get("X-Auth-Email") || "").trim();
  if (!userId || !role) {
    throw apiError(401, "Authenticated user is required");
  }
  return { email, role, userId };
}

function parseHistoryRange(query) {
  const now = new Date();
  const to = parseDate(query.to, now, "to");
  const from = parseDate(
    query.from,
    new Date(to.getTime() - 24 * 60 * 60 * 1000),
    "from"
  );
  if (from.getTime() > to.getTime()) {
    throw apiError(400, "from must be before or equal to to");
  }
  const rawLimit = query.limit === undefined ? DEFAULT_HISTORY_LIMIT : Number(query.limit);
  if (!Number.isInteger(rawLimit) || rawLimit < 1 || rawLimit > MAX_HISTORY_LIMIT) {
    throw apiError(400, `limit must be between 1 and ${MAX_HISTORY_LIMIT}`);
  }
  return {
    from: from.toISOString(),
    limit: rawLimit,
    resolution: "raw",
    to: to.toISOString()
  };
}

function parseDate(value, fallback, fieldName) {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  const parsed = new Date(String(value));
  if (Number.isNaN(parsed.getTime())) {
    throw apiError(400, `${fieldName} must be a valid ISO-8601 timestamp`);
  }
  return parsed;
}

function toDeviceResponse(row, healthConfig) {
  return {
    ...toDeviceSummary(row, healthConfig),
    latestTelemetry: row.latest_id
      ? toTelemetryResponse({
          device_id: row.device_uid,
          humidity: row.humidity,
          id: row.latest_id,
          light: row.light,
          received_at: row.latest_received_at,
          temperature: row.temperature,
          timestamp: row.latest_timestamp
        })
      : null
  };
}

function toDeviceSummary(row, healthConfig) {
  const connectivity = connectivityForDevice(row, healthConfig || {
    expectedTelemetryIntervalSeconds: 60,
    staleAfterIntervals: 3,
    offlineAfterIntervals: 10
  });
  return {
    administrativeStatus: row.status,
    cultivationAreaId: row.cultivation_area_id,
    connectivityStatus: connectivity.status,
    connectivity,
    deviceUid: row.device_uid,
    farmId: row.farm_id,
    id: row.id,
    lastSeenAt: toIsoOrNull(row.last_seen_at),
    name: row.name || row.device_uid,
    status: row.status,
    telemetryState: row.latest_id ? "HAS_TELEMETRY" : "NEVER_REPORTED"
  };
}

async function findAlert(pool, alertsTable, devicesTable, id) {
  if (!id || !id.trim()) {
    throw apiError(400, "Alert id is required");
  }
  const result = await pool.query(
    `SELECT a.*, d.name AS device_name, d.status AS device_status, d.last_seen_at
     FROM ${alertsTable} a
     LEFT JOIN ${devicesTable} d ON d.id = a.device_registry_id
     WHERE a.id::text = $1
     LIMIT 1`,
    [id.trim()]
  );
  if (result.rowCount === 0) {
    throw apiError(404, "Alert was not found");
  }
  return result.rows[0];
}

function normalizeAlertStatusFilter(value) {
  if (value === undefined || value === null || value === "") {
    return null;
  }
  const text = String(value).trim().toUpperCase();
  if (!["ALERTING", "RECOVERED", "ACKNOWLEDGED"].includes(text)) {
    throw apiError(400, "status must be ALERTING, RECOVERED or ACKNOWLEDGED");
  }
  return text;
}

function toAlertResponse(row) {
  return {
    acknowledgedAt: toIsoOrNull(row.acknowledged_at),
    acknowledgedBy: row.acknowledged_by || null,
    alertType: row.type,
    cultivationAreaId: row.cultivation_area_id,
    deviceId: row.device_registry_id,
    deviceName: row.device_name || row.device_uid,
    deviceUid: row.device_uid,
    farmId: row.farm_id,
    id: row.id,
    lastObservedAt: toIsoOrNull(row.last_observed_at),
    measuredValue: numberOrNull(row.measured_value),
    message: row.message,
    recoveredAt: toIsoOrNull(row.recovered_at),
    startedAt: toIsoOrNull(row.started_at),
    status: row.status,
    thresholdValue: numberOrNull(row.threshold_value)
  };
}

function toTelemetryResponse(row) {
  return {
    deviceUid: row.device_id,
    humidity: numberOrNull(row.humidity),
    id: row.id,
    light: numberOrNull(row.light),
    measuredAt: toIsoOrNull(row.timestamp),
    receivedAt: toIsoOrNull(row.received_at),
    temperature: numberOrNull(row.temperature),
    units: {
      humidity: "%",
      light: "raw",
      temperature: "°C"
    }
  };
}

function numberOrNull(value) {
  if (value === null || value === undefined) return null;
  return Number(value);
}

function toIsoOrNull(value) {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function apiError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

function normalizeDatabaseError(error) {
  if (error && error.code === "23505") {
    return apiError(409, "deviceUid is already registered");
  }
  return error;
}

function writeApiError(response, error) {
  const status = Number.isInteger(error.status) ? error.status : 500;
  response.status(status).json({
    error: status >= 500 ? "Internal Server Error" : "IoT API Error",
    message: error.message || "Unable to process IoT request",
    status,
    timestamp: new Date().toISOString()
  });
}

module.exports = {
  createFarmAccessClient,
  ensureReadSchema,
  parseHistoryRange,
  registerReadApi,
  requireActor,
  normalizeDeviceUid,
  toDeviceResponse,
  toTelemetryResponse
};
