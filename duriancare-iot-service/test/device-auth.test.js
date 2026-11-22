"use strict";
// Device token (CLOUD auth) and claim-code activation — fixture pool, no database.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const express = require("express");
const {
  authenticateSensor, createActivationRouter, hashDeviceSecret, secretMatches,
} = require("../src/devices/deviceAuth");
const { hashClaimCode } = require("../src/devices/deviceLifecycle");
const { measurementRangeError, parseOptionalRssi } = require("../src/sensorRanges");

test("sensor auth: legacy devices accepted only in OPTIONAL mode; issued tokens always required", () => {
  const legacy = { device_token_hash: null };
  const token = "a".repeat(43);
  const upgraded = { device_token_hash: hashDeviceSecret(token) };
  assert.equal(authenticateSensor(legacy, undefined, "OPTIONAL").ok, true);
  assert.equal(authenticateSensor(legacy, undefined, "REQUIRED").status, 401);
  assert.equal(authenticateSensor(legacy, "anything-long-enough", "OPTIONAL").status, 401);
  assert.equal(authenticateSensor(upgraded, undefined, "OPTIONAL").code, "DEVICE_TOKEN_REQUIRED");
  assert.equal(authenticateSensor(upgraded, "b".repeat(43), "REQUIRED").code, "DEVICE_TOKEN_INVALID");
  assert.deepEqual(authenticateSensor(upgraded, token, "REQUIRED"), { ok: true, authenticated: true });
  assert.equal(secretMatches(upgraded.device_token_hash, "short"), false);
});

test("ingestion ranges: TC39 humidity 100.1 and TC40 negative soil moisture are rejected before storage", () => {
  assert.equal(measurementRangeError({ humidity: 100.1 }).field, "humidity");
  assert.equal(measurementRangeError({ soil_moisture: -1 }).field, "soil_moisture");
  assert.equal(measurementRangeError({ temperature: 81 }).field, "temperature");
  assert.equal(measurementRangeError({ temperature: 30.5, humidity: 100, soil_moisture: 0 }), null);
  assert.equal(measurementRangeError({ temperature: null, humidity: null, soil_moisture: null }), null);
  assert.equal(parseOptionalRssi(-83), -83);
  assert.equal(parseOptionalRssi(undefined), null);
  assert.throws(() => parseOptionalRssi(5));
  assert.throws(() => parseOptionalRssi(-70.5));
});

function activationPool(state) {
  const client = {
    async query(sql, args) {
      const text = sql.trim();
      if (/^(BEGIN|COMMIT|ROLLBACK)/.test(text)) return { rows: [] };
      if (/FROM "fx".iot_devices/.test(text)) {
        return { rows: state.device && state.device.device_uid.toLowerCase() === args[0].toLowerCase() ? [state.device] : [] };
      }
      if (/FROM "fx".iot_device_claims\s+WHERE device_id = \$1 AND status IN/.test(text)) {
        return { rows: state.claim && !state.claim.activated_at ? [state.claim] : [] };
      }
      if (/FROM "fx".iot_device_claims WHERE device_id = \$1 AND activated_at IS NOT NULL/.test(text)) {
        return { rows: state.claim?.activated_at ? [state.claim] : [] };
      }
      if (/SET failed_attempts = failed_attempts \+ 1/.test(text)) { state.claim.failed_attempts++; return { rows: [] }; }
      if (/UPDATE "fx".iot_devices SET device_token_hash/.test(text)) { state.device.device_token_hash = args[1]; return { rows: [] }; }
      if (/SET activated_at = CURRENT_TIMESTAMP/.test(text)) { state.claim.activated_at = new Date(); return { rows: [] }; }
      throw new Error(`unexpected SQL ${text.slice(0, 60)}`);
    },
    release() {},
  };
  return { connect: async () => client };
}

test("activation exchanges the claim code once for a token; wrong codes count and lock", async () => {
  const code = "ABCDE-12345";
  const state = {
    device: { id: "11111111-1111-1111-1111-111111111111", device_uid: "esp32-01", device_type: "SENSOR" },
    claim: null,
  };
  state.claim = { id: "c1", status: "CLAIMED", failed_attempts: 0, activated_at: null,
    expires_at: new Date(Date.now() + 3600_000).toISOString(), code_hash: hashClaimCode(state.device.id, code) };
  const app = express();
  app.use(express.json());
  app.use(createActivationRouter({ pool: activationPool(state), schema: "fx" }));
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  const post = body => fetch(`http://127.0.0.1:${server.address().port}/activate`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  try {
    assert.equal((await post({ device_id: "esp32-01", claim_code: "not a code" })).status, 400);
    assert.equal((await post({ device_id: "unknown", claim_code: code })).status, 400);
    assert.equal((await post({ device_id: "esp32-01", claim_code: "ZZZZZ-99999" })).status, 400);
    assert.equal(state.claim.failed_attempts, 1);
    const ok = await post({ device_id: "esp32-01", claim_code: code.toLowerCase() });
    assert.equal(ok.status, 200);
    assert.equal(ok.headers.get("cache-control"), "no-store");
    const body = await ok.json();
    assert.equal(body.token_header, "X-Device-Token");
    assert.equal(body.device_token.length, 43);
    assert.ok(secretMatches(state.device.device_token_hash, body.device_token));
    assert.equal((await post({ device_id: "esp32-01", claim_code: code })).status, 409);

    // Camera gets a camera key instead; locked after 5 wrong codes; expired code gives 410.
    state.device.device_type = "CAMERA";
    state.claim = { ...state.claim, id: "c2", activated_at: null, failed_attempts: 5 };
    assert.equal((await post({ device_id: "esp32-01", claim_code: code })).status, 429);
    state.claim.failed_attempts = 0;
    state.claim.expires_at = new Date(Date.now() - 1000).toISOString();
    assert.equal((await post({ device_id: "esp32-01", claim_code: code })).status, 410);
    state.claim.expires_at = new Date(Date.now() + 1000_000).toISOString();
    const cam = await (await post({ device_id: "esp32-01", claim_code: code })).json();
    assert.equal(cam.key_header, "X-Camera-Key");
    assert.ok(secretMatches(state.device.device_token_hash, cam.camera_key));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
