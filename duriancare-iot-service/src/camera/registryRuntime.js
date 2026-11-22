"use strict";
const crypto = require("node:crypto");
const { privateIPv4 } = require("../discovery/udpDiscoveryService");
const { createFarmAccessClient } = require("../read-api");
const { getCamera, listCameras, heartbeat } = require("./deviceService");
const { findCameraByKey } = require("../devices/deviceAuth");
const commands = require("./commandService");
const livePreview = require("./livePreview");

const LIVE_POLL_MS = 2000;  // camera command-poll interval while live preview commands are being delivered

function cameraOrigin(value) {
  let url;
  try { url = new URL(value); } catch { throw Object.assign(new Error("Invalid camera origin"), {status:400}); }
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password ||
      url.pathname !== "/" || url.search || url.hash ||
      !(privateIPv4(url.hostname) || /^[a-z0-9][a-z0-9.-]{0,37}\.local$/i.test(url.hostname))) {
    throw Object.assign(new Error("Invalid camera LAN origin"), { status: 400 });
  }
  return { ipAddress: url.hostname, port: Number(url.port || (url.protocol === "https:" ? 443 : 80)), protocol: url.protocol.slice(0, -1) };
}

const CAMERA_ERRORS = new Set(["CAMERA_INIT_FAILED", "CAMERA_STREAM_FAILED"]);

/** Optional self-reported health; absent fields (older firmware) stay null. Returns null when malformed. */
function deviceHealth(body) {
  const { cameraReady = null, lastError = null, firmwareVersion = null, macAddress = null } = body;
  if (cameraReady !== null && typeof cameraReady !== "boolean") return null;
  if (lastError !== null && !CAMERA_ERRORS.has(lastError)) return null;
  if (firmwareVersion !== null && (typeof firmwareVersion !== "string" || !/^[A-Za-z0-9._+-]{1,32}$/.test(firmwareVersion))) return null;
  if (macAddress !== null && (typeof macAddress !== "string" || !/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/i.test(macAddress))) return null;
  return { cameraReady, lastError: cameraReady === true ? null : lastError, firmwareVersion,
    macAddress: macAddress ? macAddress.toUpperCase() : null };
}

// Optional diagnostics block reported in register/heartbeat (firmware >= 3.0.0). Only known numeric/string
// fields inside sane ranges are kept; anything malformed drops the whole block (never a 400, so a firmware
// bug in diagnostics cannot cost the camera its heartbeat).
const DIAG_NUMBERS = {
  vdd33: [0, 5000], uptimeSec: [0, 10 * 365 * 86400], freeHeap: [0, 16 * 1024 * 1024],
  minFreeHeap: [0, 16 * 1024 * 1024], freePsram: [0, 64 * 1024 * 1024], largestFreePsram: [0, 64 * 1024 * 1024],
  captureOk: [0, 1e9], captureFailures: [0, 1e9], consecutiveCaptureFailures: [0, 1e6], wifiRssi: [-127, 0],
  wifiChannel: [0, 14], xclkMhz: [1, 40], uploadFailures: [0, 1e9], timeRejects: [0, 1e9], wifiReconnects: [0, 1e9],
};
const DIAG_STRINGS = {
  resetReason: /^[A-Z_]{1,32}$/, frameSize: /^[A-Z0-9]{2,12}$/, endpointSource: /^(NVS|BEACON|CLOUD|DEFAULT)$/,
};
const CAPABILITIES = new Set(["push", "commands", "pull"]);

function parseDiagnostics(value) {
  if (value === undefined || value === null) return null;
  if (typeof value !== "object" || Array.isArray(value)) return null;
  const out = {};
  for (const [key, [min, max]] of Object.entries(DIAG_NUMBERS)) {
    if (value[key] === undefined) continue;
    if (!Number.isFinite(value[key]) || value[key] < min || value[key] > max) return null;
    out[key] = value[key];
  }
  for (const [key, pattern] of Object.entries(DIAG_STRINGS)) {
    if (value[key] === undefined) continue;
    if (typeof value[key] !== "string" || !pattern.test(value[key])) return null;
    out[key] = value[key];
  }
  return Object.keys(out).length ? out : null;
}

function parseCapabilities(value) {
  if (!Array.isArray(value) || value.length > 8) return null;
  const list = [...new Set(value.filter(item => typeof item === "string" && CAPABILITIES.has(item)))];
  return list.length ? list : null;
}

