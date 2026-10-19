"use strict";

const crypto = require("crypto");
const fs    = require("fs");
const path  = require("path");
const http  = require("http");
const https = require("https");
const captures = require("../workDrain").createWorkDrain();
let stopping = false;
// Captures are stored only on the local uploads volume; the IoT service has no S3 path.
const STORAGE_LOCAL = "LOCAL";

// ── Helpers ───────────────────────────────────────────────────────────────────

function ensureCameraDir(uploadsDir) {
  const dir = path.resolve(uploadsDir, "cameras");
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  return dir;
}

// Two separate limits: an idle limit (no connect / no bytes for N ms) catches a dead or stalled camera,
// while a larger total cap still bounds the request. A weak Wi-Fi link can deliver a valid UXGA frame
// slowly (measured 2026-10-18: first byte 0.17 s, 66.5 KB in 7.1 s), which a 10 s total cap cut off.
function cameraDownloadLimits() {
  const idleMs = Number(process.env.CAMERA_DOWNLOAD_IDLE_MS) || 10_000;
  const totalMs = Number(process.env.CAMERA_DOWNLOAD_TOTAL_MS) || 30_000;
  return { idleMs, totalMs: Math.max(totalMs, idleMs) };
}

function downloadImageBuffer(url) {
  const { idleMs, totalMs } = cameraDownloadLimits();
  return new Promise((resolve, reject) => {
    const client = url.startsWith("https") ? https : http;
    const req = client.get(url, {
      timeout: idleMs,
      headers: { Connection: "close", Accept: "image/jpeg" },
    }, (res) => {
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`ESP32-CAM returned HTTP ${res.statusCode}`));
      }
      if ((res.headers["content-type"] || "").split(";", 1)[0].trim().toLowerCase() !== "image/jpeg") {
        res.resume();
        return reject(new Error("ESP32-CAM did not return image/jpeg"));
      }
      const chunks = [];
      let bytes = 0;
      res.on("data", (chunk) => {
        bytes += chunk.length;
        if (bytes > 4 * 1024 * 1024) return res.destroy(new Error("Camera frame too large"));
        chunks.push(chunk);
      });
      res.on("end", () => {
        const buffer = Buffer.concat(chunks);
        if (buffer.length < 4 || buffer[0] !== 0xFF || buffer[1] !== 0xD8 ||
            buffer[2] !== 0xFF || buffer[buffer.length - 2] !== 0xFF || buffer[buffer.length - 1] !== 0xD9) {
          return reject(new Error("ESP32-CAM returned an empty or incomplete JPEG frame"));
        }
        resolve(buffer);
      });
      res.on("error", reject);
    });
    req.on("error", reject);
    const deadline = setTimeout(
      () => req.destroy(new Error(`ESP32-CAM transfer exceeded ${totalMs} ms total`)), totalMs);
    req.once("close", () => clearTimeout(deadline));
    req.on("timeout", () => {
      req.destroy();
      reject(new Error(`ESP32-CAM connection timed out (no data for ${idleMs} ms)`));
    });
  });
}

// Calls the FastAPI AI service with the captured image buffer.
// Uses native fetch + FormData (Node.js 18+).
async function runAiDiagnosis({ imageBuffer, filename, deviceId, aiServiceUrl }) {
  const blob     = new Blob([imageBuffer], { type: "image/jpeg" });
  const formData = new FormData();
  formData.append("image",     blob, filename);
  formData.append("source",    "IOT_CAMERA");
  formData.append("device_id", deviceId);

  const res = await fetch(`${aiServiceUrl}/api/v1/predict`, {
    method: "POST",
    body:   formData,
    signal: AbortSignal.timeout(60_000),
  });

  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`AI service HTTP ${res.status}: ${body.slice(0, 200)}`);
  }

  const json = await res.json();
  if (json.status !== "success" || !json.data) {
    throw new Error("AI service returned unexpected response format");
  }

  const { predictedDisease, confidence, recommendation, decisionSupport, topPredictions } = json.data;

  // confidence arrives as a string "94.23%" — extract the float
  const confidenceScore = parseFloat(confidence) / 100;

  return {
    disease_detected: predictedDisease ?? null,
    confidence_score: Number.isFinite(confidenceScore) ? confidenceScore : null,
    diagnosis_result: {
      predictedDisease:  predictedDisease ?? null,
      confidence:        confidence ?? null,
      confidenceScore:   Number.isFinite(confidenceScore) ? confidenceScore : null,
      riskLevel:         decisionSupport?.riskLevel   ?? null,
      vietnameseName:    recommendation?.vietnameseName ?? null,
      diseaseSummary:    recommendation?.diseaseSummary ?? null,
      severity:          recommendation?.severity        ?? null,
      symptoms:          (recommendation?.symptoms ?? []).map((s) => s.text).filter(Boolean),
      immediateActions:  decisionSupport?.immediateActions ?? [],
      farmerNotes:       decisionSupport?.farmerNotes      ?? [],
      topPredictions:    (topPredictions ?? []).slice(0, 5),
    },
  };
}

const CAPTURE_COLS =
  `id, device_id, image_url, capture_type, captured_at,
   ai_status, disease_detected, confidence_score, diagnosis_result, notes`;

// ── Public API ────────────────────────────────────────────────────────────────

function captureAndStore(options) {
  if (stopping) return Promise.reject(new Error("Camera service shutting down"));
  return captures.run(() => persistCapture(options));
}

