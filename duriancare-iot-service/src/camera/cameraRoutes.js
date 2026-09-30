"use strict";

const dns     = require("dns");
const fs      = require("fs");
const path    = require("path");
const http    = require("http");
const https   = require("https");
const cron    = require("node-cron");
const express = require("express");

const { captureAndStore, listCaptures, listSchedules, replaceSchedules } = require("./cameraService");
const { reschedule } = require("./cameraScheduler");
const { getCameraConfig } = require("../config");
const { upsertCamera, resolveCameraUrl, getCamera, listCameras, assignTree, updateCameraUrl } = require("./deviceService");

/**
 * Resolve a camera base URL, falling back to DEFAULT_ESP32_IP when the hostname
 * is a .local mDNS address that cannot be resolved (common inside Docker/NAT).
 */
async function resolveCaptureUrl(camUrl, capPath) {
  if (!camUrl) return null;
  const fullUrl = `${camUrl}${capPath}`;
  const fallbackIp = process.env.DEFAULT_ESP32_IP;
  if (!fallbackIp || !fullUrl.includes(".local")) return fullUrl;

  try {
    const parsed = new URL(fullUrl);
    await new Promise((resolve, reject) =>
      dns.lookup(parsed.hostname, (err, addr) => err ? reject(err) : resolve(addr)),
    );
    return fullUrl; // mDNS resolved OK
  } catch (err) {
    if (err.code === "ENOTFOUND") {
      const parsed = new URL(fullUrl);
      const replaced = fullUrl.replace(parsed.hostname, fallbackIp);
      console.warn(`[camera] mDNS ${parsed.hostname} not found — using ${fallbackIp}`);
      return replaced;
    }
    return fullUrl;
  }
}

/**
 * Parse a URL string and return { hostname, port } or null on failure.
 */
function parseBaseUrl(urlStr) {
  try {
    const p = new URL(urlStr);
    return {
      hostname: p.hostname,
      port: p.port ? parseInt(p.port, 10) : (p.protocol === "https:" ? 443 : 80),
      protocol: p.protocol.replace(":", ""),
    };
  } catch {
    return null;
  }
}

