const path    = require("path");
const express = require("express");
const mqtt    = require("mqtt");
const cron    = require("node-cron");
const { Kafka } = require("kafkajs");
const { Pool }  = require("pg");
const config    = require("./config");
const { createUdpDiscoveryService } = require("./discovery/udpDiscoveryService");
const { createCameraRouter } = require("./camera/cameraRoutes");
const { loadAll: loadCameraSchedules, stopAll: stopCameraSchedules } = require("./camera/cameraScheduler");
const backgroundWork = require("./workDrain").createWorkDrain();
const { drainCaptures } = require("./camera/cameraService");
const { ensureCameraHealthSchema, markOfflineStaleCameras } = require("./camera/deviceService");
const { createAssessmentRouter } = require("./export/assessmentRoutes");
const { createPublicRouter }     = require("./public/publicRoutes");
const {
  ensureAlertSchema,
  evaluateOfflineDevices,
  evaluateTelemetryAlerts,
  retryNotifications
} = require("./alert-engine");
const {
  createFarmAccessClient,
  ensureReadSchema,
  registerReadApi
} = require("./read-api");
const {
  requireInternalToken,
  runOfflineWatchdog,
  startOfflineWatchdogScheduler
} = require("./iot-runtime");
const { connectWithRetry } = require("./kafkaConnect");
const { recordDeviceSeen, httpTelemetryEvent } = require("./telemetryRegistry");
const { ensureTelemetryProvenanceSchema } = require("./telemetryProvenance");
const { applySensorQuality, ensureSensorQualitySchema, hasDiagnosis, parseSensorQuality } = require("./sensorQuality");
const {
  METRICS,
  deviceAlertsEnabled,
  effectiveChecks,
  ensureDeviceLifecycleSchema,
  excludeTestAndDeletedDevices,
  loadRulesForDevice,
  pendingConfigFor
} = require("./devices/deviceLifecycle");

const app = express();
app.use(express.json({ limit: "32kb" }));

// Serve stored camera images (public — no auth needed, accessed by browser directly)
app.use("/uploads", express.static(path.resolve(config.uploadsDir)));

const { schema: postgresSchema, ...postgresPoolConfig } = config.postgres;
if (!/^[a-z_][a-z0-9_]*$/.test(postgresSchema)) {
  throw new Error("POSTGRES_SCHEMA contains unsupported characters");
}
const telemetryTable = `"${postgresSchema}".telemetry`;
const devicesTable = `"${postgresSchema}".iot_devices`;
// Cross-device reads skip readings of test devices and of removed devices; a request for one device_id is unaffected.
const NOT_TEST_OR_DELETED_DEVICE = excludeTestAndDeletedDevices(devicesTable, telemetryTable);
const alertsTable = `"${postgresSchema}".iot_alerts`;
const pool = new Pool(postgresPoolConfig);
pool.on("error", (error) => {
  console.error("Unexpected PostgreSQL pool error", error);
});
const kafka = new Kafka({
  clientId: "duriancare-iot-service",
  brokers: config.kafkaBrokers
});
const producer = kafka.producer();
const farmAccessClient = createFarmAccessClient({
  farmServiceUrl: config.farmServiceUrl,
  internalToken: config.internalToken
});
const notificationPublisher = {
  publish(event) {
    return producer.send({
      topic: config.notificationTopic,
      messages: [{ key: event.receiverId, value: JSON.stringify(event) }]
    });
  }
};

let mqttClient;
let kafkaProducerReady = false;
let kafkaConnector;
let offlineWatchdogScheduler;
let httpServer;
let udpDiscovery;
let shuttingDown = false;
let cameraOfflineTask;
app.use((_request, response, next) => {
  if (shuttingDown) return response.status(503).json({ error: "IoT service shutting down" });
  next();
});

// ── Health ────────────────────────────────────────────────────────────────────

app.get("/actuator/health", async (_request, response) => {
  try {
    await pool.query("SELECT 1");
    response.json({
      status: "UP",
      service: "duriancare-iot-service",
      database: "PostgreSQL"
    });
  } catch (error) {
    response.status(503).json({
      status: "DOWN",
      service: "duriancare-iot-service",
      error: error.message
    });
  }
});

// ── Internal APIs ─────────────────────────────────────────────────────────────