async function drainCaptures() {
  stopping = true;
  captures.stop();
  await captures.drain();
}

async function persistCapture({
  pool, schema, deviceId, captureType,
  esp32CameraUrl, esp32CapturePath, serverBaseUrl, uploadsDir, aiServiceUrl,
}) {
  const cameraDir = ensureCameraDir(uploadsDir);
  // Digits only (route accepts ^cam_\d+\.jpg$); random suffix avoids same-millisecond collisions.
  const filename  = `cam_${Date.now()}${String(crypto.randomInt(0, 1_000_000)).padStart(6, "0")}.jpg`;
  const filepath  = path.join(cameraDir, filename);

  const downloadStarted = Date.now();
  const buf = await downloadImageBuffer(`${esp32CameraUrl}${esp32CapturePath}`);
  console.log(`[camera] frame received device=${deviceId} bytes=${buf.length} ms=${Date.now() - downloadStarted}`);
  // Local persistent storage (uploads volume). Write to a temp file and rename so a crash never
  // leaves a partial JPEG under the served name.
  const tmpPath = `${filepath}.${process.pid}.tmp`;
  try {
    fs.writeFileSync(tmpPath, buf, { flag: "wx" });
    fs.renameSync(tmpPath, filepath);
  } catch (err) {
    fs.rmSync(tmpPath, { force: true });
    throw new Error(`Local image storage failed: ${err.code || err.message}`);
  }

  const imageUrl   = `${serverBaseUrl}/api/v1/camera/image/${filename}`;
  const capturedAt = new Date().toISOString();

  // Insert with PROCESSING status so frontend can show a spinner immediately.
  const initStatus = aiServiceUrl ? "PROCESSING" : null;
  let capture;
  try {
    const { rows } = await pool.query(
      `INSERT INTO "${schema}".camera_captures
         (device_id, image_url, capture_type, captured_at, ai_status)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING ${CAPTURE_COLS}`,
      [deviceId, imageUrl, captureType, capturedAt, initStatus],
    );
    capture = { ...rows[0], storage: STORAGE_LOCAL };
  } catch (err) {
    fs.rmSync(filepath, { force: true });  // no orphan file without a record
    throw err;
  }

  if (!aiServiceUrl) return capture;

  // AI diagnosis — failure is non-fatal; never crashes the capture flow.
  try {
    const ai = await runAiDiagnosis({ imageBuffer: buf, filename, deviceId, aiServiceUrl });
    const { rows: updated } = await pool.query(
      `UPDATE "${schema}".camera_captures
       SET ai_status        = 'COMPLETED',
           disease_detected = $1,
           confidence_score = $2,
           diagnosis_result = $3
       WHERE id = $4
       RETURNING ${CAPTURE_COLS}`,
      [ai.disease_detected, ai.confidence_score, JSON.stringify(ai.diagnosis_result), capture.id],
    );
    return { ...updated[0], storage: STORAGE_LOCAL };
  } catch (err) {
    console.error("[camera] AI diagnosis failed for capture", capture.id, "—", err.message);
    await pool.query(
      `UPDATE "${schema}".camera_captures SET ai_status = 'FAILED' WHERE id = $1`,
      [capture.id],
    ).catch(() => {});
    return { ...capture, ai_status: "FAILED" };
  }
}

async function listCaptures({ pool, schema, deviceId, from, to, limit, offset }) {
  const conditions = [];
  const params     = [];

  if (deviceId) { params.push(deviceId); conditions.push(`device_id   = $${params.length}`); }
  if (from)     { params.push(from);     conditions.push(`captured_at >= $${params.length}`); }
  if (to)       { params.push(to);       conditions.push(`captured_at <= $${params.length}`); }

  const where = conditions.length ? `WHERE ${conditions.join(" AND ")}` : "";
  params.push(limit, offset);

  const { rows } = await pool.query(
    `SELECT ${CAPTURE_COLS}
     FROM "${schema}".camera_captures
     ${where}
     ORDER BY captured_at DESC
     LIMIT $${params.length - 1} OFFSET $${params.length}`,
    params,
  );
  return { data: rows, limit, offset, count: rows.length };
}

async function listSchedules({ pool, schema, deviceId }) {
  const { rows } = await pool.query(
    `SELECT id, device_id, cron_expression, label, enabled, created_at, updated_at
     FROM "${schema}".camera_schedules
     WHERE device_id = $1
     ORDER BY cron_expression ASC`,
    [deviceId],
  );
  return rows;
}

async function replaceSchedules({ pool, schema, deviceId, schedules }) {
  await pool.query(
    `DELETE FROM "${schema}".camera_schedules WHERE device_id = $1`,
    [deviceId],
  );
  const rows = [];
  for (const s of schedules) {
    const { rows: inserted } = await pool.query(
      `INSERT INTO "${schema}".camera_schedules
         (device_id, cron_expression, label, enabled)
       VALUES ($1, $2, $3, $4)
       RETURNING id, device_id, cron_expression, label, enabled, created_at, updated_at`,
      [deviceId, s.cron_expression, s.label ?? null, s.enabled !== false],
    );
    rows.push(inserted[0]);
  }
  return rows;
}

module.exports = { cameraDownloadLimits, captureAndStore, drainCaptures, listCaptures, listSchedules, replaceSchedules };
