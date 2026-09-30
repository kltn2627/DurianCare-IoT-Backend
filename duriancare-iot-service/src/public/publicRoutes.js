"use strict";

const express = require("express");
const crypto  = require("crypto");

const { upsertCamera, heartbeat: heartbeatCamera } = require("../camera/deviceService");
const config = require("../config");

const VALID_STATUSES = ["PLANNED", "GROWING", "HARVESTING", "EVALUATING", "EXPORTED"];

function generateTraceabilityCode(variety, batchCode) {
  const year   = new Date().getFullYear();
  const suffix = crypto.randomBytes(4).toString("hex").toUpperCase();
  const v      = (variety || "DURIAN").replace(/\s+/g, "-").toUpperCase().slice(0, 6);
  return `VN-${v}-${year}-${suffix}`;
}

function createPublicRouter({ pool, schema }) {
  const router = express.Router();
  const batchTable  = `"${schema}".farming_batches`;
  const chemTable   = `"${schema}".batch_chemical_applications`;
  const assessTable = `"${schema}".export_assessments`;
  const telTable    = `"${schema}".telemetry`;

  // ── Camera key middleware ────────────────────────────────────────────────
  // Validates X-Camera-Key header for camera self-registration endpoints.
  function requireCameraKey(req, res, next) {
    const key = req.headers["x-camera-key"];
    if (!key || key !== config.cameraRegistrationKey) {
      return res.status(401).json({ error: "Invalid or missing X-Camera-Key" });
    }
    next();
  }

  // Helper: extract real client IP (trusts X-Real-IP or X-Forwarded-For from
  // the gateway/reverse-proxy only; does NOT blindly trust all forwarded headers).
  function getClientIp(req) {
    // The gateway sets X-Real-IP when running behind nginx/Spring Cloud Gateway
    const xRealIp = req.headers["x-real-ip"];
    if (xRealIp && isValidIp(xRealIp)) return xRealIp;
    const xForwarded = req.headers["x-forwarded-for"];
    if (xForwarded) {
      const first = xForwarded.split(",")[0].trim();
      if (isValidIp(first)) return first;
    }
    return req.socket?.remoteAddress ?? "unknown";
  }

  function isValidIp(value) {
    if (!value || typeof value !== "string") return false;
    const trimmed = value.trim();
    // IPv4
    if (/^(\d{1,3}\.){3}\d{1,3}$/.test(trimmed)) return true;
    // IPv6 (simplified check)
    if (trimmed.includes(":")) return true;
    return false;
  }

  // ── POST /cameras/register ───────────────────────────────────────────────
  // ESP32-CAM calls this on boot / Wi-Fi reconnect to announce its current IP.
  // Requires X-Camera-Key header matching CAMERA_REGISTRATION_KEY.
  //
  // Body: { cameraId, ipAddress, port?, protocol?, macAddress?,
  //         firmwareVersion?, ssid?, treeId?, zoneId?, farmId?, capabilities? }
  router.post("/cameras/register", requireCameraKey, async (req, res) => {
    const {
      cameraId, ipAddress, port, protocol,
      macAddress, firmwareVersion, ssid,
      treeId, zoneId, farmId, capabilities,
    } = req.body ?? {};

    if (!cameraId || typeof cameraId !== "string" || !cameraId.trim()) {
      return res.status(400).json({ error: "cameraId is required" });
    }

    // Use explicit ipAddress if provided; fall back to detected client IP
    const resolvedIp = (typeof ipAddress === "string" && ipAddress.trim())
      ? ipAddress.trim()
      : getClientIp(req);

    if (!isValidIp(resolvedIp)) {
      return res.status(400).json({ error: "Unable to determine a valid IP address" });
    }

    try {
      const device = await upsertCamera(pool, schema, {
        deviceId:        cameraId.trim(),
        macAddress:      typeof macAddress === "string" ? macAddress.trim() : undefined,
        ipAddress:       resolvedIp,
        port:            typeof port === "number" ? port : 80,
        protocol:        protocol === "https" ? "https" : "http",
        firmwareVersion: typeof firmwareVersion === "string" ? firmwareVersion.trim() : undefined,
        ssid:            typeof ssid === "string" ? ssid.trim() : undefined,
        treeId:          typeof treeId === "string" ? treeId.trim() : undefined,
        zoneId:          typeof zoneId === "string" ? zoneId.trim() : undefined,
        farmId:          typeof farmId === "string" ? farmId.trim() : undefined,
        capabilities:    Array.isArray(capabilities) ? capabilities : undefined,
      });
      console.log(`[camera-register] ${cameraId} registered with IP ${resolvedIp}`);
      return res.status(200).json({ status: "registered", device });
    } catch (err) {
      console.error("[camera-register] Failed:", err.message);
      return res.status(500).json({ error: "Registration failed" });
    }
  });

  // ── POST /cameras/heartbeat ──────────────────────────────────────────────
  // ESP32-CAM calls this periodically (e.g. every 60 s) to stay online.
  // Requires X-Camera-Key header.
  //
  // Body: { cameraId, ipAddress?, port? }
  router.post("/cameras/heartbeat", requireCameraKey, async (req, res) => {
    const { cameraId, ipAddress, port } = req.body ?? {};

    if (!cameraId || typeof cameraId !== "string" || !cameraId.trim()) {
      return res.status(400).json({ error: "cameraId is required" });
    }

    const resolvedIp = (typeof ipAddress === "string" && ipAddress.trim())
      ? ipAddress.trim()
      : getClientIp(req);

    try {
      const device = await heartbeatCamera(pool, schema, {
        deviceId:  cameraId.trim(),
        ipAddress: resolvedIp,
        port:      typeof port === "number" ? port : 80,
      });

      if (!device) {
        // Camera has not registered yet — auto-register with minimal info
        const registered = await upsertCamera(pool, schema, {
          deviceId:  cameraId.trim(),
          ipAddress: resolvedIp,
          port:      typeof port === "number" ? port : 80,
          protocol:  "http",
        });
        console.log(`[camera-heartbeat] Auto-registered ${cameraId} from heartbeat`);
        return res.status(200).json({ status: "registered", device: registered });
      }

      return res.status(200).json({ status: "ok", device });
    } catch (err) {
      console.error("[camera-heartbeat] Failed:", err.message);
      return res.status(500).json({ error: "Heartbeat failed" });
    }
  });

  // ── GET /traceability/:code ─────────────────────────────────────────────
  // Public endpoint — no authentication required.
  router.get("/traceability/:code", async (req, res) => {
    const { code } = req.params;
    if (!code || code.length > 120) {
      return res.status(400).json({ error: "Invalid traceability code" });
    }

    try {
      const batchResult = await pool.query(
        `SELECT fb.id, fb.batch_code, fb.device_id, fb.variety, fb.farm_name,
                fb.start_date, fb.harvest_date, fb.target_market,
                fb.status, fb.traceability_code, fb.export_score, fb.finalized_at,
                fb.notes
         FROM ${batchTable} fb
         WHERE fb.traceability_code = $1 AND fb.status = 'EXPORTED'`,
        [code]
      );

      if (batchResult.rowCount === 0) {
        return res.status(404).json({ error: "Traceability record not found" });
      }

      const batch = batchResult.rows[0];

      // Chemical applications log
      const chemResult = await pool.query(
        `SELECT chemical_id, applied_at, dose_kg_per_ha, stage
         FROM ${chemTable}
         WHERE batch_id = $1
         ORDER BY applied_at ASC`,
        [batch.id]
      );

      // Latest export assessment
      const assessResult = await pool.query(
        `SELECT overall_score, residue_score, env_score, disease_score,
                phi_score, soil_score, risk_summary, recommendations, assessed_at
         FROM ${assessTable}
         WHERE device_id = $1
         ORDER BY assessed_at DESC
         LIMIT 1`,
        [batch.device_id]
      );

      // Environmental averages from IoT sensors over the batch period
      const startDate = batch.start_date ? new Date(batch.start_date).toISOString() : null;
      const endDate   = batch.harvest_date
        ? new Date(batch.harvest_date).toISOString()
        : new Date().toISOString();

      let envAverages = null;
      if (startDate) {
        const envResult = await pool.query(
          `SELECT
             ROUND(AVG(temperature)::numeric, 1)   AS avg_temperature,
             ROUND(AVG(humidity)::numeric, 1)       AS avg_humidity,
             ROUND(AVG(soil_moisture)::numeric, 1)  AS avg_soil_moisture,
             COUNT(*)                                AS reading_count
           FROM ${telTable}
           WHERE device_id = $1
             AND "timestamp" BETWEEN $2 AND $3`,
          [batch.device_id, startDate, endDate]
        ).catch(() => ({ rows: [null] }));
        envAverages = envResult.rows[0];
      }

      const assessment = assessResult.rows[0] || null;

      return res.json({
        traceability_code: batch.traceability_code,
        farm: {
          name:          batch.farm_name || "Nông trại DurianCare",
          variety:       batch.variety   || "Sầu riêng",
          target_market: batch.target_market,
          batch_code:    batch.batch_code,
          start_date:    batch.start_date,
          harvest_date:  batch.harvest_date,
          finalized_at:  batch.finalized_at,
        },
        export_score: batch.export_score,
        certification: {
          status:    "PASS",
          standards: certStandards(batch.target_market),
        },
        chemical_log:    chemResult.rows,
        assessment_summary: assessment
          ? {
              overall_score: assessment.overall_score,
              residue_score: assessment.residue_score,
              env_score:     assessment.env_score,
              disease_score: assessment.disease_score,
              assessed_at:   assessment.assessed_at,
              risk_summary:  assessment.risk_summary,
            }
          : null,
        env_averages: envAverages,
      });
    } catch (err) {
      console.error("[public-traceability] Failed:", err.message);
      return res.status(500).json({ error: "Failed to retrieve traceability data" });
    }
  });

  return router;
}

function certStandards(market) {
  const map = {
    CHINA:    ["China GACC", "VietGAP"],
    EU:       ["GlobalGAP", "EU MRL Regulation 396/2005"],
    US:       ["FDA FSMA", "US EPA Tolerances"],
    JAPAN:    ["JAS", "Positive List System"],
    DOMESTIC: ["VietGAP", "HACCP"],
  };
  return map[market] || ["VietGAP"];
}

module.exports = { createPublicRouter, generateTraceabilityCode };