app.post("/internal/iot/offline-watchdog/run", requireInternalToken(config.internalToken), async (_request, response) => {
  try {
    const result = await runOfflineWatchdog({
      alertsTable,
      devicesTable,
      evaluateOfflineDevices,
      farmAccessClient,
      healthConfig: config.iotHealth,
      notificationPolicy: config.notificationPolicy,
      notificationPublisher,
      pool,
      retryNotifications
    });
    response.json({
      created: result.created.length,
      recovered: result.recovered.length,
      skipped: result.skipped === true
    });
  } catch (error) {
    console.error("Offline watchdog failed", error);
    response.status(500).json({ error: "Offline watchdog failed", message: error.message });
  }
});

registerReadApi(app, {
  alertsTable,
  claimTtlHours: config.claimCodeTtlHours,
  devicesTable,
  farmAccessClient,
  healthConfig: config.iotHealth,
  pool,
  schema: postgresSchema,
  telemetryTable,
  thresholds: config.iotThresholds
});

// ── Sensor REST API ───────────────────────────────────────────────────────────

/**
 * POST /api/v1/sensors/data
 * Used by ESP32 devices to push sensor readings directly to the IoT service.
 */
app.post("/api/v1/sensors/data", async (request, response) => {
  try {
    const { device_id, temperature, humidity, soil_moisture } =
      request.body ?? {};

    if (!device_id || typeof device_id !== "string" || !device_id.trim()) {
      return response
        .status(400)
        .json({ error: "device_id is required and must be a non-empty string" });
    }

    let temp, hum, soil, quality;
    try {
      temp = parseOptionalNumber(temperature, "temperature");
      hum = parseOptionalNumber(humidity, "humidity");
      soil = parseOptionalNumber(soil_moisture, "soil_moisture");
      quality = parseSensorQuality(request.body ?? {});
    } catch (validationError) {
      return response.status(400).json({ error: validationError.message });
    }
    // A value the firmware itself marks invalid (unconnected/uncalibrated soil probe, DHT error) is dropped.
    ({ temperature: temp, humidity: hum, soilMoisture: soil } = applySensorQuality(
      { temperature: temp, humidity: hum, soilMoisture: soil }, quality));

    if (temp === null && hum === null && soil === null && !hasDiagnosis(quality)) {
      return response.status(400).json({
        error:
          "At least one measurement (temperature, humidity, soil_moisture) is required"
      });
    }

    let firmwareVersion, reportedConfigVersion;
    try {
      ({ firmwareVersion, reportedConfigVersion } = parseDeviceReport(request.body));
    } catch (validationError) {
      return response.status(400).json({ error: validationError.message });
    }

    const ingestDevice = await findIngestDevice(device_id.trim());
    if (!ingestDevice) {
      return response.status(404).json({ error: "Device is not registered", code: "DEVICE_NOT_REGISTERED" });
    }
    if (ingestDevice.provisioning_status !== "CLAIMED") {
      return response.status(409).json({ error: "Device has not been claimed yet", code: "DEVICE_NOT_CLAIMED" });
    }

    const now = new Date();
    const insertResult = await pool.query(
      `INSERT INTO ${telemetryTable}
        (device_id, temperature, humidity, soil_moisture, "timestamp", received_at, farm_id, cultivation_area_id,
         soil_raw, soil_status, dht_status)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
       RETURNING id, device_id, temperature, humidity, soil_moisture, "timestamp", soil_raw, soil_status, dht_status`,
      [ingestDevice.device_uid, temp, hum, soil, now.toISOString(), now.toISOString(),
        ingestDevice.farm_id, ingestDevice.cultivation_area_id, quality.soilRaw, quality.soilStatus, quality.dhtStatus]
    );

    const row = insertResult.rows[0];
    // Same registry stamp as the MQTT path, so connectivity is ONLINE/STALE/OFFLINE, not UNKNOWN.
    const registryDevice = await recordDeviceSeen({
      pool, devicesTable, deviceId: device_id.trim(), receivedAt: now.toISOString()
    });
    await recordFirmwareVersion(ingestDevice, firmwareVersion);

    if (kafkaProducerReady) {
      try {
        await producer.send({
          topic: config.kafkaTopic,
          messages: [
            {
              key: device_id.trim(),
              value: JSON.stringify({
                id: row.id,
                deviceId: row.device_id,
                temperature: row.temperature !== null ? Number(row.temperature) : null,
                humidity: row.humidity !== null ? Number(row.humidity) : null,
                soilMoisture:
                  row.soil_moisture !== null ? Number(row.soil_moisture) : null,
                soilRaw: row.soil_raw ?? null,
                soilStatus: row.soil_status ?? null,
                timestamp: row.timestamp,
                receivedAt: now.toISOString()
              })
            }
          ]
        });
      } catch (kafkaErr) {
        console.warn("[kafka] Publish failed (non-fatal):", kafkaErr.message);
      }
    }

    // Same threshold engine (dedupe + recovery) as MQTT. HTTP readings are evaluated when the
    // service flag is on or the farm has configured its own alert rules for this device.
    await evaluateAlertsForDevice(registryDevice, httpTelemetryEvent({
      id: row.id, deviceId: row.device_id, temperature: temp, humidity: hum,
      soilMoisture: soil, receivedAt: now.toISOString()
    }), { force: config.httpTelemetryAlertsEnabled });

    // Remote configuration rides on the ingestion response; the device echoes config_version once applied.
    const pendingConfig = await pendingConfigFor(pool, postgresSchema, ingestDevice, reportedConfigVersion)
      .catch((error) => { console.warn("[config] Delivery lookup failed (non-fatal):", error.message); return null; });

    return response.status(201).json(pendingConfig ? { ...formatRow(row), config: pendingConfig } : formatRow(row));
  } catch (error) {
    console.error("Sensor data POST failed", error);
    return response.status(500).json({ error: "Failed to store sensor data" });
  }
});

