/**
 * TREE MAP DEMO SEED — GRADUATION DEMO 2026
 *
 * Target account: minhdii1510@gmail.com  (role: FARMER, status: ACTIVE)
 * Farmer userId : 9ffc2a41-eaa6-4567-ad6d-d6741acafeb7
 *
 * This script is IDEMPOTENT — safe to run multiple times.
 * All seeded documents use fixed IDs with the prefix "demo-".
 *
 * ⚠  DATA CLASSIFICATION:
 *   - "source": "DEMO_SEED"  → seeded/demo data, NOT real AI inference
 *   - Real live AI results will have source: "AI_SERVICE"
 *
 * Run manually:
 *   docker exec duriancare-mongodb mongosh \
 *     -u duriancare -p duriancare_dev \
 *     --authenticationDatabase admin \
 *     /docker-entrypoint-initdb.d/02_seed_tree_map_demo.js
 */

// ─── Constants ──────────────────────────────────────────────────────────────

const FARMER_USER_ID = "9ffc2a41-eaa6-4567-ad6d-d6741acafeb7";
const FARM_ID        = "demo-farm-khoa-luan-2026";
const ZONE_ID        = "demo-zone-a-vung-trong-demo";
const now            = new Date();
const dayAgo = (n) => new Date(now.getTime() - n * 86400000);

// ─── Database handles ────────────────────────────────────────────────────────

const farmDb = db.getSiblingDB("duriancare_farm");

// ─── 1. Farm + embedded Zone ─────────────────────────────────────────────────

farmDb.farms.deleteMany({ _id: FARM_ID });

farmDb.farms.insertOne({
  _id:         FARM_ID,
  ownerUserId: FARMER_USER_ID,
  name:        "Trang trại Demo - Khóa Luận 2026",
  address:     "Ấp Tân Phong, Thị trấn Cai Lậy",
  province:    "Tiền Giang",
  district:    "Cai Lậy",
  latitude:    NumberDecimal("10.4125"),
  longitude:   NumberDecimal("106.1187"),
  areaHectares: NumberDecimal("4.2"),
  status:      "ACTIVE",
  zones: [
    {
      id:              ZONE_ID,
      name:            "Vùng trồng Demo A",
      code:            "DEMO-A",
      areaSquareMeters: NumberDecimal("18000"),
      boundaryGeoJson:  {},
      description:     "Vùng trồng demo cho buổi bảo vệ khóa luận — sầu riêng Monthong 6 năm tuổi.",
      status:          "ACTIVE",
      createdAt:       dayAgo(30),
      updatedAt:       now,
    }
  ],
  createdAt: dayAgo(30),
  updatedAt: now,
});

print("✓ Farm inserted: " + FARM_ID);

// ─── 2. Trees ────────────────────────────────────────────────────────────────
//
// Visual layout on canvas (positionX, positionY in 0.0–1.0 range):
//
//   T001(0.10,0.10)   T002(0.40,0.10)   T003(0.70,0.10)
//
//   T004(0.10,0.35)   T005(0.40,0.35)   T006(0.70,0.35)
//
//        T007(0.25,0.60)   T008(0.55,0.60)   T009(0.82,0.60)
//
//             T010(0.35,0.80)   T011(0.65,0.80)
//
//                    T012(0.50,0.95)
//
// HealthStatus summary:
//   HEALTHY  : T001, T002, T004, T006, T007, T010, T012  (7 trees, diagnosed)
//   DISEASED : T003 (leaf-blight), T005 (anthracnose)      (2 trees, diagnosed)
//   SUSPECTED: T008, T009, T011                             (3 trees, undiagnosed — default)
//
// Safety aggregation result:
//   assessedTrees  = 9  (T001–T007 + T010 + T012)
//   safeTrees      = 7
//   safetyRate     = 7 / 9 * 100 = 77.78%
//   notAssessed    = 3  (T008, T009, T011)

