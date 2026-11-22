require("dotenv").config();
let warnedLegacyCamera = false;

// Registry is authoritative. Legacy configuration is an explicit opt-in only.
function getCameraConfig() {
  if (!warnedLegacyCamera && (process.env.ESP32_CAMERA_URL || process.env.DEFAULT_ESP32_IP || process.env.CAMERA_REGISTRATION_KEY)) {
    console.warn("[camera] Legacy address/shared-key configuration is deprecated; use registry and device bindings.");
    warnedLegacyCamera = true;
  }
  return {
    esp32CameraUrl: process.env.CAMERA_LEGACY_ENABLED === "true"
      ? (process.env.ESP32_CAMERA_URL || "").replace(/\/$/, "") : null,
    esp32CapturePath: (process.env.ESP32_CAPTURE_PATH || "/capture").replace(/^([^/])/, "/$1"),
  };
}

const DEFAULT_EXPECTED_TELEMETRY_INTERVAL_SECONDS = 60;
const DEFAULT_STALE_AFTER_INTERVALS = 3;
// 60 s x 5 = 5 minutes without data -> OFFLINE / DEVICE_OFFLINE (paper: "không gửi quá 5 phút").
const DEFAULT_OFFLINE_AFTER_INTERVALS = 5;

function positiveNumber(name, fallback) {
  const raw = process.env[name];
  const value = raw === undefined || raw === "" ? fallback : Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return value;
}

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

// LOCAL: backend on the farm LAN (beacon discovery, legacy devices without a token accepted).
// CLOUD: backend on the internet behind HTTPS; every sensor must send its device token.
function deploymentMode(env = process.env) {
  const mode = String(env.IOT_DEPLOYMENT_MODE || "LOCAL").trim().toUpperCase();
  if (!["LOCAL", "CLOUD"].includes(mode)) throw new Error("IOT_DEPLOYMENT_MODE must be LOCAL or CLOUD");
  return mode;
}

function deviceAuthMode(env = process.env) {
  const raw = String(env.IOT_DEVICE_AUTH_MODE || "").trim().toUpperCase();
  if (!raw) return deploymentMode(env) === "CLOUD" ? "REQUIRED" : "OPTIONAL";
  if (!["OPTIONAL", "REQUIRED"].includes(raw)) throw new Error("IOT_DEVICE_AUTH_MODE must be OPTIONAL or REQUIRED");
  return raw;
}

// PUSH: the camera uploads frames and polls commands (works behind NAT / in the cloud).
// PULL: legacy — the backend downloads GET <camera LAN origin>/capture. Only allowed in LOCAL mode.
function cameraDeliveryConfig(env = process.env) {
  const mode = String(env.CAMERA_DELIVERY_MODE || "PUSH").trim().toUpperCase();
  if (!["PUSH", "PULL"].includes(mode)) throw new Error("CAMERA_DELIVERY_MODE must be PUSH or PULL");
  const local = deploymentMode(env) === "LOCAL";
  const integer = (name, fallback, min, max) => {
    const raw = env[name];
    const value = raw === undefined || raw === "" ? fallback : Number(raw);
    if (!Number.isInteger(value) || value < min || value > max) throw new Error(`${name} must be an integer ${min}-${max}`);
    return value;
  };
  return {
    mode: mode === "PULL" && !local ? "PUSH" : mode,
    // Legacy pull for a camera whose firmware does not report the "push" capability (LOCAL only).
    pullFallback: local && env.CAMERA_PULL_FALLBACK === "true",
    frameMaxBytes: integer("CAMERA_FRAME_MAX_BYTES", 2 * 1024 * 1024, 16 * 1024, 8 * 1024 * 1024),
    onDemandTtlSeconds: integer("CAMERA_COMMAND_TTL_SECONDS", 120, 10, 3600),
    scheduledTtlSeconds: integer("CAMERA_SCHEDULED_COMMAND_TTL_SECONDS", 600, 30, 86400),
    pollMs: integer("CAMERA_COMMAND_POLL_MS", 5000, 1000, 60000),
  };
}