/**
 * GET /api/v1/sensors/latest
 * Returns the most recent reading(s).
 */
app.get("/api/v1/sensors/latest", async (request, response) => {
  try {
    const { device_id } = request.query;

    let result;
    if (device_id) {
      result = await pool.query(
        `SELECT id, device_id, temperature, humidity, soil_moisture, "timestamp", soil_raw, soil_status, dht_status
         FROM ${telemetryTable}
         WHERE LOWER(device_id) = LOWER($1) AND source = 'DEVICE'
         ORDER BY "timestamp" DESC
         LIMIT 1`,
        [device_id]
      );

      if (result.rows.length === 0) {
        return response
          .status(404)
          .json({ error: "No data found for the specified device" });
      }
      return response.json(formatRow(result.rows[0]));
    }

    result = await pool.query(
      `SELECT DISTINCT ON (device_id)
         id, device_id, temperature, humidity, soil_moisture, "timestamp", soil_raw, soil_status, dht_status
       FROM ${telemetryTable}
       WHERE source = 'DEVICE' AND ${NOT_TEST_OR_DELETED_DEVICE}
       ORDER BY device_id, "timestamp" DESC`
    );
    return response.json(result.rows.map(formatRow));
  } catch (error) {
    console.error("Sensor latest GET failed", error);
    return response.status(500).json({ error: "Failed to retrieve sensor data" });
  }
});

/**
 * GET /api/v1/sensors/history
 * Returns paginated historical sensor readings.
 */
app.get("/api/v1/sensors/history", async (request, response) => {
  try {
    const { device_id, from, to } = request.query;
    const limit = Math.min(parseInt(request.query.limit, 10) || 50, 1000);
    const offset = Math.max(parseInt(request.query.offset, 10) || 0, 0);

    const conditions = [];
    const params = [];

    if (device_id) {
      params.push(device_id);
      conditions.push(`LOWER(device_id) = LOWER($${params.length})`);
    } else {
      conditions.push(NOT_TEST_OR_DELETED_DEVICE);
    }
    if (from) {
      const fromDate = new Date(from);
      if (Number.isNaN(fromDate.getTime())) {
        return response
          .status(400)
          .json({ error: "from must be a valid ISO-8601 date" });
      }
      params.push(fromDate.toISOString());
      conditions.push(`"timestamp" >= $${params.length}`);
    }
    if (to) {
      const toDate = new Date(to);
      if (Number.isNaN(toDate.getTime())) {
        return response
          .status(400)
          .json({ error: "to must be a valid ISO-8601 date" });
      }
      params.push(toDate.toISOString());
      conditions.push(`"timestamp" <= $${params.length}`);
    }

    const where =
      conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";
    params.push(limit, offset);

    const result = await pool.query(
      `SELECT id, device_id, temperature, humidity, soil_moisture, "timestamp", source, soil_raw, soil_status, dht_status
       FROM ${telemetryTable}
       ${where}
       ORDER BY "timestamp" DESC
       LIMIT $${params.length - 1} OFFSET $${params.length}`,
      params
    );

    return response.json({
      data: result.rows.map(formatRow),
      limit,
      offset,
      count: result.rows.length
    });
  } catch (error) {
    console.error("Sensor history GET failed", error);
    return response
      .status(500)
      .json({ error: "Failed to retrieve sensor history" });
  }
});