const WEAK_RSSI_DBM = -80;
const CAPTURE_FAILING_STREAK = 3;

function cameraView(row, now = Date.now()) {
  const seen = row.last_seen ? new Date(row.last_seen).getTime() : NaN;
  const timeout = Number(process.env.CAMERA_OFFLINE_TIMEOUT_MIN || 5) * 60000;
  const fresh = row.online && now - seen >= -30000 && now - seen < timeout;
  const diagnostics = row.diagnostics && typeof row.diagnostics === "object" ? row.diagnostics : null;
  const rssi = Number.isInteger(row.wifi_rssi) ? row.wifi_rssi : diagnostics?.wifiRssi ?? null;
  // Reasons the device is reachable but not fully healthy (shown next to DEGRADED in the UI).
  const reasons = [];
  if (fresh) {
    if (row.camera_ready === false) reasons.push(row.last_error || "CAMERA_STREAM_FAILED");
    if (Number.isFinite(rssi) && rssi < WEAK_RSSI_DBM) reasons.push("WIFI_WEAK");
    if ((diagnostics?.consecutiveCaptureFailures ?? 0) >= CAPTURE_FAILING_STREAK) reasons.push("CAPTURE_FAILING");
  } else if (Number.isFinite(seen)) {
    reasons.push("HEARTBEAT_TIMEOUT");
  }
  // DEGRADED: fresh heartbeat but the device's own self-test failed, the link is weak or captures keep failing.
  // camera_ready NULL = firmware too old to report it; treated as before (ONLINE).
  const status = !Number.isFinite(seen) ? "UNKNOWN"
    : !fresh ? "OFFLINE" : reasons.length > 0 ? "DEGRADED" : "ONLINE";
  const capabilities = Array.isArray(row.capabilities_reported) ? row.capabilities_reported : [];
  return { ...row, online: status === "ONLINE", cameraId: row.device_id, status, statusReasons: reasons,
    cameraReady: row.camera_ready ?? null, lastError: fresh ? row.last_error ?? null : "HEARTBEAT_TIMEOUT",
    firmwareVersion: row.firmware_version ?? null, wifiRssi: rssi, diagnostics,
    capabilities, deliveryMode: capabilities.includes("push") ? "PUSH" : "PULL", lastFrameAt: row.last_frame_at ?? null,
    lastSeenAt: row.last_seen, updatedAt: row.updated_at, addressSource: "REGISTRY",
    snapshotUrl: `/api/v1/camera/snapshot?device_id=${encodeURIComponent(row.device_id)}`,
    streamUrl: null, streamAvailable: false,
    // A weak link still delivers frames: preview stays available unless the camera itself is broken/offline.
    previewAvailable: Boolean(fresh) && row.camera_ready !== false,
    endpointVersion: crypto.createHash("sha256").update(`${row.protocol}:${row.ip_address}:${row.port}`).digest("hex").slice(0, 24) };
}

// Web/Mobile never receive the camera's LAN address (they only use backend URLs); administrators keep it
// for on-site debugging.
const LAN_FIELDS = ["ip_address", "port", "protocol", "mac_address", "ssid"];
function clientCameraView(row, actor, now = Date.now()) {
  const view = cameraView(row, now);
  if (String(actor?.role || "").toUpperCase() === "ADMIN") return view;
  for (const field of LAN_FIELDS) delete view[field];
  return view;
}

