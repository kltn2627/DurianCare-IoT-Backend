require("dotenv").config();

module.exports = {
  port: Number(process.env.PORT || 3001),
  mqttUrl: process.env.MQTT_URL || "mqtt://localhost:1883",
  mqttTopic: process.env.MQTT_TOPIC || "duriancare/devices/+/telemetry",
  mongoUrl:
    process.env.MONGO_URL ||
    "mongodb://duriancare:duriancare_dev@localhost:27017/?authSource=admin",
  mongoDatabase: process.env.MONGO_DATABASE || "duriancare_iot",
  kafkaBrokers: (process.env.KAFKA_BROKERS || "localhost:9092").split(","),
  kafkaTopic: process.env.KAFKA_TELEMETRY_TOPIC || "iot.telemetry.received"
};