const trees = [
  // Row 1 — top
  { id: "demo-tree-001", code: "DC-T001", nickname: "Cây Một",    variety: "Monthong",  posX: 0.10, posY: 0.10, healthStatus: "HEALTHY",   notes: "Cây chủ lực, sinh trưởng tốt." },
  { id: "demo-tree-002", code: "DC-T002", nickname: "Cây Hai",    variety: "Monthong",  posX: 0.40, posY: 0.10, healthStatus: "HEALTHY",   notes: "" },
  { id: "demo-tree-003", code: "DC-T003", nickname: "Cây Ba",     variety: "Monthong",  posX: 0.70, posY: 0.10, healthStatus: "DISEASED",  notes: "Phát hiện đốm lá — cần xử lý." },
  // Row 2
  { id: "demo-tree-004", code: "DC-T004", nickname: "Cây Bốn",   variety: "Ri6",       posX: 0.10, posY: 0.35, healthStatus: "HEALTHY",   notes: "" },
  { id: "demo-tree-005", code: "DC-T005", nickname: "Cây Năm",   variety: "Monthong",  posX: 0.40, posY: 0.35, healthStatus: "DISEASED",  notes: "Thán thư, đang xử lý bằng thuốc sinh học." },
  { id: "demo-tree-006", code: "DC-T006", nickname: "Cây Sáu",   variety: "Ri6",       posX: 0.70, posY: 0.35, healthStatus: "HEALTHY",   notes: "" },
  // Row 3
  { id: "demo-tree-007", code: "DC-T007", nickname: "Cây Bảy",   variety: "Musang King", posX: 0.25, posY: 0.60, healthStatus: "HEALTHY", notes: "Giống nhập khẩu, cần theo dõi thêm." },
  { id: "demo-tree-008", code: "DC-T008", nickname: "Cây Tám",   variety: "Monthong",  posX: 0.55, posY: 0.60, healthStatus: "SUSPECTED", notes: "Chưa chẩn đoán — ưu tiên trong tuần tới." },
  { id: "demo-tree-009", code: "DC-T009", nickname: "Cây Chín",  variety: "Ri6",       posX: 0.82, posY: 0.60, healthStatus: "SUSPECTED", notes: "" },
  // Row 4
  { id: "demo-tree-010", code: "DC-T010", nickname: "Cây Mười",  variety: "Monthong",  posX: 0.35, posY: 0.80, healthStatus: "HEALTHY",   notes: "" },
  { id: "demo-tree-011", code: "DC-T011", nickname: "Cây Mười Một", variety: "Monthong", posX: 0.65, posY: 0.80, healthStatus: "SUSPECTED", notes: "" },
  // Bottom centre — LIVE DEMO tree
  { id: "demo-tree-012", code: "DC-T012", nickname: "Cây Demo Trực Tiếp", variety: "Monthong", posX: 0.50, posY: 0.95, healthStatus: "HEALTHY", notes: "Cây dành riêng cho demo chẩn đoán trực tiếp tại buổi bảo vệ." },
];

farmDb.durian_trees.deleteMany({ farmId: FARM_ID });

trees.forEach(t => {
  farmDb.durian_trees.insertOne({
    _id:         t.id,
    farmId:      FARM_ID,
    farmZoneId:  ZONE_ID,
    speciesId:   null,
    treeCode:    t.code,
    nickname:    t.nickname,
    variety:     t.variety,
    plantedDate: "2020-03-15",
    latitude:    null,
    longitude:   null,
    positionX:   t.posX,
    positionY:   t.posY,
    healthStatus: t.healthStatus,
    status:      "ACTIVE",
    notes:       t.notes,
    createdAt:   dayAgo(30),
    updatedAt:   dayAgo(1),
  });
});

print("✓ Trees inserted: " + trees.length);

// ─── 3. Seeded diagnosis records ─────────────────────────────────────────────
//
// ⚠  THESE ARE SEEDED/DEMO RECORDS — NOT REAL AI INFERENCE RESULTS.
//    source: "DEMO_SEED" distinguishes them from live AI results (source: "AI_SERVICE").
//
// Diagnosed trees: T001–T007, T010, T012 (T008, T009, T011 left undiagnosed)

farmDb.tree_diagnosis_records.deleteMany({ farmId: FARM_ID });

