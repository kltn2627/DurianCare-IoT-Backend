// Child-process fixture only; never loaded by the production entry point.
const Module = require("node:module");
const { EventEmitter } = require("node:events");
const original = Module._load;
const trace = name => process.stdout.write("FIXTURE:" + name + "\n");
const middleware = (_req, _res, next) => next();
if(process.env.FIXTURE_STUCK_SHUTDOWN) {
  const originalTimeout = global.setTimeout;
  global.setTimeout = (fn, ms, ...args) => originalTimeout(fn, ms === 75000 ? 50 : ms, ...args);
}
const app = { use() {}, get() {}, post() {}, listen(_port, callback) {
  trace("listen"); const server = new EventEmitter();
  server.address = () => ({ port: 3001 }); server.closeIdleConnections = () => {};
  server.close = done => { trace("http-close"); done(); };
  setImmediate(() => { if (process.env.FIXTURE_LISTEN_ERROR) server.emit("error", new Error("fixture bind failure")); else callback(); }); return server;
} };
const express = () => app; express.json = express.static = () => middleware;
class Pool extends EventEmitter {
  async query(sql) {
    if (process.env.FIXTURE_DELAYED_TELEMETRY && sql.includes("INSERT INTO")) {
      trace("mqtt-work-start");
      if(process.env.FIXTURE_STUCK_SHUTDOWN) await new Promise(() => {});
      await new Promise(resolve => setTimeout(resolve, 75));
      trace("mqtt-work-persisted");
      return {rows:[{id:"fixture-event"}]};
    }
    return {rows:[],rowCount:0};
  }
  async end() { trace("db-end"); }
}
let fixtureMqtt;
const mqtt = { connect() {
  const client = fixtureMqtt = new EventEmitter();
  client.end = (_force, _options, done) => {
    trace("mqtt-end");
    if(process.env.FIXTURE_DELAYED_TELEMETRY) client.emit("message","duriancare/devices/fixture/telemetry",Buffer.from('{"temperature":30}'));
    done();
  };
  return client;
} };
const producer = { async connect() {}, async send() {}, async disconnect() { trace("kafka-end"); } };
Module._load = function(id, parent, main) {
  const mocks = {
    express, mqtt, pg: { Pool }, kafkajs: { Kafka: class { producer() { return producer; } } }, "node-cron": { schedule() {} },
    "./config": { postgres: { schema: "fixture" }, port: 3001, kafkaBrokers: [], iotHealth: {}, uploadsDir: "." },
    "./camera/cameraRoutes": { createCameraRouter: () => middleware },
    "./camera/cameraScheduler": { loadAll: async () => {}, stopAll: async () => trace("camera-stop") },
    "./camera/cameraService": { drainCaptures: async () => trace("capture-drain") },
    "./camera/deviceService": { markOfflineStaleCameras: async () => 0 },
    "./export/assessmentRoutes": { createAssessmentRouter: () => middleware },
    "./public/publicRoutes": { createPublicRouter: () => middleware },
    "./alert-engine": { ensureAlertSchema: async () => {} },
    "./read-api": { ensureReadSchema: async () => {}, registerReadApi() {}, createFarmAccessClient: () => ({}) },
    "./iot-runtime": { requireInternalToken: () => middleware, startOfflineWatchdogScheduler: () => ({ stop() { trace("watchdog-stop"); } }) },
    "./discovery/udpDiscoveryService": { createUdpDiscoveryService: () => ({
      async startUdpDiscovery() {
        trace("discovery-start");
        if(process.env.FIXTURE_DELAYED_TELEMETRY) {
          fixtureMqtt.emit("message","duriancare/devices/fixture/telemetry",Buffer.from('{"temperature":30}'));
          await new Promise(resolve => setImmediate(resolve));
        }
        process.send?.("ready");
      },
      async stopUdpDiscovery() { trace("discovery-stop"); },
    }) },
  };
  return Object.hasOwn(mocks, id) ? mocks[id] : original.call(this, id, parent, main);
};
process.on("message", signal => { process.emit(signal); process.emit(signal); });
