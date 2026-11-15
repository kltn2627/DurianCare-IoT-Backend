const MAX_HISTORY_LIMIT = 1000;
const DEFAULT_HISTORY_LIMIT = 288;
const DEVICE_UID_PATTERN = /^[A-Za-z0-9._:-]{3,150}$/;
const DEVICE_STATUSES = new Set(["ACTIVE", "INACTIVE", "MAINTENANCE"]);
const DEFAULT_MAX_BUCKETS = 240;
const MAX_BUCKETS = 1000;
// Candidate bucket widths (seconds); the smallest that keeps the range within maxBuckets wins.
const BUCKET_STEPS_SECONDS = [60, 300, 600, 1800, 3600, 10800, 21600, 43200, 86400, 172800, 604800];
// Buckets align to Vietnam (UTC+7) midnight so daily buckets match the farm's calendar days.
const BUCKET_ORIGIN = "2000-01-01T00:00:00+07:00";
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

  if (dependencies.schema) {
    require("./devices/deviceLifecycle").registerDeviceLifecycleApi(app, {
      claimTtlHours: dependencies.claimTtlHours,
      farmAccessClient,
      pool,
      schema: dependencies.schema,
      thresholds: dependencies.thresholds,
      helpers: {
        apiError,
        findDevice: (identifier) => findDevice(pool, devicesTable, telemetryTable, String(identifier || "")),
        healthConfig,
        normalizeDeviceUid,
        requireActor,
        requireDeviceAccess,
        requireFarmManagement,
        toDeviceResponse,
        writeApiError
      }
    });
  }

  app.use(["/api/v1/sensors/latest", "/api/v1/sensors/history"], async (request, response, next) => {
    if (request.method !== "GET") return next();
    try {
      const actor = requireActor(request);
      if (typeof request.query.device_id !== "string" || !request.query.device_id.trim()) {
        throw apiError(400, "device_id is required for scoped telemetry reads");
      }
      const device = await findDevice(pool, devicesTable, telemetryTable, request.query.device_id.trim());
      await requireDeviceAccess(farmAccessClient, actor, device);
      next();
    } catch (error) {
      writeApiError(response, error);
    }
  });

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
          d.device_type,
          d.provisioning_status,
          d.firmware_version,
          d.claimed_at,
          latest.id AS latest_id,
          latest.temperature,
          latest.humidity,
          latest.light,
          latest.soil_moisture,
      latest.soil_raw,
      latest.soil_status,
      latest.dht_status,
          latest.soil_raw,
          latest.soil_status,
          latest.dht_status,
          latest."timestamp" AS latest_timestamp,
          latest.received_at AS latest_received_at
        FROM ${devicesTable} d
        LEFT JOIN LATERAL (
          SELECT id, temperature, humidity, light, soil_moisture, "timestamp", received_at, soil_raw, soil_status, dht_status
          FROM ${telemetryTable}
          WHERE LOWER(device_id) = LOWER(d.device_uid) AND source = 'DEVICE'
          ORDER BY "timestamp" DESC, received_at DESC
          LIMIT 1
        ) latest ON TRUE
        WHERE d.status <> 'DELETED'
          AND d.provisioning_status = 'CLAIMED'
          AND d.farm_id IS NOT NULL
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
      // Knowing a UID is not proof of possession: farmers add devices with a claim code
      // (POST /api/iot/devices/claim). Direct binding stays available to administrators only.
      if (String(actor.role).toUpperCase() !== "ADMIN") {
        throw apiError(403, "Use the device claim code to add a device");
      }
      const body = parseDeviceMutationBody(request.body, { create: true });
      const result = await pool.query(
        `INSERT INTO ${devicesTable}
          (device_uid, name, farm_id, cultivation_area_id, status, provisioned_by, claimed_by, claimed_at,
           created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
         RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at,
           device_type, provisioning_status, firmware_version, claimed_at`,
        [
          body.deviceUid,
          body.name,
          body.farmId,
          body.cultivationAreaId,
          body.status || "ACTIVE",
          actor.userId
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
      const [latestByMetric, openAlerts] = await Promise.all([
        latestValuesByMetric(pool, telemetryTable, row.device_uid),
        pool.query(
          `SELECT a.*, NULL::text AS device_name FROM ${alertsTable} a
           WHERE a.device_uid = $1 AND a.status IN ('ALERTING', 'ACKNOWLEDGED')
           ORDER BY a.last_observed_at DESC LIMIT 20`,
          [row.device_uid]
        )
      ]);
      response.json({
        device: { ...toDeviceResponse(row, healthConfig), latestByMetric },
        openAlerts: openAlerts.rows.map(toAlertResponse)
      });
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
         RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at,
           device_type, provisioning_status, firmware_version, claimed_at`,
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
         RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at, created_at, updated_at,
           device_type, provisioning_status, firmware_version, claimed_at`,
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
      const [latest, latestByMetric] = await Promise.all([
        latestTelemetry(pool, telemetryTable, device.device_uid),
        latestValuesByMetric(pool, telemetryTable, device.device_uid)
      ]);
      response.json({
        device: toDeviceSummary(device, healthConfig),
        latestByMetric,
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
        `SELECT id, device_id, temperature, humidity, light, soil_moisture, "timestamp", received_at, source, soil_raw, soil_status, dht_status
         FROM ${telemetryTable}
         WHERE LOWER(device_id) = LOWER($1)
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

  app.get("/api/iot/devices/:identifier/telemetry/buckets", async (request, response) => {
    try {
      const actor = requireActor(request);
      const device = await findDevice(pool, devicesTable, telemetryTable, request.params.identifier);
      await requireDeviceAccess(farmAccessClient, actor, device);
      response.json(await bucketedHistory(pool, telemetryTable, device, request.query, healthConfig));
    } catch (error) {
      writeApiError(response, error);
    }
  });

  // Legacy device_id-scoped variant for the raw-sensor fallback; the scope middleware above
  // (prefix /api/v1/sensors/history) has already authorized device_id before this runs.
  app.get("/api/v1/sensors/history/buckets", async (request, response) => {
    try {
      const actor = requireActor(request);
      const device = await findDevice(pool, devicesTable, telemetryTable, String(request.query.device_id || ""));
      await requireDeviceAccess(farmAccessClient, actor, device);
      response.json(await bucketedHistory(pool, telemetryTable, device, request.query, healthConfig));
    } catch (error) {
      writeApiError(response, error);
    }
  });
}

/**
 * Time-bucketed averages, one row per (bucket, source). Measured (DEVICE) and synthetic
 * (SYNTHETIC_BACKFILL) values are never averaged together, so callers can label each.
 */
async function bucketedHistory(pool, telemetryTable, device, query, healthConfig) {
  const range = parseBucketRange(query);
  const result = await pool.query(
    `SELECT
       date_bin(make_interval(secs => $4), "timestamp", $5::timestamptz) AS bucket_start,
       source,
       COUNT(*)::int AS sample_count,
       AVG(temperature) AS temperature_avg,
       MIN(temperature) AS temperature_min,
       MAX(temperature) AS temperature_max,
       AVG(humidity) AS humidity_avg,
       MIN(humidity) AS humidity_min,
       MAX(humidity) AS humidity_max,
       AVG(soil_moisture) AS soil_moisture_avg,
       MIN(soil_moisture) AS soil_moisture_min,
       MAX(soil_moisture) AS soil_moisture_max,
       AVG(light) AS light_avg,
       COUNT(*) FILTER (WHERE soil_moisture < 60)::int AS dry_count,
       COUNT(*) FILTER (WHERE soil_moisture >= 60 AND soil_moisture <= 85)::int AS optimal_count,
       COUNT(*) FILTER (WHERE soil_moisture > 85)::int AS wet_count
     FROM ${telemetryTable}
     WHERE LOWER(device_id) = LOWER($1)
       AND "timestamp" >= $2
       AND "timestamp" <= $3
     GROUP BY 1, 2
     ORDER BY 1 ASC, 2 ASC`,
    [device.device_uid, range.from, range.to, range.bucketSeconds, BUCKET_ORIGIN]
  );
  return {
    device: toDeviceSummary(device, healthConfig),
    range,
    buckets: result.rows.map((row) => toBucketResponse(row, range.bucketSeconds))
  };
}

function parseBucketRange(query) {
  const now = new Date();
  const to = parseDate(query.to, now, "to");
  const from = parseDate(query.from, new Date(to.getTime() - 24 * 60 * 60 * 1000), "from");
  if (from.getTime() > to.getTime()) {
    throw apiError(400, "from must be before or equal to to");
  }
  const maxBuckets = query.maxBuckets === undefined ? DEFAULT_MAX_BUCKETS : Number(query.maxBuckets);
  if (!Number.isInteger(maxBuckets) || maxBuckets < 1 || maxBuckets > MAX_BUCKETS) {
    throw apiError(400, `maxBuckets must be between 1 and ${MAX_BUCKETS}`);
  }
  return {
    bucketSeconds: chooseBucketSeconds(to.getTime() - from.getTime(), maxBuckets),
    from: from.toISOString(),
    maxBuckets,
    resolution: "bucketed",
    to: to.toISOString()
  };
}

function chooseBucketSeconds(spanMs, maxBuckets) {
  const spanSeconds = Math.max(spanMs / 1000, 1);
  // +1 because an unaligned range can straddle one extra bucket boundary.
  const fits = BUCKET_STEPS_SECONDS.find((step) => Math.ceil(spanSeconds / step) + 1 <= maxBuckets);
  if (fits) return fits;
  const day = 86400;
  return Math.ceil(spanSeconds / Math.max(maxBuckets - 1, 1) / day) * day;
}

function toBucketResponse(row, bucketSeconds) {
  const start = toIsoOrNull(row.bucket_start);
  return {
    bucketEnd: start ? new Date(Date.parse(start) + bucketSeconds * 1000).toISOString() : null,
    bucketStart: start,
    count: Number(row.sample_count),
    humidity: roundOrNull(row.humidity_avg),
    humidityMax: roundOrNull(row.humidity_max),
    humidityMin: roundOrNull(row.humidity_min),
    light: roundOrNull(row.light_avg),
    soilMoisture: roundOrNull(row.soil_moisture_avg),
    soilMoistureMax: roundOrNull(row.soil_moisture_max),
    soilMoistureMin: roundOrNull(row.soil_moisture_min),
    soilStatusCounts: {
      DRY_WARNING: Number(row.dry_count || 0),
      OPTIMAL: Number(row.optimal_count || 0),
      WET_WARNING: Number(row.wet_count || 0)
    },
    source: row.source ?? "DEVICE",
    temperature: roundOrNull(row.temperature_avg),
    temperatureMax: roundOrNull(row.temperature_max),
    temperatureMin: roundOrNull(row.temperature_min)
  };
}

function roundOrNull(value) {
  if (value === null || value === undefined) return null;
  return Math.round(Number(value) * 100) / 100;
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

const ALERT_RECIPIENTS_TIMEOUT_MS = 10_000;

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
        }),
        // Bounded so one delivery attempt stays well inside the retry lease.
        signal: AbortSignal.timeout(ALERT_RECIPIENTS_TIMEOUT_MS)
      });
      // 404: the farm/zone no longer exists, so nobody can receive it. Any other failure is transient and
      // must surface as an error (delivery FAILED, retried) instead of an empty list (SKIPPED for good).
      if (response.status === 404) {
        return [];
      }
      if (!response.ok) {
        throw new Error(`farm-service alert-recipients responded ${response.status}`);
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
      d.device_type,
      d.provisioning_status,
      d.firmware_version,
      d.claimed_at,
      latest.id AS latest_id,
      latest.temperature,
      latest.humidity,
      latest.light,
      latest.soil_moisture,
      latest.soil_raw,
      latest.soil_status,
      latest.dht_status,
      latest."timestamp" AS latest_timestamp,
      latest.received_at AS latest_received_at
    FROM ${devicesTable} d
    LEFT JOIN LATERAL (
      SELECT id, temperature, humidity, light, soil_moisture, "timestamp", received_at, soil_raw, soil_status, dht_status
      FROM ${telemetryTable}
      WHERE LOWER(device_id) = LOWER(d.device_uid) AND source = 'DEVICE'
      ORDER BY "timestamp" DESC, received_at DESC
      LIMIT 1
    ) latest ON TRUE
    WHERE (d.id::text = $1 OR LOWER(d.device_uid) = LOWER($1))
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
    `SELECT id, device_id, temperature, humidity, light, soil_moisture, "timestamp", received_at, soil_raw, soil_status, dht_status
     FROM ${telemetryTable}
     WHERE LOWER(device_id) = LOWER($1) AND source = 'DEVICE'
     ORDER BY "timestamp" DESC, received_at DESC
     LIMIT 1`,
    [deviceUid]
  );
  return result.rows[0] || null;
}

/** Most recent measured value of each metric (a reading may carry only some metrics). */
async function latestValuesByMetric(pool, telemetryTable, deviceUid) {
  const pick = (column) => `(SELECT json_build_object('value', ${column}, 'measuredAt', "timestamp")
      FROM ${telemetryTable}
      WHERE LOWER(device_id) = LOWER($1) AND source = 'DEVICE' AND ${column} IS NOT NULL
      ORDER BY "timestamp" DESC, received_at DESC LIMIT 1)`;
  const result = await pool.query(
    `SELECT ${pick("temperature")} AS temperature, ${pick("humidity")} AS humidity,
            ${pick("soil_moisture")} AS soil_moisture, ${pick("light")} AS light,
            (SELECT json_build_object('status', soil_status, 'raw', soil_raw, 'measuredAt', "timestamp")
               FROM ${telemetryTable}
               WHERE LOWER(device_id) = LOWER($1) AND source = 'DEVICE' AND soil_status IS NOT NULL
               ORDER BY "timestamp" DESC, received_at DESC LIMIT 1) AS soil_sensor`,
    [deviceUid]
  );
  const row = result.rows[0] || {};
  const metric = (value, unit) => value && value.value !== null && value.value !== undefined
    ? { value: Number(value.value), unit, measuredAt: toIsoOrNull(value.measuredAt) }
    : null;
  const soilSensor = row.soil_sensor
    ? { status: row.soil_sensor.status, raw: numberOrNull(row.soil_sensor.raw), measuredAt: toIsoOrNull(row.soil_sensor.measuredAt) }
    : null;
  let soilMoisture = metric(row.soil_moisture, "%");
  // The probe's latest verdict is newer than the last valid percentage: that percentage is no longer current.
  if (soilMoisture && soilSensor && soilSensor.status !== "OK" &&
      new Date(soilSensor.measuredAt).getTime() > new Date(soilMoisture.measuredAt).getTime()) {
    soilMoisture = null;
  }
  return {
    temperature: metric(row.temperature, "°C"),
    humidity: metric(row.humidity, "%"),
    soilMoisture,
    soilSensor,
    light: metric(row.light, "raw")
  };
}

async function requireDeviceAccess(farmAccessClient, actor, device) {
  if (!device.farm_id) {
    throw apiError(404, "Device was not found");
  }
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
  if (!farmId) {
    throw apiError(403, "Access denied for IoT device management");
  }
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
          soil_moisture: row.soil_moisture,
          soil_raw: row.soil_raw,
          soil_status: row.soil_status,
          dht_status: row.dht_status,
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
    claimedAt: toIsoOrNull(row.claimed_at),
    deviceType: row.device_type || "SENSOR",
    deviceUid: row.device_uid,
    farmId: row.farm_id,
    firmwareVersion: row.firmware_version || null,
    id: row.id,
    lastSeenAt: toIsoOrNull(row.last_seen_at),
    name: row.name || row.device_uid,
    provisioningStatus: row.provisioning_status || "CLAIMED",
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
    severity: row.severity || null,
    startedAt: toIsoOrNull(row.started_at),
    status: row.status,
    thresholdValue: numberOrNull(row.threshold_value)
  };
}

function toTelemetryResponse(row) {
  const soilMoisture = numberOrNull(row.soil_moisture ?? row.soilMoisture);
  return {
    deviceUid: row.device_id,
    humidity: numberOrNull(row.humidity ?? row.air_humidity),
    id: row.id,
    light: numberOrNull(row.light),
    measuredAt: toIsoOrNull(row.timestamp),
    receivedAt: toIsoOrNull(row.received_at),
    // DEVICE = measured by the sensor; SYNTHETIC_BACKFILL = labelled simulated history.
    source: row.source ?? "DEVICE",
    soilMoisture,
    soil_moisture: soilMoisture,
    // Firmware >= 1.4.0: raw ADC value + its own verdict. soilMoisture is null unless soilStatus is OK
    // (or NULL for older firmware), so an unconnected/uncalibrated probe is never shown as a percentage.
    soilRaw: numberOrNull(row.soil_raw),
    soilStatus: row.soil_status ?? null,
    dhtStatus: row.dht_status ?? null,
    status: telemetryStatusFromSoil(soilMoisture),
    temperature: numberOrNull(row.temperature),
    units: {
      humidity: "%",
      light: "raw",
      temperature: "°C"
    }
  };
}

function telemetryStatusFromSoil(soilMoisture) {
  if (soilMoisture === null || soilMoisture === undefined) return null;
  if (soilMoisture < 60) return "DRY_WARNING";
  if (soilMoisture > 85) return "WET_WARNING";
  return "OPTIMAL";
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
  chooseBucketSeconds,
  createFarmAccessClient,
  parseBucketRange,
  ensureReadSchema,
  parseHistoryRange,
  registerReadApi,
  requireActor,
  normalizeDeviceUid,
  latestValuesByMetric,
  toDeviceResponse,
  toTelemetryResponse
};
