const path    = require("path");
const express = require("express");
const mqtt    = require("mqtt");
const cron    = require("node-cron");
const { Kafka } = require("kafkajs");
const { Pool }  = require("pg");
const config    = require("./config");
const { createCameraRouter } = require("./camera/cameraRoutes");
const { loadAll: loadCameraSchedules } = require("./camera/cameraScheduler");
const { markOfflineStaleCameras } = require("./camera/deviceService");
const { createAssessmentRouter } = require("./export/assessmentRoutes");
const { createPublicRouter }     = require("./public/publicRoutes");
const {
  ensureAlertSchema,
  evaluateOfflineDevices,
  evaluateTelemetryAlerts
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
let offlineWatchdogScheduler;

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
      notificationPublisher,
      pool
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
  devicesTable,
  farmAccessClient,
  healthConfig: config.iotHealth,
  pool,
  telemetryTable
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

    let temp, hum, soil;
    try {
      temp = parseOptionalNumber(temperature, "temperature");
      hum = parseOptionalNumber(humidity, "humidity");
      soil = parseOptionalNumber(soil_moisture, "soil_moisture");
    } catch (validationError) {
      return response.status(400).json({ error: validationError.message });
    }

    if (temp === null && hum === null && soil === null) {
      return response.status(400).json({
        error:
          "At least one measurement (temperature, humidity, soil_moisture) is required"
      });
    }

    const now = new Date();
    const insertResult = await pool.query(
      `INSERT INTO ${telemetryTable}
        (device_id, temperature, humidity, soil_moisture, "timestamp", received_at)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING id, device_id, temperature, humidity, soil_moisture, "timestamp"`,
      [device_id.trim(), temp, hum, soil, now.toISOString(), now.toISOString()]
    );

    const row = insertResult.rows[0];

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

    return response.status(201).json(formatRow(row));
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
        `SELECT id, device_id, temperature, humidity, soil_moisture, "timestamp"
         FROM ${telemetryTable}
         WHERE device_id = $1
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
         id, device_id, temperature, humidity, soil_moisture, "timestamp"
       FROM ${telemetryTable}
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
    const limit = Math.min(parseInt(request.query.limit, 10) || 50, 200);
    const offset = Math.max(parseInt(request.query.offset, 10) || 0, 0);

    const conditions = [];
    const params = [];

    if (device_id) {
      params.push(device_id);
      conditions.push(`device_id = $${params.length}`);
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
      `SELECT id, device_id, temperature, humidity, soil_moisture, "timestamp"
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

  const insertResult = await pool.query(
    `INSERT INTO ${telemetryTable}
      (device_id, temperature, humidity, light, soil_moisture, "timestamp", received_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING id`,
    [
      event.deviceId,
      event.temperature,
      event.humidity,
      event.light,
      event.soilMoisture,
      event.timestamp,
      event.receivedAt
    ]
  );
  event.id = insertResult.rows[0].id;

  const registryUpdate = await pool.query(
    `UPDATE ${devicesTable}
     SET last_seen_at = $2, updated_at = CURRENT_TIMESTAMP
     WHERE device_uid = $1
       AND status <> 'DELETED'
     RETURNING id, device_uid, name, farm_id, cultivation_area_id, status, last_seen_at`,
    [event.deviceId, event.receivedAt]
  );

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

  if (registryUpdate.rowCount > 0) {
    try {
      await evaluateTelemetryAlerts({
        alertsTable,
        device: registryUpdate.rows[0],
        farmAccessClient,
        notificationPublisher,
        pool,
        telemetry: event,
        thresholds: config.iotThresholds,
        logger: console
      });
    } catch (error) {
      console.error("IoT alert evaluation failed", error);
    }
  }
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
  if (soilMoisture < 30) return "DRY_WARNING";
  if (soilMoisture > 80) return "WET_WARNING";
  return "OPTIMAL";
}

function formatRow(row) {
  const soil =
    row.soil_moisture !== null && row.soil_moisture !== undefined
      ? Number(row.soil_moisture)
      : null;
  return {
    id: row.id,
    device_id: row.device_id,
    temperature:
      row.temperature !== null && row.temperature !== undefined
        ? Number(row.temperature)
        : null,
    air_humidity:
      row.humidity !== null && row.humidity !== undefined
        ? Number(row.humidity)
        : null,
    soil_moisture: soil,
    status: computeStatus(soil),
    timestamp: row.timestamp
  };
}

// ── Startup / shutdown ────────────────────────────────────────────────────────

async function start() {
  await pool.query("SELECT 1");

  await ensureReadSchema(pool, devicesTable);
  await ensureAlertSchema(pool, alertsTable);

  // Kafka is optional — if the broker is not reachable the service stays up and
  // skips event publishing rather than crashing (common in local dev without Kafka).
  try {
    await producer.connect();
    kafkaProducerReady = true;
    console.log("[kafka] Producer connected");
  } catch (err) {
    console.warn("[kafka] Broker unavailable — telemetry events will not be published:", err.message);
  }

  offlineWatchdogScheduler = startOfflineWatchdogScheduler({
    alertsTable,
    devicesTable,
    evaluateOfflineDevices,
    farmAccessClient,
    healthConfig: config.iotHealth,
    notificationPublisher,
    pool
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
  cron.schedule("*/5 * * * *", async () => {
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
  });

  mqttClient = mqtt.connect(config.mqttUrl, {
    clientId: `duriancare-iot-${process.pid}`
  });
  mqttClient.on("connect", () => mqttClient.subscribe(config.mqttTopic));
  mqttClient.on("message", (topic, payload) => {
    handleTelemetry(topic, payload).catch((error) => {
      console.error("Telemetry ingestion failed", error);
    });
  });
  mqttClient.on("error", (error) =>
    console.error("MQTT connection failed", error)
  );

  app.listen(config.port, () => {
    console.log(`DurianCare IoT service listening on port ${config.port}`);
  });
}

async function shutdown() {
  if (offlineWatchdogScheduler) {
    offlineWatchdogScheduler.stop();
  }
  if (mqttClient) {
    mqttClient.end(true);
  }
  await Promise.allSettled([producer.disconnect(), pool.end()]);
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

start().catch((error) => {
  console.error("IoT service failed to start", error);
  process.exit(1);
});
