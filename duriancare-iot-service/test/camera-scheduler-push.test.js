"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const Module = require("node:module");

// Regression: getCamera() did not select capabilities_reported, so the PUSH scheduler always logged
// "has no push-capable firmware" and never queued the 07:00/12:00/17:00 captures.

test("getCamera selects capabilities_reported", async () => {
  delete require.cache[require.resolve("../src/camera/deviceService")];
  const { getCamera } = require("../src/camera/deviceService");
  let sql = "";
  const pool = { query: async (text) => { sql = text; return { rows: [{ device_id: "cam", capabilities_reported: ["push"] }] }; } };
  const row = await getCamera(pool, "iot", "cam");
  assert.match(sql, /\bcapabilities_reported\b/);
  assert.deepEqual(row.capabilities_reported, ["push"]);
});

async function runScheduledTick(t, camera) {
  const original = Module._load;
  const enqueued = [];
  let tick = null;
  t.mock.method(Module, "_load", function (id, parent, main) {
    if (parent?.filename.endsWith("cameraScheduler.js")) {
      if (id === "node-cron") return { validate: () => true, schedule: (_expr, fn) => { tick = fn; return { stop: () => {} }; } };
      if (id === "./cameraService") return { captureAndStore: async () => { throw new Error("PUSH must not pull"); } };
      if (id === "./deviceService") return { getCamera: async () => camera, resolveCameraUrl: async () => ({}) };
      if (id === "./commandService") return { enqueueCapture: async (_pool, _schema, args) => { enqueued.push(args); return { command: { id: "c1" }, created: true }; } };
      if (id === "../config") return {
        getCameraConfig: () => ({ esp32CapturePath: "/capture" }),
        getCameraDeliveryConfig: () => ({ mode: "PUSH", pullFallback: false, scheduledTtlSeconds: 600 }),
      };
    }
    return original.call(this, id, parent, main);
  });
  delete require.cache[require.resolve("../src/camera/cameraScheduler")];
  const scheduler = require("../src/camera/cameraScheduler");
  t.mock.method(console, "log", () => {});
  scheduler.reschedule({ deviceId: "ESP32-CAM-001", schedules: [{ enabled: true, cron_expression: "0 12 * * *" }], pool: {}, schema: "iot" });
  assert.equal(typeof tick, "function");
  await tick();
  await scheduler.stopAll();
  return enqueued;
}

test("PUSH scheduler queues a SCHEDULED capture for push-capable firmware", async (t) => {
  const enqueued = await runScheduledTick(t, { device_id: "ESP32-CAM-001", capabilities_reported: ["push", "commands"] });
  assert.deepEqual(enqueued, [{ deviceId: "ESP32-CAM-001", reason: "SCHEDULED", ttlSeconds: 600 }]);
});

test("PUSH scheduler skips firmware that does not report push", async (t) => {
  const enqueued = await runScheduledTick(t, { device_id: "ESP32-CAM-001", capabilities_reported: null });
  assert.deepEqual(enqueued, []);
});
