/* Read-only mongosh report. No inserts, updates, deletes, or schema changes. */
function build_harvest_report(seasons, batches, releases, asOf = new Date()) {
  const issues = [];
  const bySeason = new Map(seasons.map(s => [String(s._id), s]));
  const byBatch = new Map(batches.map(b => [String(b._id), b]));
  const states = new Set(["DRAFT", "BLOCKED", "APPROVED", "HARVESTED", "CANCELLED"]);
  const totals = {};
  const months = {};
  const round = n => Math.round(n * 1000000) / 1000000;
  const add = (target, unit, value) => { target[unit] = round((target[unit] || 0) + value); };
  const validDate = value => value != null && Number.isFinite(new Date(value).getTime());
  const rows = batches.map(b => {
    const id = String(b._id);
    const season = bySeason.get(String(b.cultivationSeasonId));
    const problems = [];
    if (!season) problems.push("MISSING_SEASON");
    else if (b.farmId !== season.farmId || b.plotId !== season.plotId) problems.push("SEASON_SCOPE_MISMATCH");
    if (!states.has(b.status)) problems.push("UNKNOWN_HARVEST_STATUS");
    const quantity = b.quantity == null ? NaN : Number(b.quantity.toString());
    if (!Number.isFinite(quantity) || quantity < 0 || !b.quantityUnit) problems.push("INVALID_QUANTITY_OR_UNIT");
    if (!validDate(b.harvestedAt) || new Date(b.harvestedAt) > asOf) problems.push("INVALID_HARVEST_DATE");
    if (season && validDate(b.harvestedAt)) {
      const date = new Date(b.harvestedAt).toISOString().slice(0, 10);
      if ((season.startDate && date < String(season.startDate).slice(0, 10)) ||
          (season.endDate && date > String(season.endDate).slice(0, 10))) problems.push("HARVEST_OUTSIDE_SEASON");
    }
    if (validDate(b.harvestedAt) && b.latestSafeHarvestDate &&
        new Date(b.harvestedAt).toISOString().slice(0, 10) < String(b.latestSafeHarvestDate).slice(0, 10)) {
      problems.push("HARVEST_BEFORE_SAFE_DATE");
    }
    const included = problems.length === 0 && b.status === "HARVESTED";
    if (included) {
      add(totals, b.quantityUnit, quantity);
      const month = new Date(b.harvestedAt).toISOString().slice(0, 7);
      months[month] ||= {};
      add(months[month], b.quantityUnit, quantity);
    }
    for (const code of problems) issues.push({ resourceId: id, code });
    return { id, seasonId: b.cultivationSeasonId, farmId: b.farmId, plotId: b.plotId,
      status: b.status, quantity: Number.isFinite(quantity) ? String(b.quantity) : null,
      unit: b.quantityUnit, includedInRecordedHarvest: included,
      source: id.startsWith("demo-") ? "SEED_IDENTIFIER_REQUIRES_SOURCE_REVIEW" : "PERSISTED_ORIGIN_UNVERIFIED", issues: problems };
  });
  const releasedLots = new Set();
  for (const r of releases) {
    const batch = byBatch.get(String(r.harvestBatchId));
    if (!batch) issues.push({ resourceId: String(r._id), code: "ORPHAN_EXPORT_RELEASE" });
    if (r.status === "RELEASED") {
      if (!batch || !validDate(r.releasedAt) || !validDate(batch.harvestedAt) ||
          new Date(r.releasedAt) < new Date(batch.harvestedAt) || new Date(r.releasedAt) > asOf) {
        issues.push({ resourceId: String(r._id), code: "INVALID_RELEASE_EVIDENCE" });
      } else {
        if (releasedLots.has(String(batch._id))) issues.push({ resourceId: String(r._id), code: "DUPLICATE_RELEASED_LOT" });
        releasedLots.add(String(batch._id));
      }
    }
  }
  return {
    counts: { seasons: seasons.length, harvestBatches: batches.length, exportReleases: releases.length,
      validReleasedLotsRecorded: releasedLots.size },
    recordedHarvestByUnit: totals, recordedHarvestByMonth: months,
    // The current domain has no measured grading, packing or shipment quantities.
    confirmedOperationalHarvest: null, completedHarvestSeasons: null,
    qualifiedQuantity: null, rejectedQuantity: null, packedQuantity: null,
    actualShippedQuantity: null, qualityPassRate: null,
    batches: rows, issues,
    limitations: ["Persisted data alone does not establish operational provenance.",
      "Season endDate is a calendar boundary, not harvest-completion evidence.",
      "APPROVED/RELEASED dossier states do not establish physical shipment or shipped weight.",
      "No cross-unit conversion, IoT association, yield estimate, or pesticide assumption is applied."]
  };
}

if (typeof module !== "undefined") module.exports = { build_harvest_report };
if (typeof db !== "undefined") {
  const source = db.getSiblingDB("duriancare_cultivation");
  const seasons = source.cultivation_seasons.find({}, { _id: 1, farmId: 1, plotId: 1, startDate: 1, endDate: 1 }).toArray();
  const batches = source.harvest_batches.find({}, { _id: 1, cultivationSeasonId: 1, farmId: 1, plotId: 1,
    status: 1, quantity: 1, quantityUnit: 1, harvestedAt: 1, latestSafeHarvestDate: 1 }).toArray();
  const releases = source.export_releases.find({}, { _id: 1, harvestBatchId: 1, status: 1, releasedAt: 1 }).toArray();
  print(JSON.stringify(build_harvest_report(seasons, batches, releases), null, 2));
}
