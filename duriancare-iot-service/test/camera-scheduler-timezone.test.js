"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const Module = require("node:module");

test("scheduled captures run in Vietnam local time, not container UTC", async t => {
  const original = Module._load;
  const calls = [];
  t.mock.method(Module, "_load", function(id, parent, main) {
    if (parent?.filename.endsWith("cameraScheduler.js")) {
      if (id === "node-cron") return {validate: () => true, schedule: (expr, _fn, options) => {
        calls.push({expr, options}); return {stop: () => {}};
      }};
      if (id === "./cameraService") return {captureAndStore: async () => {}};
      if (id === "./deviceService") return {resolveCameraUrl: async () => ({url: "http://fixture.local", online: true, found: true})};
      if (id === "../config") return {getCameraConfig: () => ({esp32CapturePath: "/capture"})};
    }
    return original.call(this, id, parent, main);
  });
  delete require.cache[require.resolve("../src/camera/cameraScheduler")];
  const scheduler = require("../src/camera/cameraScheduler");

  const pool = {query: async () => ({rows: [{device_id: "fixture", cron_expression: "0 6 * * *"}]})};
  await scheduler.loadAll({pool, schema: "iot"});
  scheduler.reschedule({deviceId: "fixture", schedules: [{enabled: true, cron_expression: "0 16 * * *"}]});

  assert.deepEqual(calls.map(c => c.expr), ["0 6 * * *", "0 16 * * *"]);
  for (const c of calls) assert.equal(c.options?.timezone, "Asia/Ho_Chi_Minh");
  await scheduler.stopAll();
});

test("installed node-cron honours the timezone option (06:00 Vietnam = 23:00Z previous day)", () => {
  const TimeMatcher = require("node-cron/src/time-matcher");
  const vn = new TimeMatcher("0 6 * * *", "Asia/Ho_Chi_Minh");
  assert.equal(vn.match(new Date("2026-10-18T23:00:00Z")), true);
  assert.equal(vn.match(new Date("2026-10-19T06:00:00Z")), false);
});
