const { test } = require("node:test");
const assert = require("node:assert/strict");
const { auditCareReferences } = require("./audit-care-plan-references");
const plan = { _id: "plan", treeId: "tree", farmId: "farm", diagnosisId: "diagnosis", status: "PLANNED" };
const tree = { _id: "tree", farmId: "farm", farmZoneId: "zone" };
const diagnosis = { _id: "diagnosis", treeId: "tree", farmId: "farm", farmZoneId: "zone" };
test("missing historical diagnosis never invents a repair or changes status", () => {
  const before = JSON.stringify(plan);
  const result = auditCareReferences([plan], [], [tree])[0];
  assert.deepEqual(result.issues, ["MISSING_DIAGNOSIS"]);
  assert.equal(result.action, "NONE_READ_ONLY"); assert.equal(JSON.stringify(plan), before);
});
test("matching references pass without mutation", () => assert.deepEqual(auditCareReferences([plan], [diagnosis], [tree])[0].issues, []));
test("wrong tree/farm/zone cannot be considered reconciled", () => {
  assert.deepEqual(auditCareReferences([plan], [{ ...diagnosis, farmZoneId: "foreign" }], [tree])[0].issues, ["DIAGNOSIS_SCOPE_MISMATCH"]);
  assert.deepEqual(auditCareReferences([plan], [diagnosis], [])[0].issues, ["TREE_SCOPE_MISMATCH"]);
});