// Export compliance assessment routes
app.use("/api/v1/export-assessment", createAssessmentRouter({
  pool,
  schema: postgresSchema,
}));

// Public traceability — no authentication required, exposed by gateway
app.use("/api/v1/public", createPublicRouter({ pool, schema: postgresSchema }));

// Camera routes — mounted after static so /api/v1/camera/image/:fn is handled by the router
app.use("/api/v1/camera", createCameraRouter({
  pool,
  schema:        postgresSchema,
  serverBaseUrl: config.serverBaseUrl,
  uploadsDir:    config.uploadsDir,
  aiServiceUrl:  config.aiServiceUrl,
}));

// ── MQTT telemetry ingestion ──────────────────────────────────────────────────

async function handleTelemetry(topic, payload) {
  const telemetry = JSON.parse(payload.toString());
  const deviceId = topic.split("/")[2];
  if (!deviceId) {
    throw new Error(`Unable to extract device ID from MQTT topic: ${topic}`);
  }

  const measuredAt = parseTimestamp(telemetry.timestamp);
  const event = {
    deviceId,
    temperature: parseOptionalNumber(telemetry.temperature, "temperature"),
    humidity: parseOptionalNumber(telemetry.humidity, "humidity"),
    light: parseOptionalNumber(telemetry.light, "light"),
    soilMoisture: parseOptionalNumber(
      telemetry.soil_moisture ?? telemetry.soilMoisture,
      "soil_moisture"
    ),
    timestamp: measuredAt.toISOString(),
    receivedAt: new Date().toISOString()
  };

  if (
    event.temperature === null &&
    event.humidity === null &&
    event.light === null &&
    event.soilMoisture === null
  ) {
    throw new Error("Telemetry must include at least one measurement");
  }

  const ingestDevice = await findIngestDevice(event.deviceId);
  if (!ingestDevice || ingestDevice.provisioning_status !== "CLAIMED") {
    throw new Error(`Telemetry dropped: device ${event.deviceId} is not registered and claimed`);
  }
  const insertResult = await pool.query(
    `INSERT INTO ${telemetryTable}
      (device_id, temperature, humidity, light, soil_moisture, "timestamp", received_at, farm_id, cultivation_area_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
     RETURNING id`,
    [
      ingestDevice.device_uid,
      event.temperature,
      event.humidity,
      event.light,
      event.soilMoisture,
      event.timestamp,
      event.receivedAt,
      ingestDevice.farm_id,
      ingestDevice.cultivation_area_id
    ]
  );
  event.id = insertResult.rows[0].id;

  const registryDevice = await recordDeviceSeen({
    pool, devicesTable, deviceId: event.deviceId, receivedAt: event.receivedAt
  });

  if (kafkaProducerReady) {
    try {
      await producer.send({
        topic: config.kafkaTopic,
        messages: [{ key: deviceId, value: JSON.stringify(event) }]
      });
    } catch (kafkaErr) {
      console.warn("[kafka] Publish failed (non-fatal):", kafkaErr.message);
    }
  }

  await evaluateAlertsForDevice(registryDevice, event, { force: true });
}

const THRESHOLD_ALERT_TYPES = Object.keys(METRICS).flatMap((metric) => [`${metric}_HIGH`, `${metric}_LOW`]);

/**
 * Effective rules: device > zone > farm > service default. `force` evaluates even without
 * farm-defined rules (MQTT path, or HTTP with IOT_HTTP_TELEMETRY_ALERTS_ENABLED). A device whose
 * configuration turns alerts off has its open threshold alerts closed and none opened.
 */
