/* Read-only mongosh audit. Resolve the Farmer UUID from Auth first; never seed by email guesswork. */
function auditFarmerGarden(ownerId, farms, trees, seasons) {
  if (!ownerId || !farms.every(f => f.ownerUserId === ownerId)) throw new Error("OWNER_SCOPE_MISMATCH");
  const farmIds = new Set(farms.map(f => String(f._id)));
  const zones = farms.flatMap(f => (f.zones || []).map(z => ({ ...z, farmId: String(f._id) })));
  const issues = [];
  const seenCodes = new Set();
  const seenPositions = new Set();
  for (const tree of trees) {
    const zone = zones.find(z => z.id === tree.farmZoneId && z.farmId === tree.farmId);
    if (!zone) issues.push({ id: String(tree._id), code: "TREE_SCOPE_MISMATCH" });
    const code = `${tree.farmZoneId}:${tree.treeCode}`;
    if (seenCodes.has(code)) issues.push({ id: String(tree._id), code: "DUPLICATE_TREE_CODE" });
    seenCodes.add(code);
    if (tree.status === "ACTIVE") {
      const x = tree.positionX, y = tree.positionY;
      if (typeof x !== "number" || typeof y !== "number" || !Number.isFinite(x) || !Number.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
        issues.push({ id: String(tree._id), code: "INVALID_NORMALIZED_POSITION" });
      } else {
        const position = `${tree.farmZoneId}:${x}:${y}`;
        if (seenPositions.has(position)) issues.push({ id: String(tree._id), code: "DUPLICATE_ACTIVE_POSITION" });
        seenPositions.add(position);
      }
    }
  }
  for (const season of seasons) {
    if (!farmIds.has(season.farmId) || !zones.some(z => z.id === season.plotId && z.farmId === season.farmId)) {
      issues.push({ id: String(season._id), code: "SEASON_SCOPE_MISMATCH" });
    }
    if (!season.startDate || !season.endDate || String(season.startDate) > String(season.endDate)) {
      issues.push({ id: String(season._id), code: "INVALID_SEASON_DATES" });
    }
  }
  return { ownerId, farmCount: farms.length, treeCount: trees.length, seasonCount: seasons.length,
    zones: zones.map(zone => ({ id: zone.id, farmId: zone.farmId,
      activeTreeCount: trees.filter(t => t.farmId === zone.farmId && t.farmZoneId === zone.id && t.status === "ACTIVE").length,
      hasBoundary: !!zone.boundaryGeoJson && Object.keys(zone.boundaryGeoJson).length > 0 })),
    issues, writesPerformed: 0 };
}

if (typeof module !== "undefined") module.exports = { auditFarmerGarden };
if (typeof db !== "undefined") {
  const ownerId = process.env.DURIANCARE_AUDIT_OWNER_ID;
  if (!ownerId) throw new Error("DURIANCARE_AUDIT_OWNER_ID_REQUIRED");
  const farm = db.getSiblingDB("duriancare_farm");
  const farms = farm.farms.find({ ownerUserId: ownerId }).toArray();
  const farmIds = farms.map(f => f._id);
  const trees = farm.durian_trees.find({ farmId: { $in: farmIds } }).toArray();
  const seasons = db.getSiblingDB("duriancare_cultivation").cultivation_seasons.find({ farmId: { $in: farmIds } }).toArray();
  print(JSON.stringify(auditFarmerGarden(ownerId, farms, trees, seasons)));
}
