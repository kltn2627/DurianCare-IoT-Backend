"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { connectWithRetry } = require("../src/kafkaConnect");

const quietLog = { log() {}, warn() {} };

function manualTimers() {
  const pending = [];
  return {
    pending,
    setTimer: (fn, ms) => { const t = { fn, ms }; pending.push(t); return t; },
    clearTimer: t => { const i = pending.indexOf(t); if (i >= 0) pending.splice(i, 1); },
    async fire() { const t = pending.shift(); t.fn(); await new Promise(r => setImmediate(r)); },
  };
}

test("broker unavailable at startup becomes ready after background retry with capped backoff", async () => {
  const timers = manualTimers();
  let calls = 0, ready = false;
  const c = connectWithRetry({ connect: async () => { if (++calls < 4) throw new Error("ECONNREFUSED"); },
    onReady: () => { ready = true; }, log: quietLog, minMs: 5, maxMs: 12, ...timers });
  await c.started;
  assert.equal(ready, false);
  const delays = [];
  while (!ready) { delays.push(timers.pending[0].ms); await timers.fire(); }
  assert.equal(calls, 4);
  assert.deepEqual(delays, [5, 10, 12]);
  assert.equal(timers.pending.length, 0);
});

test("first-try success needs no timer; stop() cancels pending retry", async () => {
  const timers = manualTimers();
  let ready = false;
  await connectWithRetry({ connect: async () => {}, onReady: () => { ready = true; }, log: quietLog, ...timers }).started;
  assert.equal(ready, true);
  assert.equal(timers.pending.length, 0);

  let calls = 0;
  const c = connectWithRetry({ connect: async () => { calls++; throw new Error("down"); },
    onReady: () => assert.fail("must not become ready"), log: quietLog, ...timers });
  await c.started;
  assert.equal(timers.pending.length, 1);
  c.stop();
  assert.equal(timers.pending.length, 0);
  assert.equal(calls, 1);
});