function cameraAccess({ pool, schema, farmAccessClient }) {
  const config = require("../config");
  const access = farmAccessClient ?? createFarmAccessClient({
    farmServiceUrl: config.farmServiceUrl, internalToken: config.internalToken });
  return async (req, res, next) => {
    try {
      const userId = req.get("X-Auth-User-Id"), role = req.get("X-Auth-Role");
      if (!userId || !role) return res.status(401).json({ error: "Authentication required" });
      const actor = { userId, role };
      const write = !["GET", "HEAD"].includes(req.method);
      const permitted = row => row.farm_id && (write
        ? access.canManageIot(actor, row.farm_id, row.zone_id)
        : access.canReadIot(actor, row.farm_id, row.zone_id));
      if (req.path === "/devices" && !write) {
        const rows = await listCameras(pool, schema), visible = [];
        for (const row of rows) if (await permitted(row)) visible.push(clientCameraView(row, actor));
        return res.json({ devices: visible });
      }
      // Stored images: the file must belong to a capture of a camera the actor may read
      // (same farm scope as snapshots). Files without a capture record are never served.
      const image = /^\/image\/([^/]+)$/.exec(req.path);
      if (image) {
        const filename = decodeURIComponent(image[1]);
        if (!/^cam_\d+\.jpg$/.test(filename)) return res.status(400).json({ error: "Invalid filename" });
        const suffix = `/api/v1/camera/image/${filename}`;
        const { rows } = await pool.query(
          `SELECT device_id FROM "${schema}".camera_captures WHERE right(image_url, $2) = $1 LIMIT 1`,
          [suffix, suffix.length]);
        if (!rows[0]) return res.status(404).json({ error: "Image not found" });
        const owner = await getCamera(pool, schema, rows[0].device_id);
        if (!owner || !(await permitted(owner))) return res.status(403).json({ error: "Camera access denied" });
        return next();
      }
      const match = /^\/devices\/([^/]+)/.exec(req.path);
      const id = match ? decodeURIComponent(match[1]) : req.query.device_id ?? req.body?.device_id;
      if (typeof id !== "string" || !/^[A-Za-z0-9._:-]{1,150}$/.test(id)) {
        return res.status(400).json({ error: "device_id is required" });
      }
      const row = await getCamera(pool, schema, id);
      if (!row) return res.status(404).json({ error: "Camera not registered", code: "CAMERA_NOT_REGISTERED" });
      if (!(await permitted(row))) return res.status(403).json({ error: "Camera access denied" });
      if (req.path.endsWith("/tree")) {
        // Do not allow assignment claims to move the device into another scope.
        if (req.body?.farmId !== row.farm_id || (req.body?.zoneId ?? null) !== row.zone_id ||
            (req.body?.treeId ?? null) !== row.tree_id) {
          return res.status(409).json({ error: "Camera reassignment requires verified provisioning" });
        }
      }
      req.cameraDevice = cameraView(row);
      req.cameraActor = actor;
      next();
    } catch (error) { res.status(error.status || 503).json({ error: "Camera access service unavailable" }); }
  };
}

// ── Device authentication ────────────────────────────────────────────────────
// Two credential sources, tried in this order:
//   1. ENV  CAMERA_DEVICE_BINDINGS {cameraId: {key, farmId, zoneId?}} — existing operator configuration.
//   2. DB   iot_devices.device_token_hash of a CAMERA activated with its claim code
//           (POST /api/v1/public/devices/activate); farm/zone come from the farmer's claim, never firmware.
// Returns { binding } or { status, body }. Never logs or echoes keys.
function readEnvBindings() {
  const raw = process.env.CAMERA_DEVICE_BINDINGS;
  if (raw === undefined || raw.trim() === "") return { bindings: {}, invalid: false };
  try {
    const bindings = JSON.parse(raw);
    if (!bindings || Array.isArray(bindings) || typeof bindings !== "object" ||
        Object.keys(bindings).length === 0 || Object.entries(bindings).some(([id, value]) =>
          !/^[A-Za-z0-9._:-]{1,150}$/.test(id) || !value || typeof value !== "object" ||
          typeof value.key !== "string" || value.key.length < 32 ||
          typeof value.farmId !== "string" || !value.farmId.trim() ||
          (value.zoneId != null && (typeof value.zoneId !== "string" || !value.zoneId.trim())))) {
      return { bindings: {}, invalid: true };
    }
    return { bindings, invalid: false };
  } catch {
    return { bindings: {}, invalid: true };
  }
}

function keyEquals(supplied, expected) {
  return typeof supplied === "string" && typeof expected === "string" &&
    Buffer.byteLength(supplied) === Buffer.byteLength(expected) &&
    crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(expected));
}

