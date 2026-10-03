"use strict";

const { CHEMICALS, MRL } = require("./assessmentData");

/**
 * Estimate residue concentration (ppm) using first-order decay:
 *   C(t) = dose_kg_ha × application_factor × e^(-λt)
 * where λ = ln(2) / half_life
 */
function estimateResidue(doseKgPerHa, halfLifeDays, daysSinceApplication) {
  if (daysSinceApplication < 0) return 0;
  const APPLICATION_FACTOR = 0.01;  // ~1% of field dose reaches fruit tissue
  const lambda = Math.LN2 / halfLifeDays;
  return doseKgPerHa * APPLICATION_FACTOR * Math.exp(-lambda * daysSinceApplication);
}

/**
 * Score chemical residue compliance for all applied chemicals.
 * Returns: { score [0-100], risks: [], daysUntilHarvest }
 */
function scoreChemicalResidue(applications, targetMarket, harvestDate) {
  const marketMrl = MRL[targetMarket] || MRL.DOMESTIC;
  const today     = new Date();
  const harvest   = harvestDate ? new Date(harvestDate) : new Date(today.getTime() + 30 * 86400_000);
  const daysUntilHarvest = Math.max(0, (harvest - today) / 86400_000);

  if (!applications || applications.length === 0) {
    return { score: 95, risks: [], daysUntilHarvest: Math.round(daysUntilHarvest) };
  }

  const risks = [];
  let worstRatio = 0;  // max(estimatedResidue / MRL) across all chemicals

  for (const app of applications) {
    const chem = CHEMICALS[app.chemical_id];
    if (!chem) continue;

    const appliedAt         = new Date(app.applied_at);
    const daysSinceApp      = Math.max(0, (today - appliedAt) / 86400_000);
    const phiRemaining      = Math.max(0, chem.phiDays - daysSinceApp);
    const mrl               = marketMrl[app.chemical_id];
    const estimatedResidue  = estimateResidue(app.dose_kg_per_ha || 0.5, chem.halfLifeDays, daysSinceApp);
    const isBanned          = mrl === null;
    const exceedsMrl        = !isBanned && estimatedResidue > mrl;
    const ratio             = isBanned ? 999 : (mrl > 0 ? estimatedResidue / mrl : 0);

    if (ratio > worstRatio) worstRatio = ratio;

    if (isBanned || exceedsMrl || phiRemaining > 0) {
      let severity, message;
      if (isBanned) {
        severity = "CRITICAL";
        message  = `${chem.name} bị CẤM tại thị trường này. Không được xuất khẩu.`;
      } else if (exceedsMrl) {
        severity = "HIGH";
        message  = `Ước tính tồn dư ${estimatedResidue.toFixed(3)} ppm > MRL ${mrl} ppm.`;
      } else if (phiRemaining > 0) {
        severity = "MEDIUM";
        message  = `Cần thêm ${Math.ceil(phiRemaining)} ngày nữa để đủ thời gian cách ly (PHI ${chem.phiDays} ngày).`;
      }
      risks.push({
        chemical_id:       app.chemical_id,
        name:              chem.name,
        applied_at:        app.applied_at,
        days_since_app:    Math.round(daysSinceApp),
        phi_days:          chem.phiDays,
        phi_remaining:     Math.ceil(phiRemaining),
        estimated_ppm:     Number(estimatedResidue.toFixed(4)),
        mrl_ppm:           mrl,
        severity,
        message,
      });
    }
  }

  // Score: 100 − penalty. Banned = 0. Ratio > 1 = proportional deduction.
  let score;
  if (worstRatio >= 999) {
    score = 0;
  } else if (worstRatio > 1) {
    score = Math.max(0, 100 - (worstRatio - 1) * 80);
  } else {
    score = Math.max(55, 100 - worstRatio * 30);
  }

  return { score: Math.round(score), risks, daysUntilHarvest: Math.round(daysUntilHarvest) };
}

/**
 * Score environmental stability from sensor history.
 * Optimal: temperature 28–32°C, humidity 70–85%.
 */
function scoreEnvironment(sensorRows) {
  if (!sensorRows || sensorRows.length === 0) return 70;

  let tempOk = 0, humOk = 0;
  for (const r of sensorRows) {
    const t = Number(r.temperature);
    const h = Number(r.air_humidity);
    if (!Number.isNaN(t) && t >= 28 && t <= 32) tempOk++;
    if (!Number.isNaN(h) && h >= 70 && h <= 85) humOk++;
  }

  const total = sensorRows.length;
  const tempPct = tempOk / total;
  const humPct  = humOk  / total;
  return Math.round((tempPct * 0.5 + humPct * 0.5) * 100);
}

/**
 * Score disease control from camera AI diagnoses.
 * Penalizes detected diseases; HEALTHY_LEAF is positive signal.
 */
function scoreDiseaseControl(diagnosisRows) {
  if (!diagnosisRows || diagnosisRows.length === 0) return 80;

  const SEVERITY_MAP = {
    LOW:      10,
    MEDIUM:   25,
    HIGH:     45,
    CRITICAL: 70,
  };

  let penalty = 0;
  for (const d of diagnosisRows) {
    if (d.disease_detected === "HEALTHY_LEAF" || !d.disease_detected) continue;
    const riskLevel = d.diagnosis_result?.riskLevel || "MEDIUM";
    penalty += SEVERITY_MAP[riskLevel] || 25;
  }

  return Math.max(10, Math.min(100, 100 - Math.min(penalty, 90)));
}

