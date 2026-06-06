const express = require("express");
const mqtt = require("mqtt");
const { Kafka } = require("kafkajs");
const { MongoClient } = require("mongodb");
const config = require("./config");

const app = express();
const mongoClient = new MongoClient(config.mongoUrl);
const kafka = new Kafka({
  clientId: "duriancare-iot-service",
  brokers: config.kafkaBrokers
});
const producer = kafka.producer();

let telemetryCollection;

app.get("/actuator/health", (_request, response) => {
  response.json({ status: "UP", service: "duriancare-iot-service" });
});

async function handleTelemetry(topic, payload) {
  const telemetry = JSON.parse(payload.toString());
  const deviceId = topic.split("/")[2];
  const event = {
    ...telemetry,
    deviceId,
    receivedAt: new Date()
  };

  await telemetryCollection.insertOne(event);
  await producer.send({
    topic: config.kafkaTopic,
    messages: [{ key: deviceId, value: JSON.stringify(event) }]
  });
}

async function start() {
  await mongoClient.connect();
  telemetryCollection = mongoClient
    .db(config.mongoDatabase)
    .collection("telemetry");
  await telemetryCollection.createIndex({ deviceId: 1, receivedAt: -1 });
  await producer.connect();

  const mqttClient = mqtt.connect(config.mqttUrl, {
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
  await Promise.allSettled([producer.disconnect(), mongoClient.close()]);
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

start().catch((error) => {
  console.error("IoT service failed to start", error);
  process.exit(1);
});
