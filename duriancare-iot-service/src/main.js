const express = require("express");
const mqtt = require("mqtt");
const { Kafka } = require("kafkajs");
const { Pool } = require("pg");
const config = require("./config");
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
let offlineWatchdogScheduler;

app.use(express.json({ limit: "32kb" }));

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
    timestamp: measuredAt.toISOString(),
    receivedAt: new Date().toISOString()
  };

  if (
    event.temperature === null &&
    event.humidity === null &&
    event.light === null
  ) {
    throw new Error("Telemetry must include temperature, humidity or light");
  }

  const insertResult = await pool.query(
    `INSERT INTO ${telemetryTable}
      (device_id, temperature, humidity, light, "timestamp", received_at)
     VALUES ($1, $2, $3, $4, $5, $6)
     RETURNING id`,
    [
      event.deviceId,
      event.temperature,
      event.humidity,
      event.light,
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

  await producer.send({
    topic: config.kafkaTopic,
    messages: [{ key: deviceId, value: JSON.stringify(event) }]
  });

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

async function start() {
  await pool.query("SELECT 1");
  await ensureReadSchema(pool, devicesTable);
  await ensureAlertSchema(pool, alertsTable);
  await producer.connect();
  offlineWatchdogScheduler = startOfflineWatchdogScheduler({
    alertsTable,
    devicesTable,
    evaluateOfflineDevices,
    farmAccessClient,
    healthConfig: config.iotHealth,
    notificationPublisher,
    pool
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
  mqttClient.on("error", (error) => console.error("MQTT connection failed", error));

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
