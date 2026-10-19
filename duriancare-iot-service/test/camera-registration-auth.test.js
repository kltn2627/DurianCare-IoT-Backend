"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const express = require("express");
const crypto = require("node:crypto");
const { createRegistrationRouter } = require("../src/camera/registryRuntime");

test("registration distinguishes unavailable configuration from rejected credentials without leaking keys", async () => {
  const previous = process.env.CAMERA_DEVICE_BINDINGS;
  const key = crypto.randomBytes(32).toString("hex");
  let reads = 0;
  const app = express();
  app.use(express.json());
  app.use(createRegistrationRouter({schema: "fixture", pool: {query: async sql => {
    assert.match(sql, /^SELECT/);
    reads++;
    return {rows: []};
  }}}));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const post = (body, credential) => fetch(`http://127.0.0.1:${server.address().port}/register`, {
    method: "POST", headers: {"Content-Type": "application/json",
      ...(credential === undefined ? {} : {"X-Camera-Key": credential})}, body: JSON.stringify(body)
  });
  const body = {cameraId: "fixture", announcedAt: Date.now(), ipAddress: "10.2.3.4"};
  try {
    for (const value of ["", "{", "null", "[]", "{}", JSON.stringify({fixture: {key}})]) {
      process.env.CAMERA_DEVICE_BINDINGS = value;
      const response = await post(body, key);
      assert.equal(response.status, 503);
      assert.deepEqual(await response.json(), {error: "Camera registration configuration unavailable",
        code: "CAMERA_BINDINGS_UNAVAILABLE"});
    }
    process.env.CAMERA_DEVICE_BINDINGS = JSON.stringify({fixture: {key, farmId: "farm"}});
    for (const credential of [undefined, "incorrect"]) {
      assert.equal((await post(body, credential)).status, 401);
    }
    assert.equal((await post({...body, cameraId: "unknown"}, key)).status, 401);
    assert.equal(reads, 0);
    for (const announcedAt of [0, null, "now", Date.now() + 60000]) {
      assert.equal((await post({...body, announcedAt}, key)).status, 400);
    }
    assert.equal(reads, 0);
    assert.equal((await post(body, key)).status, 404);
    assert.equal(reads, 1);
  } finally {
    await new Promise(resolve => server.close(resolve));
    if (previous === undefined) delete process.env.CAMERA_DEVICE_BINDINGS;
    else process.env.CAMERA_DEVICE_BINDINGS = previous;
  }
});
