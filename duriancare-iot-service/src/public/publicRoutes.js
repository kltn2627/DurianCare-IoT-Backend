"use strict";

const express = require("express");
const crypto  = require("crypto");


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

  router.use("/cameras", require("../camera/registryRuntime").createRegistrationRouter({ pool, schema }));
  router.use("/devices", require("./deviceConfigRoutes").createDeviceConfigRouter());

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
