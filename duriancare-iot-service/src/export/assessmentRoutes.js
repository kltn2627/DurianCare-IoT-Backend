"use strict";

const express = require("express");
const { runAssessment } = require("./assessmentService");
const { VALID_MARKETS, MARKET_NAMES, CHEMICALS } = require("./assessmentData");
const { generateTraceabilityCode } = require("../public/publicRoutes");

const VALID_STATUSES = ["PLANNED", "GROWING", "HARVESTING", "EVALUATING", "EXPORTED"];

function createAssessmentRouter({ pool, schema }) {
  const router = express.Router();
  const telemetryTable = `"${schema}".telemetry`;
  const capturesTable  = `"${schema}".camera_captures`;
  const assessTable    = `"${schema}".export_assessments`;

  // ── GET /chemicals — list available chemicals ─────────────────────────────
  router.get("/chemicals", (_req, res) => {
    const list = Object.entries(CHEMICALS).map(([id, c]) => ({ id, ...c }));
    return res.json({ chemicals: list });
  });

  // ── GET /markets — list target markets ───────────────────────────────────
  router.get("/markets", (_req, res) => {
    const list = VALID_MARKETS.map((m) => ({ id: m, label: MARKET_NAMES[m] }));
    return res.json({ markets: list });
  });

  // ── GET /batches — list farming batches ──────────────────────────────────
  router.get("/batches", async (req, res) => {
    const batchTable = `"${schema}".farming_batches`;
    const chemTable  = `"${schema}".batch_chemical_applications`;
    try {
      const result = await pool.query(
        `SELECT fb.id, fb.batch_code, fb.device_id, fb.cam_device_id,
                fb.variety, fb.farm_name, fb.start_date, fb.harvest_date,
                fb.target_market, fb.notes, fb.created_at,
                COALESCE(
                  json_agg(
                    json_build_object(
                      'chemical_id', bc.chemical_id,
                      'applied_at',  bc.applied_at,
                      'dose_kg_per_ha', bc.dose_kg_per_ha,
                      'stage', bc.stage
                    )
                  ) FILTER (WHERE bc.id IS NOT NULL), '[]'
                ) AS chemical_applications
         FROM ${batchTable} fb
         LEFT JOIN ${chemTable} bc ON bc.batch_id = fb.id
         GROUP BY fb.id
         ORDER BY fb.created_at DESC`,
      );
      return res.json({ batches: result.rows });
    } catch (err) {
      console.error("[export-assessment] Batches failed:", err.message);
      return res.status(500).json({ error: "Failed to retrieve batches" });
    }
  });

  // ── POST /evaluate — run a new assessment ─────────────────────────────────
  // Body (option A — manual):
  //   device_id              string (required unless batch_id given)
  //   target_market          CHINA|EU|US|JAPAN|DOMESTIC (required unless batch_id given)
  //   harvest_date           ISO date (optional)
  //   chemical_applications  array of { chemical_id, applied_at, dose_kg_per_ha }
  //
  // Body (option B — batch-based):
  //   batch_id               UUID of a farming_batch row — auto-loads chemicals, device, market
  router.post("/evaluate", async (req, res) => {
    let {
      device_id,
      target_market,
      harvest_date,
      chemical_applications = [],
      batch_id,
    } = req.body ?? {};

    // If batch_id provided: load batch data from DB and override manual fields
    if (batch_id) {
      const batchTable = `"${schema}".farming_batches`;
      const chemTable  = `"${schema}".batch_chemical_applications`;
      try {
        const batchRow = await pool.query(
          `SELECT device_id, target_market, harvest_date FROM ${batchTable} WHERE id = $1`,
          [batch_id]
        );
        if (batchRow.rowCount === 0) {
          return res.status(404).json({ error: `batch_id not found: ${batch_id}` });
        }
        const b = batchRow.rows[0];
        device_id    = device_id    || b.device_id;
        target_market = target_market || b.target_market;
        harvest_date  = harvest_date  || (b.harvest_date ? b.harvest_date.toISOString().slice(0, 10) : null);

        if (!chemical_applications.length) {
          const chemRows = await pool.query(
            `SELECT chemical_id, applied_at::text AS applied_at, dose_kg_per_ha
             FROM ${chemTable} WHERE batch_id = $1`,
            [batch_id]
          );
          chemical_applications = chemRows.rows;
        }
      } catch (err) {
        console.error("[export-assessment] Batch lookup failed:", err.message);
        return res.status(500).json({ error: "Batch lookup failed: " + err.message });
      }
    }

    if (!device_id || typeof device_id !== "string" || !device_id.trim()) {
      return res.status(400).json({ error: "device_id is required" });
    }
    if (!VALID_MARKETS.includes(target_market)) {
      return res.status(400).json({ error: `target_market must be one of: ${VALID_MARKETS.join(", ")}` });
    }
    if (!Array.isArray(chemical_applications)) {
      return res.status(400).json({ error: "chemical_applications must be an array" });
    }

    try {
      // Pull last 30 days of sensor data
      const thirtyDaysAgo = new Date(Date.now() - 30 * 86400_000).toISOString();
      const [sensorResult, diagnosisResult] = await Promise.all([
        pool.query(
          `SELECT temperature, humidity AS air_humidity, soil_moisture
           FROM ${telemetryTable}
           WHERE device_id = $1 AND "timestamp" >= $2
           ORDER BY "timestamp" DESC LIMIT 500`,
          [device_id.trim(), thirtyDaysAgo]
        ),
        pool.query(
          `SELECT disease_detected, confidence_score, diagnosis_result
           FROM ${capturesTable}
           WHERE device_id = $1 AND captured_at >= $2 AND ai_status = 'COMPLETED'
           ORDER BY captured_at DESC LIMIT 50`,
          [device_id.trim(), thirtyDaysAgo]
        ).catch(() => ({ rows: [] })),   // non-fatal: camera table may not exist yet
      ]);

      const result = runAssessment({
        targetMarket:          target_market,
        chemicalApplications:  chemical_applications,
        harvestDate:           harvest_date,
        sensorRows:            sensorResult.rows,
        diagnosisRows:         diagnosisResult.rows,
      });

      // Persist assessment
      const insertResult = await pool.query(
        `INSERT INTO ${assessTable}
          (device_id, target_market, overall_score, residue_score, env_score, disease_score, phi_score, soil_score, risk_summary, recommendations, harvest_date)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
         RETURNING id, assessed_at`,
        [
          device_id.trim(),
          target_market,
          result.overall_score,
          result.residue_score,
          result.env_score,
          result.disease_score,
          result.phi_score,
          result.soil_score,
          JSON.stringify(result.risk_summary),
          JSON.stringify(result.recommendations),
          harvest_date || null,
        ]
      );

      return res.status(201).json({
        id:            insertResult.rows[0].id,
        device_id:     device_id.trim(),
        target_market,
        market_label:  MARKET_NAMES[target_market],
        assessed_at:   insertResult.rows[0].assessed_at,
        ...result,
      });
    } catch (err) {
      console.error("[export-assessment] Evaluate failed:", err.message);
      return res.status(500).json({ error: "Assessment failed: " + err.message });
    }
  });

  // ── GET /history — list past assessments for a device ───────────────────
  router.get("/history", async (req, res) => {
    const { device_id } = req.query;
    const limit  = Math.min(parseInt(req.query.limit,  10) || 20, 50);
    const offset = Math.max(parseInt(req.query.offset, 10) || 0,  0);

    try {
      const result = await pool.query(
        `SELECT id, device_id, target_market, overall_score, residue_score,
                env_score, disease_score, phi_score, soil_score,
                risk_summary, recommendations, harvest_date, assessed_at
         FROM ${assessTable}
         WHERE ($1::text IS NULL OR device_id = $1)
         ORDER BY assessed_at DESC
         LIMIT $2 OFFSET $3`,
        [device_id || null, limit, offset]
      );

      return res.json({
        data:  result.rows.map((r) => ({
          ...r,
          market_label: MARKET_NAMES[r.target_market] || r.target_market,
        })),
        count: result.rows.length,
        limit,
        offset,
      });
    } catch (err) {
      console.error("[export-assessment] History failed:", err.message);
      return res.status(500).json({ error: "Failed to retrieve assessment history" });
    }
  });

  // ── PATCH /batches/:id/status — update lifecycle status ──────────────────
  router.patch("/batches/:id/status", async (req, res) => {
    const { id } = req.params;
    const { status } = req.body ?? {};

    if (!VALID_STATUSES.includes(status)) {
      return res.status(400).json({
        error: `status must be one of: ${VALID_STATUSES.join(", ")}`,
      });
    }
    if (status === "EXPORTED") {
      return res.status(400).json({
        error: "Use POST /batches/:id/finalize to move a batch to EXPORTED status",
      });
    }

    try {
      const result = await pool.query(
        `UPDATE ${`"${schema}".farming_batches`}
         SET status = $1
         WHERE id = $2
         RETURNING id, batch_code, status`,
        [status, id]
      );
      if (result.rowCount === 0) {
        return res.status(404).json({ error: "Batch not found" });
      }
      return res.json(result.rows[0]);
    } catch (err) {
      console.error("[export-assessment] Status update failed:", err.message);
      return res.status(500).json({ error: "Failed to update batch status" });
    }
  });

  // ── POST /batches/:id/finalize — export batch, generate traceability code ─
  router.post("/batches/:id/finalize", async (req, res) => {
    const { id } = req.params;
    const batchTable = `"${schema}".farming_batches`;

    try {
      const batchRow = await pool.query(
        `SELECT id, batch_code, variety, status FROM ${batchTable} WHERE id = $1`,
        [id]
      );
      if (batchRow.rowCount === 0) {
        return res.status(404).json({ error: "Batch not found" });
      }
      const batch = batchRow.rows[0];
      if (batch.status !== "EVALUATING") {
        return res.status(400).json({
          error: `Batch must be in EVALUATING status to finalize (current: ${batch.status})`,
        });
      }

      // Pull the latest assessment score for this batch's device
      const assessRow = await pool.query(
        `SELECT ea.overall_score
         FROM ${`"${schema}".export_assessments`} ea
         JOIN ${batchTable} fb ON fb.device_id = ea.device_id
         WHERE fb.id = $1
         ORDER BY ea.assessed_at DESC
         LIMIT 1`,
        [id]
      );
      const exportScore = assessRow.rows[0]?.overall_score ?? null;

      if (exportScore !== null && exportScore < 70) {
        return res.status(422).json({
          error: `Batch score ${exportScore} is below minimum threshold (70) for export`,
          export_score: exportScore,
        });
      }

      const code = generateTraceabilityCode(batch.variety, batch.batch_code);
      const updateResult = await pool.query(
        `UPDATE ${batchTable}
         SET status = 'EXPORTED',
             traceability_code = $1,
             export_score      = $2,
             finalized_at      = NOW()
         WHERE id = $3
         RETURNING id, batch_code, status, traceability_code, export_score, finalized_at`,
        [code, exportScore, id]
      );

      const row = updateResult.rows[0];
      return res.status(201).json({
        ...row,
        traceability_url: `/traceability/${row.traceability_code}`,
      });
    } catch (err) {
      console.error("[export-assessment] Finalize failed:", err.message);
      return res.status(500).json({ error: "Failed to finalize batch: " + err.message });
    }
  });

  return router;
}

module.exports = { createAssessmentRouter };