module.exports = {
  deploymentMode: deploymentMode(),
  deviceAuthMode: deviceAuthMode(),
  cameraDelivery: cameraDeliveryConfig(),
  getDeploymentMode: deploymentMode,
  getDeviceAuthMode: deviceAuthMode,
  getCameraDeliveryConfig: cameraDeliveryConfig,
  // Public base URL devices use (documentation / setup screen only), e.g. https://api.duriancare.io.vn/iot
  publicBaseUrl: (process.env.IOT_PUBLIC_BASE_URL || "").trim().replace(/\/$/, ""),
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
  // Same precedence as notification-service's consumer (KAFKA_ALERT_TOPIC, then NOTIFICATION_EVENTS_TOPIC).
  notificationTopic: process.env.KAFKA_ALERT_TOPIC || process.env.NOTIFICATION_EVENTS_TOPIC || "duriancare.notification.events",
  // Alert notification delivery: bounded retry with exponential backoff (see src/alertNotifications.js).
  notificationPolicy: {
    maxAttempts: positiveNumber("IOT_NOTIFICATION_MAX_ATTEMPTS", 8),
    baseDelayMs: positiveNumber("IOT_NOTIFICATION_RETRY_BASE_SECONDS", 30) * 1000,
    maxDelayMs: positiveNumber("IOT_NOTIFICATION_RETRY_MAX_SECONDS", 1800) * 1000,
    leaseMs: positiveNumber("IOT_NOTIFICATION_LEASE_SECONDS", 120) * 1000,
    // Optional test account for is_test devices; empty = their alerts are stored but notify nobody.
    testReceiverId: (process.env.IOT_TEST_NOTIFICATION_RECEIVER_ID || "").trim(),
  },
  esp32CameraUrl: getCameraConfig().esp32CameraUrl,
  esp32CapturePath:  (process.env.ESP32_CAPTURE_PATH  || "/capture").replace(/^([^/])/, "/$1"),
  serverBaseUrl:  (process.env.SERVER_BASE_URL  || "http://localhost:8080").replace(/\/$/, ""),
  aiServiceUrl:   (process.env.AI_SERVICE_URL   || "http://localhost:8000").replace(/\/$/, ""),
  uploadsDir: process.env.UPLOADS_DIR || "./uploads",
  cameraRegistrationKey: process.env.CAMERA_REGISTRATION_KEY || "",
  cameraOfflineTimeoutMinutes: positiveNumber("CAMERA_OFFLINE_TIMEOUT_MIN", 5),
  getCameraConfig,
  iotHealth: {
    expectedTelemetryIntervalSeconds,
    offlineAfterIntervals,
    offlineWatchdogIntervalSeconds,
    staleAfterIntervals
  },
  // Threshold alerts for HTTP POST /api/v1/sensors/data readings (MQTT readings are always evaluated).
  // Off by default: enabling it starts real notifications for HTTP-only sensors such as esp32-01.
  httpTelemetryAlertsEnabled: process.env.IOT_HTTP_TELEMETRY_ALERTS_ENABLED === "true",
  // Lifetime of a device claim code issued at provisioning (single use, stored hashed).
  claimCodeTtlHours: positiveNumber("IOT_CLAIM_CODE_TTL_HOURS", 72),
  iotThresholds: {
    temperatureHigh: Number(process.env.IOT_TEMPERATURE_HIGH || 38),
    temperatureLow: Number(process.env.IOT_TEMPERATURE_LOW || 15),
    humidityHigh: Number(process.env.IOT_HUMIDITY_HIGH || 95),
    humidityLow: Number(process.env.IOT_HUMIDITY_LOW || 45),
    lightHigh: Number(process.env.IOT_LIGHT_HIGH || 4095),
    lightLow: Number(process.env.IOT_LIGHT_LOW || 0)
  }
};