/**
 * Score soil quality from sensor history.
 * Optimal soil moisture: 70–85%.
 */
function scoreSoilQuality(sensorRows) {
  if (!sensorRows || sensorRows.length === 0) return 70;

  let ok = 0;
  for (const r of sensorRows) {
    const s = Number(r.soil_moisture);
    if (!Number.isNaN(s) && s >= 70 && s <= 85) ok++;
  }
  return Math.round((ok / sensorRows.length) * 100);
}

/**
 * Build human-readable recommendations based on risks and scores.
 */
function buildRecommendations({ risks, envScore, diseaseScore, soilScore, daysUntilHarvest }) {
  const recs = [];

  for (const r of risks) {
    if (r.severity === "CRITICAL") {
      recs.push({ priority: "CRITICAL", text: `Loại bỏ ${r.name} khỏi lịch canh tác — bị cấm tại thị trường mục tiêu.` });
    } else if (r.severity === "HIGH") {
      recs.push({ priority: "HIGH", text: `Ngưng phun ${r.name}. Dự kiến tồn dư vượt MRL — cần ${Math.max(0, r.phi_remaining)} ngày thêm để đảm bảo an toàn.` });
    } else if (r.severity === "MEDIUM" && r.phi_remaining > 0) {
      recs.push({ priority: "MEDIUM", text: `${r.name}: chờ thêm ${r.phi_remaining} ngày cách ly trước khi cắt trái.` });
    }
  }

  if (envScore < 60) recs.push({ priority: "MEDIUM", text: "Nhiệt độ hoặc độ ẩm không khí thường xuyên nằm ngoài khoảng tối ưu (28–32°C, 70–85%). Kiểm tra hệ thống tưới và lưới che." });
  if (diseaseScore < 60) recs.push({ priority: "HIGH",   text: "Phát hiện nhiều bệnh qua camera AI. Cần xử lý đặc hiệu và tái kiểm tra sau 7 ngày." });
  if (soilScore < 60)    recs.push({ priority: "MEDIUM", text: "Độ ẩm đất thường xuyên nằm ngoài khoảng tối ưu (70–85%). Điều chỉnh lịch tưới." });

  if (daysUntilHarvest > 0 && daysUntilHarvest <= 7) {
    recs.push({ priority: "HIGH", text: `Còn ${daysUntilHarvest} ngày đến thu hoạch — xác nhận tất cả thời gian cách ly đã đủ.` });
  }

  if (recs.length === 0) {
    recs.push({ priority: "LOW", text: "Lô sầu riêng đang đáp ứng tốt các tiêu chuẩn xuất khẩu. Duy trì lịch canh tác hiện tại." });
  }

  return recs;
}

/**
 * Full assessment pipeline.
 * @param {object} params
 * @param {string} params.targetMarket
 * @param {Array}  params.chemicalApplications
 * @param {string} params.harvestDate ISO date
 * @param {Array}  params.sensorRows  rows from telemetry table (last 30d)
 * @param {Array}  params.diagnosisRows rows from camera_captures (last 30d)
 * @returns Assessment result object
 */
function runAssessment({ targetMarket, chemicalApplications, harvestDate, sensorRows, diagnosisRows }) {
  const WEIGHTS = { residue: 0.30, env: 0.20, disease: 0.25, phi: 0.10, soil: 0.15 };

  const { score: residueScore, risks, daysUntilHarvest } =
    scoreChemicalResidue(chemicalApplications, targetMarket, harvestDate);

  // PHI score: % of applied chemicals that have completed their PHI
  let phiScore = 100;
  if (chemicalApplications && chemicalApplications.length > 0) {
    const today = new Date();
    let compliant = 0;
    for (const app of chemicalApplications) {
      const chem = CHEMICALS[app.chemical_id];
      if (!chem) { compliant++; continue; }
      const daysSince = (today - new Date(app.applied_at)) / 86400_000;
      if (daysSince >= chem.phiDays) compliant++;
    }
    phiScore = Math.round((compliant / chemicalApplications.length) * 100);
  }

  const envScore     = scoreEnvironment(sensorRows);
  const diseaseScore = scoreDiseaseControl(diagnosisRows);
  const soilScore    = scoreSoilQuality(sensorRows);

  const overallScore = Math.round(
    residueScore * WEIGHTS.residue +
    envScore     * WEIGHTS.env     +
    diseaseScore * WEIGHTS.disease +
    phiScore     * WEIGHTS.phi     +
    soilScore    * WEIGHTS.soil
  );

  const recommendations = buildRecommendations({ risks, envScore, diseaseScore, soilScore, daysUntilHarvest });

  return {
    overall_score:   overallScore,
    residue_score:   residueScore,
    env_score:       envScore,
    disease_score:   diseaseScore,
    phi_score:       phiScore,
    soil_score:      soilScore,
    days_until_harvest: daysUntilHarvest,
    risk_summary:    risks,
    recommendations,
    criteria: [
      { key: "residue",  label: "Dư lượng BVTV",       score: residueScore, weight: 30 },
      { key: "disease",  label: "Kiểm soát sâu bệnh",   score: diseaseScore, weight: 25 },
      { key: "env",      label: "Môi trường",            score: envScore,     weight: 20 },
      { key: "soil",     label: "Chất lượng đất",        score: soilScore,    weight: 15 },
      { key: "phi",      label: "Tuân thủ cách ly",      score: phiScore,     weight: 10 },
    ],
  };
}

module.exports = { runAssessment };
