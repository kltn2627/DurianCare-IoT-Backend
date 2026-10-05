"use strict";

/**
 * Demo seed script — inserts two farming batches for Export Compliance demo.
 *
 * PASS scenario (BATCH-EXPORT-PASS-2026):
 *   Device esp32-demo-pass | Target market: CHINA
 *   Expected overall score: ~95% PASS
 *
 * FAIL scenario (BATCH-EXPORT-FAIL-2026):
 *   Device esp32-demo-fail | Target market: EU
 *   Expected overall score: ~38% FAIL (metalaxyl MRL violation + all PHI missed)
 *
 * Usage (from duriancare-iot-service directory):
 *   node scripts/seed-demo.js
 *   node scripts/seed-demo.js --reset   # drops existing demo records first
 */

require("dotenv").config();
const { Pool } = require("pg");
const { runAssessment } = require("../src/export/assessmentService");

const config = require("../src/config");
const RESET = process.argv.includes("--reset");

// ─── Database connection ──────────────────────────────────────────────────────

const pool = new Pool({
  host:     config.postgres.host,
  port:     config.postgres.port,
  database: config.postgres.database,
  user:     config.postgres.user,
  password: config.postgres.password,
});

// ─── Deterministic IDs (re-runnable with ON CONFLICT DO NOTHING) ──────────────

const USER_ID   = "99900001-0000-4000-a000-000000000001";
const PROF_ID   = "99900002-0000-4000-a000-000000000001";
const PREF_ID   = "99900003-0000-4000-a000-000000000001";
const PASS_BATCH_ID = "aaaa0001-0000-4000-a000-000000000001";
const FAIL_BATCH_ID = "aaaa0002-0000-4000-a000-000000000001";

const DEMO_EMAIL     = "huynh.le.minhduy@duriancare.vn";
const DEMO_PASS_DEV  = "esp32-demo-pass";
const DEMO_FAIL_DEV  = "esp32-demo-fail";
const DEMO_PASS_CAM  = "esp32-cam-pass";
const DEMO_FAIL_CAM  = "esp32-cam-fail";

// ─── Chemical applications ────────────────────────────────────────────────────
// PASS: all PHI cleared, residues negligible vs CHINA MRL
const PASS_CHEMICALS = [
  { chemical_id: "paclobutrazol", applied_at: "2026-03-25", dose_kg_per_ha: 0.8,  stage: "Ra hoa (kích hoa)" },
  { chemical_id: "hexaconazole",  applied_at: "2026-05-20", dose_kg_per_ha: 0.3,  stage: "Phát triển quả (trị nấm)" },
  { chemical_id: "fosetyl_al",    applied_at: "2026-05-30", dose_kg_per_ha: 1.0,  stage: "Phát triển quả (thối rễ)" },
  { chemical_id: "metalaxyl",     applied_at: "2026-07-10", dose_kg_per_ha: 0.5,  stage: "Chín (phòng xì mủ)" },
];

// FAIL: 3 chemicals with PHI not met, metalaxyl dose 67 kg/ha → residue ~0.45 ppm > EU MRL 0.05 ppm
const FAIL_CHEMICALS = [
  { chemical_id: "paclobutrazol", applied_at: "2026-08-05", dose_kg_per_ha: 1.2,  stage: "Gần chín (SAI SÓT - quá muộn)" },
  { chemical_id: "metalaxyl",     applied_at: "2026-08-20", dose_kg_per_ha: 67.0, stage: "Trước thu hoạch (VI PHẠM liều cao)" },
  { chemical_id: "mancozeb",      applied_at: "2026-08-21", dose_kg_per_ha: 2.0,  stage: "Trước thu hoạch (PHI chưa đủ)" },
];

// ─── 30-day sensor readings (temperature °C, humidity %, soil_moisture %) ─────
// PASS: index 0 = today-1d, index 29 = today-30d
// Design: 27/30 temp optimal (28–32), 27/30 hum optimal (70–85), 24/30 soil optimal (70–85)
// → envScore = 90, soilScore = 80

