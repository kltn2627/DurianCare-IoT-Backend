/* Read-only mongosh dry-run. Never choose replacement diagnoses or alter history. */
function auditCareReferences(plans, diagnoses, trees) {
  const byDiagnosis = new Map(diagnoses.map(d => [String(d._id), d]));
  const byTree = new Map(trees.map(t => [String(t._id), t]));
  return plans.map(plan => {
    const tree = byTree.get(String(plan.treeId));
    const diagnosis = byDiagnosis.get(String(plan.diagnosisId));
    const issues = [];
    if (!tree || tree.farmId !== plan.farmId) issues.push("TREE_SCOPE_MISMATCH");
    if (plan.diagnosisId && !diagnosis) issues.push("MISSING_DIAGNOSIS");
    if (diagnosis && (diagnosis.treeId !== plan.treeId || diagnosis.farmId !== plan.farmId ||
        (tree && diagnosis.farmZoneId !== tree.farmZoneId))) issues.push("DIAGNOSIS_SCOPE_MISMATCH");
    return { planId: String(plan._id), treeId: plan.treeId, diagnosisId: plan.diagnosisId,
      status: plan.status, issues, action: "NONE_READ_ONLY" };
  });
}
if (typeof module !== "undefined") module.exports = { auditCareReferences };
if (typeof db !== "undefined") {
  const farmId = process.env.DURIANCARE_AUDIT_FARM_ID, ownerId = process.env.DURIANCARE_AUDIT_OWNER_ID;
  if (!farmId || !ownerId) throw new Error("AUDIT_FARM_AND_OWNER_REQUIRED");
  const farm = db.getSiblingDB("duriancare_farm");
  if (!farm.farms.findOne({ _id: farmId, ownerUserId: ownerId })) throw new Error("AUDIT_OWNER_SCOPE_MISMATCH");
  const plans = farm.tree_care_plans.find({ farmId }).toArray();
  const diagnosisIds = [...new Set(plans.map(p => p.diagnosisId).filter(Boolean))];
  const rows = auditCareReferences(plans, farm.tree_diagnosis_records.find({ _id: { $in: diagnosisIds } }).toArray(),
    farm.durian_trees.find({ farmId }).toArray());
  print(JSON.stringify({ farmId, planCount: rows.length, rows, writesPerformed: 0 }));
}
