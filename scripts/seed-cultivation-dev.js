// NON-DESTRUCTIVE cultivation seed for thesis demo.
// Inserts ONE cultivation plan and ONE harvest batch for the existing demo farm/zone.
// Safe to run multiple times — uses insertOne with _id that won't collide if already exists.
// Does NOT delete or modify any existing documents.
//
// Run: mongosh "mongodb://duriancare:duriancare_dev@localhost:27018/duriancare_cultivation?authSource=admin" seed-cultivation-dev.js

const FARM_ID   = "demo-farm-khoa-luan-2026";
const ZONE_ID   = "demo-zone-a-vung-trong-demo";
const USER_ID   = "9ffc2a41-eaa6-4567-ad6d-d6741acafeb7"; // farmer userId from existing diagnosis records
const SEASON_ID = "demo-season-2026-vụ1";                  // logical reference key; no separate collection

const now   = new Date();
const today = now.toISOString().slice(0, 10);

// ── 1. Cultivation Plan ───────────────────────────────────────────────────────
const planId = "demo-plan-vụ1-2026";
const planExists = db.cultivation_plans.countDocuments({ _id: planId }) > 0;
if (!planExists) {
  db.cultivation_plans.insertOne({
    _id:                   planId,
    farmId:                FARM_ID,
    plotId:                ZONE_ID,
    cultivationSeasonId:   SEASON_ID,
    templateId:            null,
    name:                  "Kế hoạch vụ 1 năm 2026",
    startDate:             "2026-03-01",
    expectedHarvestDate:   "2026-09-15",
    targetMarketCodes:     ["VN", "TH", "CN"],
    status:                "ACTIVE",
    createdBy:             USER_ID,
    createdAt:             now,
    updatedAt:             now,
  });
  print("✅ cultivation_plans: inserted demo-plan-vụ1-2026");
} else {
  print("⏭  cultivation_plans: demo-plan-vụ1-2026 already exists, skipped");
}

// ── 2. Harvest Batch ─────────────────────────────────────────────────────────
const batchId = "demo-harvest-batch-2026-001";
const batchExists = db.harvest_batches.countDocuments({ _id: batchId }) > 0;
if (!batchExists) {
  db.harvest_batches.insertOne({
    _id:                     batchId,
    batchCode:               "VN-DRC-2026-001",
    cultivationSeasonId:     SEASON_ID,
    farmId:                  FARM_ID,
    plotId:                  ZONE_ID,
    harvestedAt:             new Date("2026-09-15T06:00:00Z"),
    quantity:                NumberDecimal("1200.00"),
    quantityUnit:            "KG",
    expectedDestinationMarket: "Thị trường Trung Quốc (Guangzhou)",
    latestSafeHarvestDate:   "2026-09-20",
    chemicalRiskLevel:       "LOW",
    status:                  "PENDING_INSPECTION",
    createdBy:               USER_ID,
    createdAt:               now,
  });
  print("✅ harvest_batches: inserted demo-harvest-batch-2026-001");
} else {
  print("⏭  harvest_batches: demo-harvest-batch-2026-001 already exists, skipped");
}

print("\nDone. cultivation_plans: " + db.cultivation_plans.countDocuments() + " docs");
print("harvest_batches: " + db.harvest_batches.countDocuments() + " docs");