function createCameraRouter({ pool, schema, esp32CameraUrl, esp32CapturePath, serverBaseUrl, uploadsDir, aiServiceUrl }) {
  const router = express.Router();

  // ── POST /capture-now ──────────────────────────────────────────────────────
  router.post("/capture-now", async (req, res) => {
    const deviceId = ((req.query.device_id ?? req.body?.device_id) || "ESP32-CAM-001").trim();
    const { esp32CameraUrl: fallbackUrl, esp32CapturePath: capPath } = getCameraConfig();
    const { url: camUrl, found } = await resolveCameraUrl(pool, schema, deviceId, fallbackUrl);

    // Auto-register unknown device with fallback URL so it becomes trackable
    if (!found && fallbackUrl) {
      const parsed = parseBaseUrl(fallbackUrl);
      if (parsed) {
        upsertCamera(pool, schema, { deviceId, ipAddress: parsed.hostname, port: parsed.port, protocol: parsed.protocol })
          .catch((e) => console.warn("[camera] auto-register failed:", e.message));
      }
    }

    try {
      // Apply mDNS fallback to the base camera URL before passing to captureAndStore
      const resolvedBase = await resolveCaptureUrl(camUrl, "").replace(/\/$/, "");
      const capture = await captureAndStore({ pool, schema, deviceId, captureType: "MANUAL", esp32CameraUrl: resolvedBase, esp32CapturePath: capPath, serverBaseUrl, uploadsDir, aiServiceUrl });
      return res.status(201).json(capture);
    } catch (err) {
      console.error("[camera] Capture failed:", err.message);
      return res.status(502).json({ error: `Capture failed: ${err.message}` });
    }
  });

  // ── GET /snapshot — single JPEG frame proxy (for live preview polling) ─────
  //
  // Design goals:
  //  1. Collect the full JPEG into a buffer before responding — never stream a
  //     partial frame to the client (broken stream → black <img>).
  //  2. One in-flight download per router instance.  If another snapshot is
  //     already in progress, serve the last good cached frame immediately so the
  //     browser never waits and ESP32-CAM is never hit concurrently.
  //  3. Keep the cached frame for up to CACHE_TTL ms so rapid-fire browser
  //     polling doesn't re-download from ESP32-CAM every time.

  // Per-device snapshot state: deviceId → { inFlight, lastBuf, lastAt }
  const deviceSnaps = new Map();
  function getSnapState(deviceId) {
    if (!deviceSnaps.has(deviceId)) {
      deviceSnaps.set(deviceId, { inFlight: false, lastBuf: null, lastAt: 0 });
    }
    return deviceSnaps.get(deviceId);
  }

  const CACHE_TTL    =  4_000;  // serve cached frame for up to 4 s
  const SNAP_TIMEOUT = 12_000;  // abort if ESP32-CAM takes longer than 12 s

  function serveLastFrame(res, buf) {
    res.setHeader("Content-Type", "image/jpeg");
    res.setHeader("Cache-Control", "no-cache, no-store");
    res.setHeader("X-Frame-Cached", "1");
    return res.end(buf);
  }

  router.get("/snapshot", async (req, res) => {
    const deviceId = (req.query.device_id || "ESP32-CAM-001").trim();
    const snap = getSnapState(deviceId);
    const now = Date.now();

    // Serve cache if fresh and not mid-download
    if (snap.lastBuf && !snap.inFlight && (now - snap.lastAt) < CACHE_TTL) {
      return serveLastFrame(res, snap.lastBuf);
    }

    // Another download is already running — return cached frame (or 503)
    if (snap.inFlight) {
      if (snap.lastBuf) return serveLastFrame(res, snap.lastBuf);
      return res.status(503).json({ error: "Camera snapshot in progress — retry shortly" });
    }

    // Resolve runtime camera URL from DB; fall back to env config
    const { esp32CameraUrl: fallbackUrl, esp32CapturePath: capPath } = getCameraConfig();
    const { url: camUrl, online, found } = await resolveCameraUrl(pool, schema, deviceId, fallbackUrl);

    // Serve stale cache immediately when device is known-offline and we have a recent frame.
    // Do NOT return 503 — fall through to attempt a live fetch regardless of online flag,
    // because the DB status may lag behind the camera's actual state.
    if (found && !online && snap.lastBuf) {
      return serveLastFrame(res, snap.lastBuf);
    }

    // Auto-register unknown device with fallback URL so it becomes trackable in DB
    const snapState = snap; // captured for async callback
    if (!found && fallbackUrl && !snapState._autoRegistered) {
      snapState._autoRegistered = true;
      const parsed = parseBaseUrl(fallbackUrl);
      if (parsed) {
        upsertCamera(pool, schema, { deviceId, ipAddress: parsed.hostname, port: parsed.port, protocol: parsed.protocol })
          .catch((e) => console.warn("[camera] auto-register failed:", e.message));
      }
    }

    const captureUrl = await resolveCaptureUrl(camUrl, capPath);
    if (!captureUrl) {
      return res.status(503).json({ error: "Camera URL chưa được cấu hình. Vui lòng đặt ESP32_CAMERA_URL trong biến môi trường." });
    }

    snap.inFlight = true;
    let done = false;

    const client = captureUrl.startsWith("https") ? https : http;

    const proxyReq = client.get(captureUrl, {
      timeout: SNAP_TIMEOUT,
      headers: { Connection: "close", Accept: "image/jpeg" },
    }, (proxyRes) => {
      if (proxyRes.statusCode !== 200) {
        proxyRes.resume();
        if (!done) { done = true; snap.inFlight = false; }
        if (!res.headersSent) return res.status(502).json({ error: `ESP32-CAM HTTP ${proxyRes.statusCode}` });
        return;
      }

      // Collect full JPEG into memory — never pipe a partial stream to the client
      const chunks = [];
      proxyRes.on("data", (chunk) => chunks.push(chunk));
      proxyRes.on("end", () => {
        if (done) return;
        done = true;
        snap.inFlight = false;
        const buf = Buffer.concat(chunks);
        if (buf.length > 0) { snap.lastBuf = buf; snap.lastAt = Date.now(); }
        if (res.headersSent) return;
        if (buf.length === 0) {
          console.warn(`[camera] empty frame from ESP32-CAM — device=${deviceId} url=${captureUrl}`);
          return res.status(502).json({ error: "Empty frame from ESP32-CAM" });
        }
        const isJpeg = buf[0] === 0xFF && buf[1] === 0xD8 && buf[2] === 0xFF;
        if (!isJpeg) {
          console.warn(`[camera] non-JPEG body from ESP32-CAM — device=${deviceId} first=[${buf.slice(0,4).toString("hex")}]`);
          return res.status(502).json({ error: "Non-JPEG response from ESP32-CAM" });
        }
        console.log(`[camera] snapshot OK — device=${deviceId} bytes=${buf.length}`);
        res.setHeader("Content-Type", proxyRes.headers["content-type"] || "image/jpeg");
        res.setHeader("Cache-Control", "no-cache, no-store");
        return res.end(buf);
      });
      proxyRes.on("error", (err) => {
        if (done) return;
        done = true;
        snap.inFlight = false;
        if (snap.lastBuf && !res.headersSent) return serveLastFrame(res, snap.lastBuf);
        if (!res.headersSent) res.status(502).json({ error: `Stream error: ${err.message}` });
      });
    });

    proxyReq.on("error", (err) => {
      if (done) return;
      done = true;
      snap.inFlight = false;
      if (snap.lastBuf && !res.headersSent) return serveLastFrame(res, snap.lastBuf);
      if (!res.headersSent) res.status(502).json({ error: `Snapshot unavailable: ${err.message}` });
    });

    proxyReq.on("timeout", () => {
      if (done) return;
      done = true;
      snap.inFlight = false;
      proxyReq.destroy();
      console.warn(`[camera] snapshot timeout after ${SNAP_TIMEOUT}ms — device=${deviceId} url=${captureUrl}`);
      if (snap.lastBuf && !res.headersSent) return serveLastFrame(res, snap.lastBuf);
      if (!res.headersSent) res.status(504).json({ error: "ESP32-CAM snapshot timed out", device: deviceId, url: captureUrl });
    });

    req.on("close", () => { if (!done) proxyReq.destroy(); });
  });

  // ── GET /image/:filename — serve stored images (public, no auth) ───────────
  router.get("/image/:filename", (req, res) => {
    const filename = path.basename(req.params.filename);
    if (!/^cam_\d+\.jpg$/.test(filename)) {
      return res.status(400).json({ error: "Invalid filename" });
    }
    const filepath = path.resolve(uploadsDir, "cameras", filename);
    if (!fs.existsSync(filepath)) {
      return res.status(404).json({ error: "Image not found" });
    }
    res.setHeader("Content-Type", "image/jpeg");
    res.setHeader("Cache-Control", "public, max-age=86400");
    return res.sendFile(filepath);
  });

  // ── GET /history ───────────────────────────────────────────────────────────
  router.get("/history", async (req, res) => {
    const { device_id, from, to } = req.query;
    const limit  = Math.min(parseInt(req.query.limit,  10) || 20, 100);
    const offset = Math.max(parseInt(req.query.offset, 10) || 0,  0);

    if (from && isNaN(new Date(from).getTime())) {
      return res.status(400).json({ error: "from must be a valid ISO-8601 date" });
    }
    if (to && isNaN(new Date(to).getTime())) {
      return res.status(400).json({ error: "to must be a valid ISO-8601 date" });
    }

    try {
      const result = await listCaptures({ pool, schema, deviceId: device_id, from, to, limit, offset });
      return res.json(result);
    } catch (err) {
      console.error("[camera] History failed:", err.message);
      return res.status(500).json({ error: "Failed to retrieve camera history" });
    }
  });

  // ── GET /devices — list all registered cameras ────────────────────────────
  router.get("/devices", async (req, res) => {
    try {
      const devices = await listCameras(pool, schema);
      return res.json({ devices });
    } catch (err) {
      console.error("[camera] List devices failed:", err.message);
      return res.status(500).json({ error: "Failed to list camera devices" });
    }
  });

  // ── GET /devices/:deviceId — single camera ────────────────────────────────
  router.get("/devices/:deviceId", async (req, res) => {
    try {
      const device = await getCamera(pool, schema, req.params.deviceId);
      if (!device) return res.status(404).json({ error: "Camera not found" });
      return res.json({ device });
    } catch (err) {
      console.error("[camera] Get device failed:", err.message);
      return res.status(500).json({ error: "Failed to get camera device" });
    }
  });

  // ── PATCH /devices/:deviceId/tree — assign tree to camera ─────────────────
  router.patch("/devices/:deviceId/tree", async (req, res) => {
    const { treeId, zoneId, farmId } = req.body ?? {};
    try {
      const device = await assignTree(pool, schema, req.params.deviceId, {
        treeId:  treeId  ?? null,
        zoneId:  zoneId  ?? null,
        farmId:  farmId  ?? null,
      });
      if (!device) return res.status(404).json({ error: "Camera not found" });
      return res.json({ device });
    } catch (err) {
      console.error("[camera] Assign tree failed:", err.message);
      return res.status(500).json({ error: "Failed to assign tree" });
    }
  });

  // ── POST /config — update camera IP/URL at runtime (no container restart) ──
  router.post("/config", async (req, res) => {
    const { device_id, camera_url } = req.body ?? {};

    if (!device_id || typeof device_id !== "string" || !device_id.trim()) {
      return res.status(400).json({ error: "device_id is required" });
    }
    if (!camera_url || typeof camera_url !== "string" || !camera_url.trim()) {
      return res.status(400).json({ error: "camera_url is required (e.g. http://192.168.1.137)" });
    }

    let parsed;
    try {
      parsed = new URL(camera_url.trim());
    } catch {
      return res.status(400).json({ error: "camera_url must be a valid URL (e.g. http://192.168.1.137 or http://192.168.1.137:81)" });
    }

    const ipAddress = parsed.hostname;
    const port = parsed.port
      ? parseInt(parsed.port, 10)
      : (parsed.protocol === "https:" ? 443 : 80);

    if (!ipAddress) {
      return res.status(400).json({ error: "Cannot extract IP/hostname from camera_url" });
    }

    try {
      let device = await updateCameraUrl(pool, schema, device_id.trim(), { ipAddress, port });
      if (!device) {
        // Device not in DB yet — auto-register with the provided IP
        device = await upsertCamera(pool, schema, {
          deviceId: device_id.trim(),
          ipAddress,
          port,
          protocol: parsed.protocol.replace(":", ""),
        });
      }
      const resolvedUrl = `${parsed.protocol}//${device.ip_address}:${device.port}`;
      console.log(`[camera] config updated — device=${device.device_id} ip=${device.ip_address} port=${device.port}`);
      return res.json({ device, camera_url: resolvedUrl });
    } catch (err) {
      console.error("[camera] Config update failed:", err.message);
      return res.status(500).json({ error: "Failed to update camera config" });
    }
  });

  // ── GET /schedule ──────────────────────────────────────────────────────────
  router.get("/schedule", async (req, res) => {
    const deviceId = (req.query.device_id || "ESP32-CAM-001").trim();
    try {
      const schedules = await listSchedules({ pool, schema, deviceId });
      return res.json({ device_id: deviceId, schedules });
    } catch (err) {
      console.error("[camera] Get schedule failed:", err.message);
      return res.status(500).json({ error: "Failed to retrieve schedules" });
    }
  });

  // ── PUT /schedule ──────────────────────────────────────────────────────────
  router.put("/schedule", async (req, res) => {
    const { device_id, schedules } = req.body ?? {};

    if (!device_id || typeof device_id !== "string" || !device_id.trim()) {
      return res.status(400).json({ error: "device_id is required" });
    }
    if (!Array.isArray(schedules)) {
      return res.status(400).json({ error: "schedules must be an array" });
    }
    for (const s of schedules) {
      if (!s.cron_expression || !cron.validate(s.cron_expression)) {
        return res.status(400).json({ error: `Invalid cron expression: ${s.cron_expression}` });
      }
    }

    try {
      const updated = await replaceSchedules({ pool, schema, deviceId: device_id.trim(), schedules });
      reschedule({ deviceId: device_id.trim(), schedules: updated, pool, schema, serverBaseUrl, uploadsDir, aiServiceUrl });
      return res.json({ device_id: device_id.trim(), schedules: updated });
    } catch (err) {
      console.error("[camera] Update schedule failed:", err.message);
      return res.status(500).json({ error: "Failed to update schedules" });
    }
  });

  return router;
}

module.exports = { createCameraRouter };
