const { test } = require("node:test");
const assert = require("node:assert/strict");
const { auditFarmerGarden } = require("./audit-farmer-garden");
const farms = [{ _id: "f", ownerUserId: "u", zones: [{ id: "z" }] }];
const trees = [{ _id: "t", farmId: "f", farmZoneId: "z", treeCode: "T1", status: "ACTIVE", positionX: 0.5, positionY: 0.5 }];
const seasons = [{ _id: "s", farmId: "f", plotId: "z", startDate: "2026-01-01", endDate: "2026-12-31" }];
test("canonical graph is repeatably read-only", () => {
  const before = JSON.stringify({ farms, trees, seasons });
  const first = auditFarmerGarden("u", farms, trees, seasons);
  assert.deepEqual(first, auditFarmerGarden("u", farms, trees, seasons));
  assert.deepEqual(first.issues, []);
  assert.equal(first.zones[0].activeTreeCount, 1);
  assert.equal(first.writesPerformed, 0);
  assert.equal(JSON.stringify({ farms, trees, seasons }), before);
});
test("rejects cross-owner input", () => assert.throws(() => auditFarmerGarden("other", farms, trees, seasons), /OWNER_SCOPE/));
test("flags orphan trees, invalid positions and season scopes", () => {
  const report = auditFarmerGarden("u", farms, [{ ...trees[0], farmZoneId: "other", positionX: null }], [{ ...seasons[0], plotId: "other" }]);
  assert.deepEqual(report.issues.map(i => i.code), ["TREE_SCOPE_MISMATCH", "INVALID_NORMALIZED_POSITION", "SEASON_SCOPE_MISMATCH"]);
});
test("detects duplicate codes and positions only within their zone", () => {
  const report = auditFarmerGarden("u", farms, [...trees, { ...trees[0], _id: "t2" }], seasons);
  assert.deepEqual(report.issues.map(i => i.code), ["DUPLICATE_TREE_CODE", "DUPLICATE_ACTIVE_POSITION"]);
});
