// Child-process fixture only: boots the real Express app from src/main.js on an ephemeral port
// with in-memory pg/mqtt/kafka stand-ins, so POST /api/v1/sensors/data runs without a database.
const Module = require("node:module");
const { EventEmitter } = require("node:events");
const original = Module._load;
const middleware = (_req, _res, next) => next();
const inserts = [];
const queries = [];
class Pool extends EventEmitter {
  async query(sql, params) {
    queries.push(sql);
    // Registry lookup used by ingestion: "unknown-*" is unregistered, "unclaimed-*" is only provisioned.
    if (sql.includes("provisioning_status, firmware_version, device_type")) {
      const uid = String(params?.[0] || "");
      if (uid.startsWith("unknown")) return { rows: [], rowCount: 0 };
      return { rows: [{ id: "dev-1", device_uid: uid, name: null, farm_id: "farm-1", cultivation_area_id: "area-1",
        status: "ACTIVE", provisioning_status: uid.startsWith("unclaimed") || process.env.FIXTURE_UNCLAIMED ? "PROVISIONED" : "CLAIMED",
        firmware_version: null, device_type: "SENSOR" }], rowCount: 1 };
    }
    // Remote configuration waiting for the device: version 3, publish every 30 s.
    if (sql.includes("iot_device_configs WHERE device_id")) {
      return { rows: [{ device_id: "dev-1", config_version: 3, publish_interval_seconds: 30, alerts_enabled: true,
        delivery_status: "PENDING", applied_version: null }], rowCount: 1 };
    }
    if (sql.includes('"timestamp", source') && sql.includes("OFFSET")) {
      return { rows: [
        { id: "r1", device_id: "esp32-01", temperature: "31.2", humidity: "70.1", soil_moisture: null, timestamp: "2026-08-01T07:00:00Z", source: "SYNTHETIC_BACKFILL" },
        { id: "r2", device_id: "esp32-01", temperature: "34.0", humidity: "72.0", soil_moisture: "60", timestamp: "2026-08-12T05:00:00Z", source: "DEVICE" },
      ], rowCount: 2 };
    }
    if (sql.includes("INSERT INTO") && sql.includes("telemetry")) {
      inserts.push(params);
      const [device_id, temperature, humidity, soil_moisture, timestamp] = params;
      return { rows: [{ id: `row-${inserts.length}`, device_id, temperature, humidity, soil_moisture, timestamp,
        soil_raw: params[8] ?? null, soil_status: params[9] ?? null, dht_status: params[10] ?? null }], rowCount: 1 };
    }
    return { rows: [], rowCount: 0 };
  }
  async end() {}
}
const mqtt = { connect() { const c = new EventEmitter(); c.subscribe = () => {}; c.end = (_f, _o, done) => done(); return c; } };
const producer = { async connect() {}, async send() {}, async disconnect() {} };
Module._load = function(id, parent, main) {
  const mocks = {
    mqtt, pg: { Pool }, kafkajs: { Kafka: class { producer() { return producer; } } },
    "node-cron": { schedule() { return { stop() {} }; } },
    "./config": { postgres: { schema: "fixture" }, port: 0, kafkaBrokers: [], kafkaTopic: "t", iotHealth: {}, uploadsDir: ".",
      httpTelemetryAlertsEnabled: false, mqttUrl: "mqtt://fixture", mqttTopic: "t" },
    "./kafkaConnect": { connectWithRetry: () => ({ started: Promise.resolve(), stop: async () => {} }) },
    "./camera/cameraRoutes": { createCameraRouter: () => middleware },
    "./camera/cameraScheduler": { loadAll: async () => {}, stopAll: async () => {} },
    "./camera/cameraService": { drainCaptures: async () => {} },
    "./camera/deviceService": { ensureCameraHealthSchema: async () => undefined, markOfflineStaleCameras: async () => 0 },
    "./export/assessmentRoutes": { createAssessmentRouter: () => middleware },
    "./public/publicRoutes": { createPublicRouter: () => middleware },
    "./alert-engine": { ensureAlertSchema: async () => {} },
    "./read-api": { ensureReadSchema: async () => {}, registerReadApi() {}, createFarmAccessClient: () => ({}) },
    "./iot-runtime": { requireInternalToken: () => middleware, startOfflineWatchdogScheduler: () => ({ stop() {} }) },
    "./discovery/udpDiscoveryService": { createUdpDiscoveryService: () => ({
      async startUdpDiscovery() {},
      async stopUdpDiscovery() {},
    }) },
  };
  return Object.hasOwn(mocks, id) ? mocks[id] : original.call(this, id, parent, main);
};
// Report the real http.Server's bound port once it is listening.
const http = require("node:http");
const listen = http.Server.prototype.listen;
http.Server.prototype.listen = function(...args) {
  this.once("listening", () => process.send?.({ port: this.address().port }));
  return listen.apply(this, args);
};
process.on("message", message => {
  if (message === "inserts") process.send?.({ inserts });
  if (message === "queries") process.send?.({ queries });
});
