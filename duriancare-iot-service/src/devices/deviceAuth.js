"use strict";

// Device credentials for internet (CLOUD) deployments.
//
// A device proves possession of its one-time claim code (the same 10-character code the farmer uses on
// the Web) once, at POST /api/v1/public/devices/activate, and receives a random secret:
//   SENSOR -> device token, sent as X-Device-Token on every POST /api/v1/sensors/data
//   CAMERA -> camera key, sent as X-Camera-Key on register / heartbeat / frames / commands
// Only sha256(secret) is stored (iot_devices.device_token_hash). The secret is returned exactly once and
// never logged. Activating again with a newly issued claim code replaces the previous secret.
const crypto = require("node:crypto");
const express = require("express");
const { MAX_CLAIM_ATTEMPTS, hashClaimCode, normalizeClaimCode } = require("./deviceLifecycle");

const TOKEN_HEADER = "X-Device-Token";
const CAMERA_KEY_HEADER = "X-Camera-Key";
const DEVICE_UID_PATTERN = /^[A-Za-z0-9._:-]{1,150}$/;

function deviceAuthStatements(schema) {
  const devices = `"${schema}".iot_devices`;
  const claims = `"${schema}".iot_device_claims`;
  return [
    `ALTER TABLE ${devices} ADD COLUMN IF NOT EXISTS device_token_hash CHAR(64)`,
    `ALTER TABLE ${devices} ADD COLUMN IF NOT EXISTS device_token_issued_at TIMESTAMPTZ`,
    `ALTER TABLE ${claims} ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ`,
    // Last Wi-Fi RSSI reported by a sensor (optional wifi_rssi field): shown as a DEGRADED reason.
    `ALTER TABLE ${devices} ADD COLUMN IF NOT EXISTS last_wifi_rssi INTEGER`,
  ];
}

async function ensureDeviceAuthSchema(pool, schema) {
  for (const statement of deviceAuthStatements(schema)) await pool.query(statement);
}

function generateDeviceSecret(randomBytes = crypto.randomBytes) {
  return randomBytes(32).toString("base64url"); // 43 characters
}

function hashDeviceSecret(secret) {
  return crypto.createHash("sha256").update(String(secret), "utf8").digest("hex");
}

