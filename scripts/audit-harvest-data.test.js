const { test } = require("node:test");
const assert = require("node:assert/strict");
const { build_harvest_report } = require("./audit-harvest-data");
const season = { _id: "s", farmId: "f", plotId: "p", startDate: "2025-01-01", endDate: "2025-12-31" };
const batch = { _id: "b", cultivationSeasonId: "s", farmId: "f", plotId: "p", status: "HARVESTED",
  quantity: "1850", quantityUnit: "KG", harvestedAt: "2025-10-15T07:00:00Z" };
const now = new Date("2026-10-14T00:00:00Z");
test("aggregates valid persisted harvest without inventing grading or shipments", () => {
  const report = build_harvest_report([season], [batch], [], now);
  assert.deepEqual(report.recordedHarvestByUnit, { KG: 1850 });
  assert.deepEqual(report.recordedHarvestByMonth, { "2025-10": { KG: 1850 } });
  assert.equal(report.actualShippedQuantity, null);
  assert.equal(report.qualityPassRate, null);
  assert.equal(report.completedHarvestSeasons, null);
});
test("excludes orphan, scope mismatch, draft, cancelled and unknown status from totals", () => {
  for (const change of [{ cultivationSeasonId: "missing" }, { farmId: "other" }, { status: "DRAFT" },
    { status: "CANCELLED" }, { status: "PENDING_INSPECTION" }]) {
    assert.deepEqual(build_harvest_report([season], [{ ...batch, ...change }], [], now).recordedHarvestByUnit, {});
  }
});
test("rejects invalid weights, dates and unsafe harvest dates", () => {
  for (const change of [{ quantity: "-1" }, { quantity: null }, { quantity: "NaN" },
    { harvestedAt: "invalid" }, { harvestedAt: "2027-01-01" }, { latestSafeHarvestDate: "2025-10-20" }]) {
    const report = build_harvest_report([season], [{ ...batch, ...change }], [], now);
    assert.deepEqual(report.recordedHarvestByUnit, {});
    assert.ok(report.issues.length > 0);
  }
});
test("keeps units separate and does not count approval as shipment", () => {
  const report = build_harvest_report([season], [batch, { ...batch, _id: "c", quantity: "2", quantityUnit: "TONNE" }],
    [{ _id: "r", harvestBatchId: "b", status: "APPROVED" }], now);
  assert.deepEqual(report.recordedHarvestByUnit, { KG: 1850, TONNE: 2 });
  assert.equal(report.counts.validReleasedLotsRecorded, 0);
});
test("flags orphan, duplicate and premature release evidence without doubling lot count", () => {
  const release = { _id: "r", harvestBatchId: "b", status: "RELEASED", releasedAt: "2025-10-16" };
  const report = build_harvest_report([season], [batch], [release, { ...release, _id: "r2" },
    { ...release, _id: "r3", releasedAt: "2025-10-14" }, { ...release, _id: "r4", harvestBatchId: "missing" }], now);
  assert.equal(report.counts.validReleasedLotsRecorded, 1);
  assert.ok(report.issues.some(i => i.code === "DUPLICATE_RELEASED_LOT"));
  assert.ok(report.issues.some(i => i.code === "ORPHAN_EXPORT_RELEASE"));
  assert.ok(report.issues.some(i => i.code === "INVALID_RELEASE_EVIDENCE"));
});
test("is repeatable and does not mutate source documents", () => {
  const input = JSON.stringify([[season], [batch], []]);
  const a = build_harvest_report([season], [batch], [], now);
  assert.deepEqual(a, build_harvest_report([season], [batch], [], now));
  assert.equal(JSON.stringify([[season], [batch], []]), input);
});