async function authenticateCamera(pool, schema, cameraId, supplied) {
  const env = readEnvBindings();
  const binding = typeof cameraId === "string" && Object.hasOwn(env.bindings, cameraId) && env.bindings[cameraId];
  if (binding && keyEquals(supplied, binding.key)) {
    return { binding: { farmId: binding.farmId, zoneId: binding.zoneId ?? null, source: "ENV" } };
  }
  const device = await findCameraByKey(pool, schema, cameraId, supplied);
  if (device) {
    if (device.provisioning_status !== "CLAIMED" || !device.farm_id) {
      return { status: 409, body: { error: "Camera is activated but not claimed by a farm yet", code: "CAMERA_NOT_CLAIMED" } };
    }
    return { binding: { farmId: device.farm_id, zoneId: device.cultivation_area_id ?? null, source: "DB", device } };
  }
  if (env.invalid) {
    return { status: 503, body: { error: "Camera registration configuration unavailable", code: "CAMERA_BINDINGS_UNAVAILABLE" } };
  }
  return { status: 401, body: { error: "Invalid device credentials" } };
}

/** First registration of a DB-activated camera: its camera_devices row is created from the farmer's claim. */
async function ensureCameraRow(pool, schema, cameraId, binding, origin, existing) {
  let current = existing;
  if (binding.source !== "DB") return current;
  if (!current) {
    await pool.query(
      `INSERT INTO "${schema}".camera_devices (device_id, ip_address, port, protocol, online, farm_id, zone_id)
       VALUES ($1, $2, $3, $4, false, $5, $6) ON CONFLICT (device_id) DO NOTHING`,
      [cameraId, origin.ipAddress, origin.port, origin.protocol, binding.farmId, binding.zoneId]);
    current = await getCamera(pool, schema, cameraId);
  } else if (current.farm_id !== binding.farmId || (current.zone_id ?? null) !== binding.zoneId) {
    // The farmer's claim is authoritative for a DB-activated camera (not the firmware).
    await pool.query(`UPDATE "${schema}".camera_devices SET farm_id = $2, zone_id = $3, updated_at = NOW()
      WHERE device_id = $1`, [cameraId, binding.farmId, binding.zoneId]);
    current = await getCamera(pool, schema, cameraId);
  }
  return current;
}

// Diagnostics/capabilities are stored only when the firmware sends them (older firmware: no extra write).
async function recordDeviceReport(pool, schema, cameraId, body) {
  const diagnostics = parseDiagnostics(body.diagnostics);
  const capabilities = parseCapabilities(body.capabilities);
  const rssi = Number.isInteger(body.wifiRssi) && body.wifiRssi >= -127 && body.wifiRssi <= 0
    ? body.wifiRssi : diagnostics?.wifiRssi ?? null;
  if (!diagnostics && !capabilities && rssi === null) return null;
  const { rows } = await pool.query(
    `UPDATE "${schema}".camera_devices
     SET diagnostics = COALESCE($2::jsonb, diagnostics),
         capabilities_reported = COALESCE($3::text[], capabilities_reported),
         wifi_rssi = COALESCE($4::int, wifi_rssi)
     WHERE device_id = $1 RETURNING *`,
    [cameraId, diagnostics ? JSON.stringify(diagnostics) : null, capabilities, rssi]);
  return rows[0] ?? null;
}

const CAMERA_ID_PATTERN = /^[A-Za-z0-9._:-]{1,150}$/;

