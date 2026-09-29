require("dotenv").config();

const DEFAULT_EXPECTED_TELEMETRY_INTERVAL_SECONDS = 60;
const DEFAULT_STALE_AFTER_INTERVALS = 3;
const DEFAULT_OFFLINE_AFTER_INTERVALS = 10;

const expectedTelemetryIntervalSeconds = positiveNumber(
  "IOT_EXPECTED_TELEMETRY_INTERVAL_SECONDS",
  DEFAULT_EXPECTED_TELEMETRY_INTERVAL_SECONDS
);
const staleAfterIntervals = positiveNumber("IOT_STALE_AFTER_INTERVALS", DEFAULT_STALE_AFTER_INTERVALS);
const offlineAfterIntervals = positiveNumber("IOT_OFFLINE_AFTER_INTERVALS", DEFAULT_OFFLINE_AFTER_INTERVALS);
if (staleAfterIntervals >= offlineAfterIntervals) {
  throw new Error("IOT_STALE_AFTER_INTERVALS must be lower than IOT_OFFLINE_AFTER_INTERVALS");
}
const offlineWatchdogIntervalSeconds = positiveNumber(
  "IOT_OFFLINE_WATCHDOG_INTERVAL_SECONDS",
  expectedTelemetryIntervalSeconds
);

function positiveNumber(name, fallback) {
  const raw = process.env[name];
  const value = raw === undefined || raw === "" ? fallback : Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return value;
}

module.exports = {
  port: Number(process.env.PORT || 3001),
  mqttUrl: process.env.MQTT_URL || "mqtt://localhost:1883",
  mqttTopic: process.env.MQTT_TOPIC || "duriancare/devices/+/telemetry",
  postgres: {
    host: process.env.POSTGRES_HOST || "localhost",
    port: Number(process.env.POSTGRES_PORT || 5432),
    database: process.env.POSTGRES_DB || "duriancare",
    user: process.env.POSTGRES_USER || "duriancare",
    password: process.env.POSTGRES_PASSWORD || "",
    schema: process.env.POSTGRES_SCHEMA || "duriancare_iot",
    max: Number(process.env.POSTGRES_POOL_MAX || 10),
    idleTimeoutMillis: Number(process.env.POSTGRES_IDLE_TIMEOUT_MS || 30000),
    connectionTimeoutMillis: Number(
      process.env.POSTGRES_CONNECTION_TIMEOUT_MS || 5000
    )
  },
  farmServiceUrl: process.env.FARM_SERVICE_URL || "http://localhost:8082",
  internalToken: process.env.INTERNAL_SERVICE_TOKEN || "local-internal-token",
  kafkaBrokers: (process.env.KAFKA_BROKERS || "localhost:9092").split(","),
  kafkaTopic: process.env.KAFKA_TELEMETRY_TOPIC || "iot.telemetry.received",
  notificationTopic: process.env.NOTIFICATION_EVENTS_TOPIC || "duriancare.notification.events",
  iotHealth: {
    expectedTelemetryIntervalSeconds,
    offlineAfterIntervals,
    offlineWatchdogIntervalSeconds,
    staleAfterIntervals
  },
  iotThresholds: {
    temperatureHigh: Number(process.env.IOT_TEMPERATURE_HIGH || 38),
    temperatureLow: Number(process.env.IOT_TEMPERATURE_LOW || 15),
    humidityHigh: Number(process.env.IOT_HUMIDITY_HIGH || 95),
    humidityLow: Number(process.env.IOT_HUMIDITY_LOW || 45),
    lightHigh: Number(process.env.IOT_LIGHT_HIGH || 4095),
    lightLow: Number(process.env.IOT_LIGHT_LOW || 0)
  }
};