function secretMatches(storedHash, supplied) {
  if (typeof storedHash !== "string" || storedHash.length !== 64 || typeof supplied !== "string" ||
      supplied.length < 16 || supplied.length > 256) return false;
  const left = Buffer.from(storedHash, "hex");
  const right = Buffer.from(hashDeviceSecret(supplied), "hex");
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

/**
 * Sensor ingestion authentication.
 *   token sent             -> must match (401 otherwise), in every mode
 *   no token, device has a token issued -> 401 (an upgraded device cannot be impersonated by a token-less sender)
 *   no token, legacy device -> accepted in OPTIONAL (LOCAL) mode, 401 in REQUIRED (CLOUD) mode
 */
function authenticateSensor(device, suppliedToken, authMode) {
  const supplied = typeof suppliedToken === "string" ? suppliedToken.trim() : "";
  if (supplied) {
    return secretMatches(device.device_token_hash, supplied)
      ? { ok: true, authenticated: true }
      : { ok: false, status: 401, code: "DEVICE_TOKEN_INVALID", error: "Device token is not valid" };
  }
  if (device.device_token_hash) {
    return { ok: false, status: 401, code: "DEVICE_TOKEN_REQUIRED", error: "This device must send its device token" };
  }
  if (authMode === "REQUIRED") {
    return { ok: false, status: 401, code: "DEVICE_TOKEN_REQUIRED",
      error: "Device token required; activate the device with its claim code" };
  }
  return { ok: true, authenticated: false };
}

/** iot_devices row of a CAMERA whose activation key matches, or null. */
async function findCameraByKey(pool, schema, cameraId, suppliedKey) {
  if (typeof cameraId !== "string" || !DEVICE_UID_PATTERN.test(cameraId) || typeof suppliedKey !== "string") return null;
  const { rows } = await pool.query(
    `SELECT id, device_uid, farm_id, cultivation_area_id, provisioning_status, status, device_type, device_token_hash
     FROM "${schema}".iot_devices
     WHERE LOWER(device_uid) = LOWER($1) AND device_type = 'CAMERA' AND status <> 'DELETED'
       AND device_token_hash IS NOT NULL
     LIMIT 1`, [cameraId]);
  const row = rows[0];
  return row && secretMatches(row.device_token_hash, suppliedKey) ? row : null;
}

function apiError(status, error, code) {
  return Object.assign(new Error(error), { status, code });
}

/** POST /activate (mounted at /api/v1/public/devices). */
function createActivationRouter({ pool, schema, now = () => new Date(), randomBytes } = {}) {
  const router = express.Router();
  const devices = `"${schema}".iot_devices`;
  const claims = `"${schema}".iot_device_claims`;

  router.post("/activate", async (request, response) => {
    const body = request.body && typeof request.body === "object" ? request.body : {};
    const deviceUid = typeof body.device_id === "string" ? body.device_id.trim() : "";
    const code = normalizeClaimCode(body.claim_code);
    const invalid = () => apiError(400, "Device id or claim code is not valid", "ACTIVATION_INVALID");
    let client;
    try {
      if (!DEVICE_UID_PATTERN.test(deviceUid) || !code) throw invalid();
      client = await pool.connect();
      await client.query("BEGIN");
      const device = (await client.query(
        `SELECT id, device_uid, device_type FROM ${devices}
         WHERE LOWER(device_uid) = LOWER($1) AND status <> 'DELETED' FOR UPDATE`, [deviceUid])).rows[0];
      if (!device) throw invalid();
      // Latest code issued for the device that has not been used for an activation yet.
      const claim = (await client.query(
        `SELECT id, code_hash, status, expires_at, failed_attempts FROM ${claims}
         WHERE device_id = $1 AND status IN ('PENDING', 'CLAIMED') AND activated_at IS NULL
         ORDER BY created_at DESC LIMIT 1 FOR UPDATE`, [device.id])).rows[0];
      const hash = hashClaimCode(device.id, code);
      if (!claim) {
        const used = (await client.query(
          `SELECT code_hash FROM ${claims} WHERE device_id = $1 AND activated_at IS NOT NULL`, [device.id])).rows;
        if (used.some((row) => row.code_hash === hash)) {
          throw apiError(409, "Claim code was already used to activate this device; ask for a new code", "ACTIVATION_USED");
        }
        throw invalid();
      }
      if (claim.failed_attempts >= MAX_CLAIM_ATTEMPTS) {
        throw apiError(429, "Too many wrong claim codes; ask for a new code", "ACTIVATION_LOCKED");
      }
      const expected = Buffer.from(claim.code_hash, "hex");
      const actual = Buffer.from(hash, "hex");
      if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) {
        await client.query(`UPDATE ${claims} SET failed_attempts = failed_attempts + 1 WHERE id = $1`, [claim.id]);
        await client.query("COMMIT");
        client.release(); client = null;
        return response.status(400).json({ error: "Device id or claim code is not valid", code: "ACTIVATION_INVALID" });
      }
      // A code the farmer already used for the claim stays valid for the device until it expires.
      if (new Date(claim.expires_at).getTime() <= now().getTime()) {
        throw apiError(410, "Claim code has expired; ask for a new code", "ACTIVATION_EXPIRED");
      }
      const secret = generateDeviceSecret(randomBytes);
      await client.query(
        `UPDATE ${devices} SET device_token_hash = $2, device_token_issued_at = CURRENT_TIMESTAMP,
           updated_at = CURRENT_TIMESTAMP WHERE id = $1`, [device.id, hashDeviceSecret(secret)]);
      await client.query(`UPDATE ${claims} SET activated_at = CURRENT_TIMESTAMP WHERE id = $1`, [claim.id]);
      await client.query("COMMIT");
      client.release(); client = null;
      console.log(`[device-auth] Device activated: ${device.device_uid} (${device.device_type || "SENSOR"})`);
      response.setHeader("Cache-Control", "no-store");
      if ((device.device_type || "SENSOR") === "CAMERA") {
        return response.json({ device_id: device.device_uid, camera_key: secret, key_header: CAMERA_KEY_HEADER });
      }
      return response.json({ device_id: device.device_uid, device_token: secret, token_header: TOKEN_HEADER });
    } catch (error) {
      if (client) {
        await client.query("ROLLBACK").catch(() => {});
        client.release();
      }
      if (error.status) return response.status(error.status).json({ error: error.message, code: error.code });
      console.error("[device-auth] Activation failed:", error.message);
      return response.status(503).json({ error: "Device activation unavailable", code: "ACTIVATION_UNAVAILABLE" });
    }
  });
  return router;
}

module.exports = {
  CAMERA_KEY_HEADER,
  TOKEN_HEADER,
  authenticateSensor,
  createActivationRouter,
  deviceAuthStatements,
  ensureDeviceAuthSchema,
  findCameraByKey,
  generateDeviceSecret,
  hashDeviceSecret,
  secretMatches,
};