const diagnosisSeed = [
  // HEALTHY trees
  { id: "demo-diag-001", treeId: "demo-tree-001", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.94, status: "HEALTHY",  daysAgo: 5  },
  { id: "demo-diag-002", treeId: "demo-tree-002", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.91, status: "HEALTHY",  daysAgo: 7  },
  { id: "demo-diag-004", treeId: "demo-tree-004", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.89, status: "HEALTHY",  daysAgo: 8  },
  { id: "demo-diag-006", treeId: "demo-tree-006", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.96, status: "HEALTHY",  daysAgo: 4  },
  { id: "demo-diag-007", treeId: "demo-tree-007", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.88, status: "HEALTHY",  daysAgo: 10 },
  { id: "demo-diag-010", treeId: "demo-tree-010", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.93, status: "HEALTHY",  daysAgo: 6  },
  { id: "demo-diag-012", treeId: "demo-tree-012", code: "HEALTHY_LEAF", name: "Lá khỏe mạnh",  conf: 0.92, status: "HEALTHY",  daysAgo: 3  },
  // DISEASED trees
  { id: "demo-diag-003", treeId: "demo-tree-003", code: "leaf-blight",  name: "Cháy lá (Leaf Blight)", conf: 0.82, status: "DISEASED", daysAgo: 2 },
  { id: "demo-diag-005", treeId: "demo-tree-005", code: "anthracnose",  name: "Thán thư (Anthracnose)", conf: 0.79, status: "DISEASED", daysAgo: 12 },
];

diagnosisSeed.forEach(d => {
  farmDb.tree_diagnosis_records.insertOne({
    _id:                 d.id,
    treeId:              d.treeId,
    farmId:              FARM_ID,
    farmZoneId:          ZONE_ID,
    imageUrl:            "https://demo.placeholder.local/demo-leaf-" + d.treeId + ".jpg",
    diseaseCode:         d.code,
    diseaseName:         d.name,
    confidence:          d.conf,
    boundingBox:         null,
    source:              "DEMO_SEED",
    diagnosedByUserId:   FARMER_USER_ID,
    impliedHealthStatus: d.status,
    diagnosedAt:         dayAgo(d.daysAgo),
    createdAt:           dayAgo(d.daysAgo),
  });
});

print("✓ Seeded diagnosis records inserted: " + diagnosisSeed.length);

// ─── 4. Verification summary ─────────────────────────────────────────────────

const farmCount  = farmDb.farms.countDocuments({ ownerUserId: FARMER_USER_ID });
const treeCount  = farmDb.durian_trees.countDocuments({ farmId: FARM_ID });
const diagCount  = farmDb.tree_diagnosis_records.countDocuments({ farmId: FARM_ID });
const healthy    = farmDb.durian_trees.countDocuments({ farmId: FARM_ID, healthStatus: "HEALTHY" });
const diseased   = farmDb.durian_trees.countDocuments({ farmId: FARM_ID, healthStatus: "DISEASED" });
const suspected  = farmDb.durian_trees.countDocuments({ farmId: FARM_ID, healthStatus: "SUSPECTED" });

print("\n═══════════════════════════════════════════");
print("  TREE MAP DEMO SEED — VERIFICATION");
print("═══════════════════════════════════════════");
print("  Farmer userId : " + FARMER_USER_ID);
print("  Farms owned   : " + farmCount);
print("  Farm ID       : " + FARM_ID);
print("  Zone ID       : " + ZONE_ID);
print("  Total trees   : " + treeCount);
print("  → HEALTHY     : " + healthy + " (with seeded diagnosis)");
print("  → DISEASED    : " + diseased + " (with seeded diagnosis)");
print("  → SUSPECTED   : " + suspected + " (undiagnosed — no records)");
print("  Diagnosis recs: " + diagCount + " (source: DEMO_SEED)");
print("  AssessedTrees : 9  (trees with diagnosisCount > 0)");
print("  SafetyRate    : 77.78%  (7 healthy / 9 assessed * 100)");
print("  DC-T012       : Reserved for LIVE AI DIAGNOSIS during defense");
print("═══════════════════════════════════════════\n");
