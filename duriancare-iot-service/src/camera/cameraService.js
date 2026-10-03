"use strict";

const fs    = require("fs");
const path  = require("path");
const http  = require("http");
const https = require("https");

// ── Helpers ───────────────────────────────────────────────────────────────────

function ensureCameraDir(uploadsDir) {
  const dir = path.resolve(uploadsDir, "cameras");
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function downloadImageBuffer(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith("https") ? https : http;
    const req = client.get(url, {
      timeout: 10_000,
      headers: { Connection: "close", Accept: "image/jpeg" },
    }, (res) => {
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`ESP32-CAM returned HTTP ${res.statusCode}`));
      }
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end",  ()      => resolve(Buffer.concat(chunks)));
      res.on("error", reject);
    });
    req.on("error", reject);
    req.on("timeout", () => {
      req.destroy();
      reject(new Error("ESP32-CAM connection timed out"));
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

async function captureAndStore({
  pool, schema, deviceId, captureType,
  esp32CameraUrl, esp32CapturePath, serverBaseUrl, uploadsDir, aiServiceUrl,
}) {
  const cameraDir = ensureCameraDir(uploadsDir);
  const filename  = `cam_${Date.now()}.jpg`;
  const filepath  = path.join(cameraDir, filename);

  const buf = await downloadImageBuffer(`${esp32CameraUrl}${esp32CapturePath}`);
  fs.writeFileSync(filepath, buf);

  const imageUrl   = `${serverBaseUrl}/api/v1/camera/image/${filename}`;
  const capturedAt = new Date().toISOString();

  // Insert with PROCESSING status so frontend can show a spinner immediately.
  const initStatus = aiServiceUrl ? "PROCESSING" : null;
  const { rows } = await pool.query(
    `INSERT INTO "${schema}".camera_captures
       (device_id, image_url, capture_type, captured_at, ai_status)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING ${CAPTURE_COLS}`,
    [deviceId, imageUrl, captureType, capturedAt, initStatus],
  );
  const capture = rows[0];

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
    return updated[0];
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

module.exports = { captureAndStore, listCaptures, listSchedules, replaceSchedules };
