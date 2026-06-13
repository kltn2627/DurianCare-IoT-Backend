require("dotenv").config();

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
  kafkaTopic: process.env.KAFKA_TELEMETRY_TOPIC || "iot.telemetry.received"
};