async function evaluateAlertsForDevice(device, telemetry, { force = false } = {}) {
  if (!device) return;
  try {
    const rules = await loadRulesForDevice(pool, postgresSchema, device);
    const { checks, disabledMetrics, explicit } = effectiveChecks(device, rules, config.iotThresholds);
    if (!force && !explicit) return;
    const alertsOn = await deviceAlertsEnabled(pool, postgresSchema, device);
    await evaluateTelemetryAlerts({
      alertsTable,
      checks: alertsOn ? checks : [],
      device,
      disabledTypes: alertsOn ? disabledMetrics.flatMap((m) => [`${m}_HIGH`, `${m}_LOW`]) : THRESHOLD_ALERT_TYPES,
      farmAccessClient,
      notificationPolicy: config.notificationPolicy,
      notificationPublisher,
      pool,
      telemetry,
      thresholds: config.iotThresholds,
      logger: console
    });
  } catch (error) {
    console.error("IoT alert evaluation failed", error);
  }
}

/** Registry row for ingestion (deleted devices are unknown). */
async function findIngestDevice(deviceUid) {
  const result = await pool.query(
    `SELECT id, device_uid, name, farm_id, cultivation_area_id, status, provisioning_status, firmware_version, device_type,
            is_test
     FROM ${devicesTable}
     WHERE LOWER(device_uid) = LOWER($1) AND status <> 'DELETED'
     LIMIT 1`,
    [deviceUid]
  );
  return result.rows[0] || null;
}

/** Optional device report fields: firmware_version (string) and config_version (applied config). */
function parseDeviceReport(body) {
  const report = { firmwareVersion: null, reportedConfigVersion: null };
  const firmware = body?.firmware_version;
  if (firmware !== undefined && firmware !== null && firmware !== "") {
    if (typeof firmware !== "string" || !/^[A-Za-z0-9._+-]{1,40}$/.test(firmware.trim())) {
      throw new Error("firmware_version must be 1-40 characters of letters, digits, dot, dash, plus or underscore");
    }
    report.firmwareVersion = firmware.trim();
  }
  const version = body?.config_version;
  if (version !== undefined && version !== null && version !== "") {
    const parsed = Number(version);
    if (!Number.isInteger(parsed) || parsed < 0) throw new Error("config_version must be a non-negative integer");
    report.reportedConfigVersion = parsed;
  }
  return report;
}

async function recordFirmwareVersion(device, firmwareVersion) {
  if (!device || !firmwareVersion || device.firmware_version === firmwareVersion) return;
  await pool.query(
    `UPDATE ${devicesTable} SET firmware_version = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $1`,
    [device.id, firmwareVersion]
  ).catch((error) => console.warn("[firmware] Version update failed (non-fatal):", error.message));
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseOptionalNumber(value, fieldName) {
  if (value === undefined || value === null || value === "") {
    return null;
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`${fieldName} must be a finite number`);
  }
  return parsed;
}

function parseTimestamp(value) {
  const timestamp = value ? new Date(value) : new Date();
  if (Number.isNaN(timestamp.getTime())) {
    throw new Error("timestamp must be a valid ISO-8601 value");
  }
  return timestamp;
}

function computeStatus(soilMoisture) {
  if (soilMoisture === null || soilMoisture === undefined) return null;
  if (soilMoisture < 60) return "DRY_WARNING";
  if (soilMoisture > 85) return "WET_WARNING";
  return "OPTIMAL";
}

function formatRow(row) {
  const soil =
    row.soil_moisture !== null && row.soil_moisture !== undefined
      ? Number(row.soil_moisture)
      : null;
  const humidity =
    row.humidity !== null && row.humidity !== undefined
      ? Number(row.humidity)
      : null;
  return {
    id: row.id,
    device_id: row.device_id,
    temperature:
      row.temperature !== null && row.temperature !== undefined
        ? Number(row.temperature)
        : null,
    air_humidity: humidity,
    humidity,
    soil_moisture: soil,
    // Raw ADC value and the firmware's own verdict; soil_moisture is null unless soil_status is OK/NULL.
    soil_raw: row.soil_raw ?? null,
    soil_status: row.soil_status ?? null,
    dht_status: row.dht_status ?? null,
    status: computeStatus(soil),
    timestamp: row.timestamp,
    // DEVICE = measured by the sensor; SYNTHETIC_BACKFILL = labelled simulated history.
    source: row.source ?? "DEVICE"
  };
}

