"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createWorkDrain } = require("../src/workDrain");
test("stop rejects new jobs and drain waits for accepted jobs, including failures", async () => {
  const work = createWorkDrain();
  let release, finished = false, late = false;
  const accepted = work.run(() => new Promise(resolve => { release = resolve; }));
  await Promise.resolve();
  work.stop();
  await work.run(() => { late = true; });
  const drained = work.drain().then(() => { finished = true; });
  await Promise.resolve();
  assert.equal(finished, false);
  assert.equal(late, false);
  release(); await accepted; await drained;
  assert.equal(finished, true);
});

test("scheduler stops all cron tasks and drains an in-flight capture without accepting another", async t => {
  const Module = require("node:module");
  const original = Module._load;
  let callback, stopped = 0, captures = 0, release;
  t.mock.method(Module, "_load", function(id, parent, main) {
    if (parent?.filename.endsWith("cameraScheduler.js")) {
      if (id === "node-cron") return {validate: () => true, schedule: (_expr, fn) => {
        callback = fn; return {stop: () => { stopped++; }};
      }};
      if (id === "./cameraService") return {captureAndStore: async () => {
        captures++; await new Promise(resolve => { release = resolve; });
      }};
      if (id === "./deviceService") return {resolveCameraUrl: async () => ({url: "http://fixture.local", online: true, found: true})};
      if (id === "../config") return {getCameraConfig: () => ({esp32CapturePath: "/capture"})};
    }
    return original.call(this, id, parent, main);
  });
  const scheduler = require("../src/camera/cameraScheduler");
  scheduler.reschedule({deviceId: "fixture", schedules: [{enabled: true, cron_expression: "* * * * *"}]});
  const job = callback();
  await new Promise(resolve => setImmediate(resolve));
  let drained = false;
  const stop = scheduler.stopAll().then(() => { drained = true; });
  await callback();
  assert.equal(captures, 1); assert.equal(stopped, 1); assert.equal(drained, false);
  release(); await job; await stop; assert.equal(drained, true);
});
