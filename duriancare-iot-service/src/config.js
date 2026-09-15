require("dotenv").config();

// Re-reads ESP32_CAMERA_URL and ESP32_CAPTURE_PATH from .env on every call so
// you can update the IP without restarting the service — just save .env.
function getCameraConfig() {
  require("dotenv").config({ override: true });
  return {
    esp32CameraUrl:   (process.env.ESP32_CAMERA_URL   || "http://192.168.1.100").replace(/\/$/, ""),
    esp32CapturePath: (process.env.ESP32_CAPTURE_PATH || "/capture").replace(/^([^/])/, "/$1"),
  };
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
  kafkaBrokers: (process.env.KAFKA_BROKERS || "localhost:9092").split(","),
  kafkaTopic: process.env.KAFKA_TELEMETRY_TOPIC || "iot.telemetry.received",
  esp32CameraUrl:    (process.env.ESP32_CAMERA_URL    || "http://192.168.1.100").replace(/\/$/, ""),
  esp32CapturePath:  (process.env.ESP32_CAPTURE_PATH  || "/capture").replace(/^([^/])/, "/$1"),
  serverBaseUrl:  (process.env.SERVER_BASE_URL  || "http://localhost:8080").replace(/\/$/, ""),
  aiServiceUrl:   (process.env.AI_SERVICE_URL   || "http://localhost:8000").replace(/\/$/, ""),
  uploadsDir: process.env.UPLOADS_DIR || "./uploads",
  getCameraConfig,
};
