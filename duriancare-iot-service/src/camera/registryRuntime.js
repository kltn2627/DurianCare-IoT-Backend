"use strict";
const crypto = require("node:crypto");
const { privateIPv4 } = require("../discovery/udpDiscoveryService");
const { createFarmAccessClient } = require("../read-api");
const { getCamera, listCameras, heartbeat } = require("./deviceService");

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

function cameraView(row, now = Date.now()) {
  const seen = row.last_seen ? new Date(row.last_seen).getTime() : NaN;
  const timeout = Number(process.env.CAMERA_OFFLINE_TIMEOUT_MIN || 5) * 60000;
  const status = !Number.isFinite(seen) ? "UNKNOWN"
    : row.online && now - seen >= -30000 && now - seen < timeout ? "ONLINE" : "OFFLINE";
  return { ...row, online: status === "ONLINE", cameraId: row.device_id, status,
    lastSeenAt: row.last_seen, updatedAt: row.updated_at, addressSource: "REGISTRY",
    snapshotUrl: `/api/v1/camera/snapshot?device_id=${encodeURIComponent(row.device_id)}`,
    streamUrl: null, streamAvailable: false, previewAvailable: status === "ONLINE",
    endpointVersion: crypto.createHash("sha256").update(`${row.protocol}:${row.ip_address}:${row.port}`).digest("hex").slice(0, 24) };
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
        for (const row of rows) if (await permitted(row)) visible.push(cameraView(row));
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
      next();
    } catch (error) { res.status(error.status || 503).json({ error: "Camera access service unavailable" }); }
  };
}

function createRegistrationRouter({ pool, schema }) {
  const router = require("express").Router();
  for (const route of ["/register", "/heartbeat"]) router.post(route, async (req, res) => {
    try {
      const body = req.body ?? {};
      let bindings;
      try {
        bindings = JSON.parse(process.env.CAMERA_DEVICE_BINDINGS || "{}");
        if (!bindings || Array.isArray(bindings) || typeof bindings !== "object" ||
            Object.keys(bindings).length === 0 || Object.entries(bindings).some(([id, value]) =>
              !/^[A-Za-z0-9._:-]{1,150}$/.test(id) || !value || typeof value !== "object" ||
              typeof value.key !== "string" || value.key.length < 32 ||
              typeof value.farmId !== "string" || !value.farmId.trim() ||
              (value.zoneId != null && (typeof value.zoneId !== "string" || !value.zoneId.trim())))) {
          throw new Error("Invalid camera binding configuration");
        }
      } catch {
        return res.status(503).json({ error: "Camera registration configuration unavailable",
          code: "CAMERA_BINDINGS_UNAVAILABLE" });
      }
      const binding = typeof body.cameraId === "string" && Object.hasOwn(bindings, body.cameraId) && bindings[body.cameraId];
      const supplied = req.get("X-Camera-Key");
      if (!binding || typeof binding.key !== "string" || binding.key.length < 32 ||
          typeof supplied !== "string" || Buffer.byteLength(supplied) !== Buffer.byteLength(binding.key) ||
          !crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(binding.key))) {
        return res.status(401).json({ error: "Invalid device credentials" });
      }
      if (!Number.isSafeInteger(body.announcedAt) || Math.abs(Date.now() - body.announcedAt) > 30000) {
        return res.status(400).json({ error: "Fresh announcedAt epoch milliseconds required" });
      }
      const current = await getCamera(pool, schema, body.cameraId);
      if (!current) return res.status(404).json({ error: "Operator must provision camera ownership first" });
      if (!binding.farmId || current.farm_id !== binding.farmId ||
          (binding.zoneId && current.zone_id !== binding.zoneId) ||
          ["farmId", "zoneId", "treeId"].some(key => Object.hasOwn(body, key))) {
        return res.status(403).json({ error: "Device scope cannot be changed by firmware" });
      }
      const protocol = body.protocol ?? current.protocol;
      const port = body.port ?? current.port;
      if (!["http", "https"].includes(protocol) || !Number.isInteger(port) || port < 1 || port > 65535 ||
          typeof body.ipAddress !== "string") return res.status(400).json({ error: "Explicit address, HTTP scheme and valid port required" });
      const origin = cameraOrigin(`${protocol}://${body.ipAddress}:${port}`);
      const device = await heartbeat(pool, schema, { deviceId: body.cameraId, ...origin,
        expectedFarmId: binding.farmId, announcedAt: body.announcedAt });
      if (!device) {
        const latest = await getCamera(pool, schema, body.cameraId);
        if (!latest || latest.farm_id !== binding.farmId ||
            new Date(latest.last_seen).getTime() !== body.announcedAt ||
            latest.ip_address !== origin.ipAddress || Number(latest.port) !== origin.port || latest.protocol !== protocol) {
          return res.status(409).json({ error: "Obsolete camera announcement" });
        }
        return res.json({ status: "ok", device: cameraView(latest) });
      }
      return res.json({ status: route === "/register" ? "registered" : "ok", device: cameraView(device) });
    } catch (error) {
      return res.status(error.status || 503).json({ error: error.status === 400 ? "Invalid camera origin" : "Camera registration unavailable" });
    }
  });
  return router;
}

module.exports = { cameraOrigin, cameraView, cameraAccess, createRegistrationRouter };