function createRegistrationRouter({ pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl } = {}) {
  const express = require("express");
  const config = require("../config");
  const cameraService = require("./cameraService");
  const router = express.Router();
  const baseUrl = serverBaseUrl ?? config.serverBaseUrl;
  const uploads = uploadsDir ?? config.uploadsDir;
  const aiUrl = aiServiceUrl ?? config.aiServiceUrl;

  for (const route of ["/register", "/heartbeat"]) router.post(route, async (req, res) => {
    try {
      const body = req.body ?? {};
      const auth = await authenticateCamera(pool, schema, body.cameraId, req.get("X-Camera-Key"));
      if (!auth.binding) return res.status(auth.status).json(auth.body);
      const { binding } = auth;
      if (!Number.isSafeInteger(body.announcedAt) || Math.abs(Date.now() - body.announcedAt) > 30000) {
        return res.status(400).json({ error: "Fresh announcedAt epoch milliseconds required" });
      }
      if (["farmId", "zoneId", "treeId"].some(key => Object.hasOwn(body, key))) {
        return res.status(403).json({ error: "Device scope cannot be changed by firmware" });
      }
      const preview = await getCamera(pool, schema, body.cameraId);
      const protocol = body.protocol ?? preview?.protocol ?? "http";
      const port = body.port ?? preview?.port ?? 80;
      if (!["http", "https"].includes(protocol) || !Number.isInteger(port) || port < 1 || port > 65535 ||
          typeof body.ipAddress !== "string") return res.status(400).json({ error: "Explicit address, HTTP scheme and valid port required" });
      const origin = cameraOrigin(`${protocol}://${body.ipAddress}:${port}`);
      const health = deviceHealth(body);
      if (!health) return res.status(400).json({ error: "Invalid camera health fields" });
      const current = await ensureCameraRow(pool, schema, body.cameraId, binding, origin, preview);
      if (!current) return res.status(404).json({ error: "Operator must provision camera ownership first" });
      if (!binding.farmId || current.farm_id !== binding.farmId ||
          (binding.zoneId && current.zone_id !== binding.zoneId)) {
        return res.status(403).json({ error: "Device scope cannot be changed by firmware" });
      }
      let device = await heartbeat(pool, schema, { deviceId: body.cameraId, ...origin,
        expectedFarmId: binding.farmId, announcedAt: body.announcedAt, ...health });
      let status = route === "/register" ? "registered" : "ok";
      if (!device) {
        const latest = await getCamera(pool, schema, body.cameraId);
        if (!latest || latest.farm_id !== binding.farmId ||
            new Date(latest.last_seen).getTime() !== body.announcedAt ||
            latest.ip_address !== origin.ipAddress || Number(latest.port) !== origin.port || latest.protocol !== protocol) {
          return res.status(409).json({ error: "Obsolete camera announcement" });
        }
        device = latest;
        status = "ok";
      }
      device = (await recordDeviceReport(pool, schema, body.cameraId, body)) ?? device;
      const reply = { status, device: cameraView(device) };
      // Push firmware learns about queued captures here too (in addition to polling /commands).
      if (Array.isArray(device.capabilities_reported) && device.capabilities_reported.includes("commands")) {
        reply.commandsPending = await commands.countOpenCommands(pool, schema, body.cameraId);
        reply.pollAfterMs = config.getCameraDeliveryConfig().pollMs;
        reply.serverTime = Date.now();
      }
      return res.json(reply);
    } catch (error) {
      return res.status(error.status || 503).json({ error: error.status === 400 ? "Invalid camera origin" : "Camera registration unavailable" });
    }
  });

  // Authenticated camera + its registered row; used by the push endpoints below.
  async function deviceRequest(req, res) {
    const cameraId = req.params.cameraId;
    if (!CAMERA_ID_PATTERN.test(cameraId)) { res.status(400).json({ error: "Invalid camera id" }); return null; }
    const auth = await authenticateCamera(pool, schema, cameraId, req.get("X-Camera-Key"));
    if (!auth.binding) { res.status(auth.status).json(auth.body); return null; }
    const row = await getCamera(pool, schema, cameraId);
    if (!row) { res.status(404).json({ error: "Camera must register first", code: "CAMERA_NOT_REGISTERED" }); return null; }
    if (row.farm_id !== auth.binding.farmId) { res.status(403).json({ error: "Camera scope mismatch" }); return null; }
    return row;
  }

  // ── GET /:cameraId/commands — queued captures (204 when none) ─────────────
  router.get("/:cameraId/commands", async (req, res) => {
    try {
      const row = await deviceRequest(req, res);
      if (!row) return;
      const list = await commands.takeCommands(pool, schema, row.device_id);
      res.setHeader("Cache-Control", "no-store");
      if (list.length === 0) return res.status(204).end();
      // Someone is watching live: poll faster while preview commands flow. The next register/heartbeat
      // reply (every 60 s) restores the normal interval once the viewer leaves.
      const pollMs = config.getCameraDeliveryConfig().pollMs;
      const pollAfterMs = list.some((c) => c.preview) ? Math.min(pollMs, LIVE_POLL_MS) : pollMs;
      return res.json({ commands: list.map(commands.commandForDevice), pollAfterMs });
    } catch (error) {
      console.error("[camera] Command poll failed:", error.message);
      return res.status(503).json({ error: "Camera commands unavailable" });
    }
  });

  // ── POST /:cameraId/commands/:commandId/result — camera could not capture ──
  router.post("/:cameraId/commands/:commandId/result", async (req, res) => {
    try {
      const row = await deviceRequest(req, res);
      if (!row) return;
      const { status, error } = req.body ?? {};
      if (status !== "FAILED" || (error != null && (typeof error !== "string" || !/^[A-Z_]{1,64}$/.test(error)))) {
        return res.status(400).json({ error: "status must be FAILED with an optional UPPER_CASE error code" });
      }
      const updated = await commands.failCommand(pool, schema, row.device_id, req.params.commandId, error ?? "CAMERA_CAPTURE_FAILED");
      return updated ? res.status(204).end() : res.status(409).json({ error: "Command is not open for this camera" });
    } catch (error) {
      console.error("[camera] Command result failed:", error.message);
      return res.status(503).json({ error: "Camera commands unavailable" });
    }
  });

  // ── POST /:cameraId/frames — camera pushes a JPEG (PUSH delivery mode) ────
  const frameLimit = () => config.getCameraDeliveryConfig().frameMaxBytes;
  router.post("/:cameraId/frames",
    (req, res, next) => express.raw({ type: "image/jpeg", limit: frameLimit() })(req, res, (error) => {
      if (error) {
        return res.status(error.type === "entity.too.large" ? 413 : 400)
          .json({ error: error.type === "entity.too.large" ? "Frame too large" : "Invalid frame body" });
      }
      next();
    }),
    async (req, res) => {
      try {
        const row = await deviceRequest(req, res);
        if (!row) return;
        if ((req.get("Content-Type") || "").split(";", 1)[0].trim().toLowerCase() !== "image/jpeg" || !Buffer.isBuffer(req.body)) {
          return res.status(400).json({ error: "Content-Type must be image/jpeg" });
        }
        const capturedAt = Number(req.query.capturedAt);
        const now = Date.now();
        if (!Number.isSafeInteger(capturedAt) || capturedAt < now - 10 * 60000 || capturedAt > now + 30000) {
          return res.status(400).json({ error: "capturedAt must be UTC epoch milliseconds within the last 10 minutes" });
        }
        const reason = req.query.reason;
        if (!commands.REASONS.has(reason)) return res.status(400).json({ error: "reason must be SCHEDULED or ON_DEMAND" });
        let command = null;
        if (req.query.commandId !== undefined) {
          command = await commands.findOpenCommand(pool, schema, row.device_id, req.query.commandId);
          if (!command) return res.status(409).json({ error: "Command is not open for this camera", code: "COMMAND_NOT_OPEN" });
        }
        if (!cameraService.isCompleteJpeg(req.body)) {
          return res.status(400).json({ error: "Frame is not a complete JPEG", code: "INVALID_JPEG" });
        }
        const preview = Boolean(command?.preview);
        if (preview) {
          // Live view frame: memory only (no file, no captures row, no AI); see livePreview.js.
          livePreview.setFrame(row.device_id, Buffer.from(req.body), new Date(capturedAt));
          await pool.query(`UPDATE "${schema}".camera_devices SET last_frame_at = NOW() WHERE device_id = $1`, [row.device_id]);
          await commands.discardPreviewCommand(pool, schema, row.device_id, command.id);
          return res.status(201).json({ captureId: null, imageUrl: null, aiStatus: null, commandId: command.id, preview: true });
        }
        const capture = await cameraService.storePushedFrame({ pool, schema, deviceId: row.device_id, buffer: req.body,
          captureType: reason === "SCHEDULED" ? "SCHEDULED" : "MANUAL", capturedAt: new Date(capturedAt).toISOString(),
          commandId: command?.id ?? null, serverBaseUrl: baseUrl, uploadsDir: uploads,
          aiServiceUrl: preview ? null : aiUrl });
        await pool.query(`UPDATE "${schema}".camera_devices SET last_frame_at = NOW() WHERE device_id = $1`, [row.device_id]);
        if (command) await commands.completeCommand(pool, schema, command.id, capture.id);
        return res.status(201).json({ captureId: capture.id, imageUrl: capture.image_url.replace(/^https?:\/\/[^/]+/, ""),
          aiStatus: capture.ai_status, commandId: command?.id ?? null });
      } catch (error) {
        console.error("[camera] Frame upload failed:", error.message);
        return res.status(error.status || 503).json({ error: error.status ? error.message : "Frame storage unavailable" });
      }
    });

  return router;
}

module.exports = { cameraOrigin, cameraView, clientCameraView, deviceHealth, parseDiagnostics, parseCapabilities,
  cameraAccess, createRegistrationRouter, authenticateCamera };