// ── Startup / shutdown ────────────────────────────────────────────────────────

async function start() {
  await pool.query("SELECT 1");

  await ensureReadSchema(pool, devicesTable);
  await ensureAlertSchema(pool, alertsTable);
  await ensureTelemetryProvenanceSchema(pool, postgresSchema);
  await ensureSensorQualitySchema(pool, postgresSchema);
  await ensureCameraHealthSchema(pool, postgresSchema);
  await ensureDeviceLifecycleSchema(pool, postgresSchema);

  // Kafka is optional — if the broker is not reachable the service stays up, skips event
  // publishing meanwhile and keeps retrying in the background (see kafkaConnect.js).
  kafkaConnector = connectWithRetry({
    connect: () => producer.connect(),
    onReady: () => { kafkaProducerReady = true; },
  });
  await kafkaConnector.started;

  offlineWatchdogScheduler = startOfflineWatchdogScheduler({
    alertsTable,
    devicesTable,
    evaluateOfflineDevices,
    farmAccessClient,
    healthConfig: config.iotHealth,
    notificationPolicy: config.notificationPolicy,
    notificationPublisher,
    pool,
    retryNotifications
  });

  await loadCameraSchedules({
    pool,
    schema:        postgresSchema,
    serverBaseUrl: config.serverBaseUrl,
    uploadsDir:    config.uploadsDir,
    aiServiceUrl:  config.aiServiceUrl,
  });

  // Offline detection: every 5 min mark cameras silent for longer than the
  // configured timeout as offline.
  cameraOfflineTask = cron.schedule("*/5 * * * *", () => backgroundWork.run(async () => {
    try {
      const count = await markOfflineStaleCameras(
        pool, postgresSchema, config.cameraOfflineTimeoutMinutes
      );
      if (count > 0) {
        console.log(`[camera-offline] Marked ${count} camera(s) offline`);
      }
    } catch (err) {
      console.warn("[camera-offline] Offline detection failed (non-fatal):", err.message);
    }
  }));

  mqttClient = mqtt.connect(config.mqttUrl, {
    clientId: `duriancare-iot-${process.pid}`
  });
  mqttClient.on("connect", () => mqttClient.subscribe(config.mqttTopic));
  mqttClient.on("message", (topic, payload) => {
    backgroundWork.run(() => handleTelemetry(topic, payload)).catch((error) => {
      console.error("Telemetry ingestion failed", error);
    });
  });
  mqttClient.on("error", (error) =>
    console.error("MQTT connection failed", error)
  );

  httpServer = app.listen(config.port, () => {
    console.log(`DurianCare IoT service listening on port ${config.port}`);
    udpDiscovery = createUdpDiscoveryService({ pool, schema: postgresSchema, servicePort: httpServer.address().port });
    void udpDiscovery.startUdpDiscovery().catch(() => console.warn("[udp-discovery] Startup degraded"));
  });
  httpServer.on("error", () => { console.error("IoT HTTP listener failed"); void shutdown(1); });
}

async function shutdown(exitCode = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  // Docker grants 90s; bound shutdown when a dependency never settles.
  const deadline = setTimeout(() => {
    console.error("IoT shutdown deadline exceeded; unfinished work was not confirmed");
    process.exit(1);
  }, 75000);
  backgroundWork.stop();
  kafkaConnector?.stop();
  cameraOfflineTask?.stop();
  offlineWatchdogScheduler?.stop();
  const scheduledDrain = stopCameraSchedules();
  const discoveryDrain = udpDiscovery?.stopUdpDiscovery();
  const mqttDrain = mqttClient ? new Promise(resolve => mqttClient.end(false, {}, resolve)) : Promise.resolve();
  if (httpServer) {
    httpServer.closeIdleConnections?.();
    await new Promise(resolve => {
      httpServer.close(resolve);
    });
  }
  await Promise.allSettled([scheduledDrain, discoveryDrain, mqttDrain,
    backgroundWork.drain(), offlineWatchdogScheduler?.drain?.()]);
  await drainCaptures();
  await producer.disconnect().catch(() => {});
  await pool.end();
  clearTimeout(deadline);
  process.exit(exitCode === 1 ? 1 : 0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

start().catch((error) => {
  console.error("IoT service failed to start", error);
  process.exit(1);
});