const PASS_SENSOR = [
  // Days 1–24: all optimal temp + humidity + soil
  [29.5, 74.2, 75.8], [30.1, 76.8, 77.2], [28.8, 72.5, 73.1], [31.2, 78.1, 79.4],
  [29.8, 73.9, 75.0], [30.5, 77.4, 78.6], [29.1, 71.8, 72.3], [31.0, 79.2, 80.1],
  [28.6, 73.5, 74.5], [30.3, 76.1, 77.8], [29.7, 75.3, 76.2], [30.8, 77.9, 78.3],
  [28.9, 72.8, 73.5], [31.1, 78.5, 79.8], [29.4, 74.1, 75.3], [30.7, 76.7, 77.1],
  [29.0, 72.0, 72.8], [31.3, 79.8, 80.5], [28.7, 73.2, 74.1], [30.2, 77.1, 78.4],
  [29.6, 75.6, 76.5], [30.6, 76.3, 77.3], [28.8, 73.0, 73.7], [31.2, 78.8, 79.1],
  // Days 25–27: good temp + humidity, soil slightly below optimal
  [29.5, 74.5, 65.2], [30.2, 77.2, 67.8], [28.9, 72.3, 63.4],
  // Days 28–30: temp too low, humidity too high, soil still poor
  [27.2, 87.5, 64.1], [27.5, 88.2, 66.3], [26.8, 86.8, 64.8],
];
// Verification: tempOk=27, humOk=27, soilOk=24 → envScore=90, soilScore=80

// FAIL: 17/30 temp optimal, 17/30 hum optimal, 15/30 soil optimal
// → envScore = 57, soilScore = 50

const FAIL_SENSOR = [
  // Days 1–15: good temp + humidity + soil
  [30.1, 75.2, 76.8], [29.5, 73.8, 74.5], [31.0, 78.5, 79.2], [28.8, 71.2, 72.3],
  [30.7, 76.9, 77.5], [29.2, 74.1, 75.8], [31.2, 79.8, 80.1], [28.6, 70.5, 70.9],
  [30.5, 77.3, 78.4], [29.8, 75.6, 76.2], [31.5, 80.2, 81.3], [28.9, 72.4, 73.1],
  [30.3, 76.8, 77.9], [29.6, 74.5, 75.2], [31.1, 79.1, 80.5],
  // Days 16–17: good temp + humidity, soil flooded
  [30.8, 77.6, 55.2], [29.4, 73.2, 58.7],
  // Days 18–30: heat stress + dry soil
  [34.2, 60.5, 45.3], [35.1, 58.2, 42.8], [33.8, 62.1, 48.6], [35.5, 57.9, 44.1],
  [34.7, 61.3, 47.2], [33.5, 59.8, 50.4], [35.9, 56.2, 43.7], [34.3, 62.8, 46.9],
  [35.2, 58.5, 41.3], [33.9, 60.2, 49.8], [34.8, 57.1, 44.5], [35.4, 63.5, 47.6],
  [33.6, 59.4, 42.1],
];
// Verification: tempOk=17, humOk=17, soilOk=15 → envScore=57, soilScore=50

// ─── Camera captures ──────────────────────────────────────────────────────────
// Each entry: [disease_detected, confidence_score, riskLevel]
// PASS: 10 captures, all HEALTHY_LEAF → diseaseScore = 100

const PASS_CAMERA = [
  ["HEALTHY_LEAF", 0.9400, "LOW"], ["HEALTHY_LEAF", 0.9150, "LOW"],
  ["HEALTHY_LEAF", 0.9550, "LOW"], ["HEALTHY_LEAF", 0.8900, "LOW"],
  ["HEALTHY_LEAF", 0.9300, "LOW"], ["HEALTHY_LEAF", 0.9200, "LOW"],
  ["HEALTHY_LEAF", 0.9600, "LOW"], ["HEALTHY_LEAF", 0.8800, "LOW"],
  ["HEALTHY_LEAF", 0.9100, "LOW"], ["HEALTHY_LEAF", 0.9000, "LOW"],
];

// FAIL: 8 captures — 7 HEALTHY + 1 LEAF_BLIGHT MEDIUM → penalty=25 → diseaseScore=75

const FAIL_CAMERA = [
  ["HEALTHY_LEAF", 0.8700, "LOW"],
  ["HEALTHY_LEAF", 0.8400, "LOW"],
  ["LEAF_BLIGHT",  0.7800, "MEDIUM"],   // ← disease detected
  ["HEALTHY_LEAF", 0.8200, "LOW"],
  ["HEALTHY_LEAF", 0.8500, "LOW"],
  ["HEALTHY_LEAF", 0.8300, "LOW"],
  ["HEALTHY_LEAF", 0.8600, "LOW"],
  ["HEALTHY_LEAF", 0.8100, "LOW"],
];

// ─── Helpers ──────────────────────────────────────────────────────────────────

function daysAgo(n) {
  const d = new Date(Date.now() - n * 86400_000);
  return d.toISOString();
}

function imageUrl(device, idx) {
  return `https://cdn.duriancare.vn/demo/${device}/capture-${idx.toString().padStart(3, "0")}.jpg`;
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  const client = await pool.connect();

  try {
    await client.query("BEGIN");

    // ── Optional reset ────────────────────────────────────────────────────────
    if (RESET) {
      console.log("🗑  Resetting demo records...");
      await client.query(
        `DELETE FROM duriancare_iot.export_assessments WHERE device_id IN ($1, $2)`,
        [DEMO_PASS_DEV, DEMO_FAIL_DEV]
      );
      await client.query(
        `DELETE FROM duriancare_iot.camera_captures WHERE device_id IN ($1, $2)`,
        [DEMO_PASS_CAM, DEMO_FAIL_CAM]
      );
      await client.query(
        `DELETE FROM duriancare_iot.telemetry WHERE device_id IN ($1, $2)`,
        [DEMO_PASS_DEV, DEMO_FAIL_DEV]
      );
      await client.query(
        `DELETE FROM duriancare_iot.batch_chemical_applications ba
         USING duriancare_iot.farming_batches fb
         WHERE ba.batch_id = fb.id AND fb.id IN ($1, $2)`,
        [PASS_BATCH_ID, FAIL_BATCH_ID]
      );
      await client.query(
        `DELETE FROM duriancare_iot.farming_batches WHERE id IN ($1, $2)`,
        [PASS_BATCH_ID, FAIL_BATCH_ID]
      );
      await client.query(
        `DELETE FROM duriancare_auth.user_preferences WHERE id = $1`, [PREF_ID]
      );
      await client.query(
        `DELETE FROM duriancare_auth.user_profiles WHERE id = $1`, [PROF_ID]
      );
      await client.query(
        `DELETE FROM duriancare_auth.users WHERE id = $1`, [USER_ID]
      );
      console.log("   Done.\n");
    }

    // ── 1. Auth user (Huỳnh Lê Minh Duy) ────────────────────────────────────
    console.log("👤 Seeding auth user...");

    // Use pgcrypto to generate BCrypt hash compatible with Spring Security
    const hashRow = await client.query(
      `SELECT crypt($1, gen_salt('bf', 10)) AS hash`,
      ["Linh19012004@"]
    );
    const pwHash = hashRow.rows[0].hash;

    await client.query(
      `INSERT INTO duriancare_auth.users (id, email, password, status, role, created_at, updated_at)
       VALUES ($1, $2, $3, 'ACTIVE', 'FARMER', NOW(), NOW())
       ON CONFLICT (id) DO NOTHING`,
      [USER_ID, DEMO_EMAIL, pwHash]
    );

    await client.query(
      `INSERT INTO duriancare_auth.user_profiles
         (id, user_id, full_name, phone_number, farm_address, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, NOW(), NOW())
       ON CONFLICT (id) DO NOTHING`,
      [PROF_ID, USER_ID,
       "Huỳnh Lê Minh Duy",
       "0912345678",
       "Ấp Bình An, Xã Ngũ Hiệp, Huyện Cai Lậy, Tiền Giang"]
    );

    await client.query(
      `INSERT INTO duriancare_auth.user_preferences
         (id, user_id, language, firebase_notification_enabled, email_notification_enabled, created_at, updated_at)
       VALUES ($1, $2, 'vi', TRUE, TRUE, NOW(), NOW())
       ON CONFLICT (id) DO NOTHING`,
      [PREF_ID, USER_ID]
    );

    console.log(`   User: ${DEMO_EMAIL}  password: Linh19012004@  role: FARMER`);

    // ── 2. Farming batches ────────────────────────────────────────────────────
    console.log("\n🌾 Seeding farming batches...");

    await client.query(
      `INSERT INTO duriancare_iot.farming_batches
         (id, batch_code, device_id, cam_device_id, variety, farm_name,
          start_date, harvest_date, target_market, notes)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
       ON CONFLICT (batch_code) DO NOTHING`,
      [PASS_BATCH_ID, "BATCH-EXPORT-PASS-2026",
       DEMO_PASS_DEV, DEMO_PASS_CAM,
       "RI6 (Ri 6)", "Nông trại Sầu Riêng DurianCare",
       "2026-02-10", "2026-08-15", "CHINA",
       "Lô xuất khẩu mẫu — canh tác đúng PHI, dư lượng thấp, môi trường tối ưu"]
    );

    await client.query(
      `INSERT INTO duriancare_iot.farming_batches
         (id, batch_code, device_id, cam_device_id, variety, farm_name,
          start_date, harvest_date, target_market, notes)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
       ON CONFLICT (batch_code) DO NOTHING`,
      [FAIL_BATCH_ID, "BATCH-EXPORT-FAIL-2026",
       DEMO_FAIL_DEV, DEMO_FAIL_CAM,
       "Monthong", "Nông trại Sầu Riêng DurianCare",
       "2026-02-10", "2026-08-31", "EU",
       "Lô vi phạm — phun metalaxyl liều cao 4 ngày trước thu hoạch, vượt MRL EU 9×"]
    );

    // ── 3. Chemical applications ──────────────────────────────────────────────
    console.log("🧪 Seeding chemical applications...");

    for (const c of PASS_CHEMICALS) {
      await client.query(
        `INSERT INTO duriancare_iot.batch_chemical_applications
           (batch_id, chemical_id, applied_at, dose_kg_per_ha, stage)
         VALUES ($1, $2, $3, $4, $5)`,
        [PASS_BATCH_ID, c.chemical_id, c.applied_at, c.dose_kg_per_ha, c.stage]
      );
    }

    for (const c of FAIL_CHEMICALS) {
      await client.query(
        `INSERT INTO duriancare_iot.batch_chemical_applications
           (batch_id, chemical_id, applied_at, dose_kg_per_ha, stage)
         VALUES ($1, $2, $3, $4, $5)`,
        [FAIL_BATCH_ID, c.chemical_id, c.applied_at, c.dose_kg_per_ha, c.stage]
      );
    }

    // ── 4. Telemetry — PASS ──────────────────────────────────────────────────
    console.log("\n📡 Seeding telemetry (PASS — 30 readings)...");
    for (let i = 0; i < PASS_SENSOR.length; i++) {
      const [temp, hum, soil] = PASS_SENSOR[i];
      await client.query(
        `INSERT INTO duriancare_iot.telemetry
           (device_id, "timestamp", temperature, humidity, soil_moisture)
         VALUES ($1, $2, $3, $4, $5)`,
        [DEMO_PASS_DEV, daysAgo(i + 1), temp, hum, soil]
      );
    }

    // ── 5. Telemetry — FAIL ──────────────────────────────────────────────────
    console.log("📡 Seeding telemetry (FAIL — 30 readings)...");
    for (let i = 0; i < FAIL_SENSOR.length; i++) {
      const [temp, hum, soil] = FAIL_SENSOR[i];
      await client.query(
        `INSERT INTO duriancare_iot.telemetry
           (device_id, "timestamp", temperature, humidity, soil_moisture)
         VALUES ($1, $2, $3, $4, $5)`,
        [DEMO_FAIL_DEV, daysAgo(i + 1), temp, hum, soil]
      );
    }

    // ── 6. Camera captures — PASS ─────────────────────────────────────────────
    console.log("\n📷 Seeding camera captures (PASS — 10 captures)...");
    // Spread captures: every ~3 days over 30 days
    const passCaptureOffsets = [1, 4, 7, 10, 13, 16, 19, 22, 25, 28];
    for (let i = 0; i < PASS_CAMERA.length; i++) {
      const [disease, conf, risk] = PASS_CAMERA[i];
      await client.query(
        `INSERT INTO duriancare_iot.camera_captures
           (device_id, image_url, capture_type, captured_at, ai_status,
            disease_detected, confidence_score, diagnosis_result)
         VALUES ($1, $2, 'SCHEDULED', $3, 'COMPLETED', $4, $5, $6)`,
        [
          DEMO_PASS_CAM,
          imageUrl(DEMO_PASS_CAM, i + 1),
          daysAgo(passCaptureOffsets[i]),
          disease,
          conf,
          JSON.stringify({ riskLevel: risk, disease, confidence: conf }),
        ]
      );
    }

    // ── 7. Camera captures — FAIL ─────────────────────────────────────────────
    console.log("📷 Seeding camera captures (FAIL — 8 captures)...");
    const failCaptureOffsets = [2, 5, 8, 12, 16, 19, 23, 28];
    for (let i = 0; i < FAIL_CAMERA.length; i++) {
      const [disease, conf, risk] = FAIL_CAMERA[i];
      await client.query(
        `INSERT INTO duriancare_iot.camera_captures
           (device_id, image_url, capture_type, captured_at, ai_status,
            disease_detected, confidence_score, diagnosis_result)
         VALUES ($1, $2, 'SCHEDULED', $3, 'COMPLETED', $4, $5, $6)`,
        [
          DEMO_FAIL_CAM,
          imageUrl(DEMO_FAIL_CAM, i + 1),
          daysAgo(failCaptureOffsets[i]),
          disease,
          conf,
          JSON.stringify({ riskLevel: risk, disease, confidence: conf }),
        ]
      );
    }

    // ── 8. Compute and insert assessments ─────────────────────────────────────
    console.log("\n🔬 Computing export assessments...");

    // Build sensor rows in assessmentService format (air_humidity instead of humidity)
    const passSensorRows = PASS_SENSOR.map(([t, h, s]) => ({
      temperature: t, air_humidity: h, soil_moisture: s,
    }));
    const failSensorRows = FAIL_SENSOR.map(([t, h, s]) => ({
      temperature: t, air_humidity: h, soil_moisture: s,
    }));

    // Camera rows for assessment
    const passCamRows = PASS_CAMERA.map(([disease, conf, risk]) => ({
      disease_detected: disease,
      confidence_score: conf,
      diagnosis_result: { riskLevel: risk, disease },
    }));
    const failCamRows = FAIL_CAMERA.map(([disease, conf, risk]) => ({
      disease_detected: disease,
      confidence_score: conf,
      diagnosis_result: { riskLevel: risk, disease },
    }));

    const passResult = runAssessment({
      targetMarket:         "CHINA",
      chemicalApplications: PASS_CHEMICALS,
      harvestDate:          "2026-08-15",
      sensorRows:           passSensorRows,
      diagnosisRows:        passCamRows,
    });

    const failResult = runAssessment({
      targetMarket:         "EU",
      chemicalApplications: FAIL_CHEMICALS,
      harvestDate:          "2026-08-31",
      sensorRows:           failSensorRows,
      diagnosisRows:        failCamRows,
    });

    await client.query(
      `INSERT INTO duriancare_iot.export_assessments
         (device_id, target_market, overall_score, residue_score, env_score,
          disease_score, phi_score, soil_score, risk_summary, recommendations, harvest_date)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`,
      [
        DEMO_PASS_DEV, "CHINA",
        passResult.overall_score, passResult.residue_score, passResult.env_score,
        passResult.disease_score, passResult.phi_score, passResult.soil_score,
        JSON.stringify(passResult.risk_summary),
        JSON.stringify(passResult.recommendations),
        "2026-08-15",
      ]
    );

    await client.query(
      `INSERT INTO duriancare_iot.export_assessments
         (device_id, target_market, overall_score, residue_score, env_score,
          disease_score, phi_score, soil_score, risk_summary, recommendations, harvest_date)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)`,
      [
        DEMO_FAIL_DEV, "EU",
        failResult.overall_score, failResult.residue_score, failResult.env_score,
        failResult.disease_score, failResult.phi_score, failResult.soil_score,
        JSON.stringify(failResult.risk_summary),
        JSON.stringify(failResult.recommendations),
        "2026-08-31",
      ]
    );

    await client.query("COMMIT");

    // ─── Summary ──────────────────────────────────────────────────────────────
    console.log("\n" + "═".repeat(60));
    console.log("✅  SEED COMPLETE");
    console.log("═".repeat(60));

    console.log("\n📋 DEMO ACCOUNT");
    console.log(`   Email    : ${DEMO_EMAIL}`);
    console.log(`   Password : Linh19012004@`);
    console.log(`   Role     : FARMER`);

    console.log("\n📊 BATCH-EXPORT-PASS-2026 (CHINA market)");
    console.log(`   Sensor device  : ${DEMO_PASS_DEV}`);
    console.log(`   Camera device  : ${DEMO_PASS_CAM}`);
    console.log(`   Overall score  : ${passResult.overall_score}% ✅ PASS`);
    console.log(`   Residue score  : ${passResult.residue_score}`);
    console.log(`   Env score      : ${passResult.env_score}`);
    console.log(`   Disease score  : ${passResult.disease_score}`);
    console.log(`   PHI score      : ${passResult.phi_score}`);
    console.log(`   Soil score     : ${passResult.soil_score}`);

    console.log("\n📊 BATCH-EXPORT-FAIL-2026 (EU market)");
    console.log(`   Sensor device  : ${DEMO_FAIL_DEV}`);
    console.log(`   Camera device  : ${DEMO_FAIL_CAM}`);
    console.log(`   Overall score  : ${failResult.overall_score}% ❌ FAIL`);
    console.log(`   Residue score  : ${failResult.residue_score}  (metalaxyl ~0.45 ppm > MRL 0.05 ppm)`);
    console.log(`   Env score      : ${failResult.env_score}`);
    console.log(`   Disease score  : ${failResult.disease_score}  (1× LEAF_BLIGHT detected)`);
    console.log(`   PHI score      : ${failResult.phi_score}  (3/3 chemicals not cleared)`);
    console.log(`   Soil score     : ${failResult.soil_score}`);

    console.log("\n🚀 DEMO INSTRUCTIONS");
    console.log("   1. Start the IoT backend service (npm start)");
    console.log("   2. Open web client and log in with the demo account above");
    console.log("   3. Navigate to Dashboard → Đánh giá Xuất khẩu");
    console.log("   4. PASS demo: Device ID = esp32-demo-pass, Market = Trung Quốc");
    console.log("      Click Đánh giá → expect ~95% PASS");
    console.log("   5. FAIL demo: Device ID = esp32-demo-fail, Market = EU");
    console.log("      Click Đánh giá → expect ~38% FAIL + MRL violation alert");
    console.log("   6. Mobile: tap Đánh giá Xuất khẩu in More tab → same device IDs");
    console.log("═".repeat(60) + "\n");

  } catch (err) {
    await client.query("ROLLBACK");
    console.error("\n❌ Seed failed:", err.message);
    console.error(err);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

main();
