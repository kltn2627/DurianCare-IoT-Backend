// Mongosh migration. DRY_RUN is read-only; APPLY adds only the source-proven
// Minh Phat Zone A season and separate audit markers for unresolved records.
(function () {
  const mode = process.env.MIGRATION_MODE || "DRY_RUN";
  if (!["DRY_RUN", "APPLY"].includes(mode)) throw new Error("MIGRATION_MODE must be DRY_RUN or APPLY");
  if (mode === "APPLY" && process.env.BACKUP_CONFIRMED !== "true") {
    throw new Error("APPLY requires a verified BSON backup (BACKUP_CONFIRMED=true)");
  }
  const farmDb = db.getSiblingDB("duriancare_farm");
  const cultivationDb = db.getSiblingDB("duriancare_cultivation");
  const id = (value) => value == null ? null : String(value);
  const farms = farmDb.farms.find().toArray();
  const seasons = cultivationDb.cultivation_seasons.find().toArray();
  const plans = cultivationDb.cultivation_plans.find().toArray();
  const activities = cultivationDb.cultivation_activities.find().toArray();
  const batches = cultivationDb.harvest_batches.find().toArray();
  const samples = cultivationDb.lab_samples.find().toArray();
  const releases = cultivationDb.export_releases.find().toArray();
  const schedules = cultivationDb.cultivation_schedules.find().toArray();
  const existingMarkerIds = new Set(cultivationDb.legacy_integrity_markers.find().toArray()
    .map((item) => id(item._id)));
  const seasonById = new Map(seasons.map((item) => [id(item._id), item]));
  const planById = new Map(plans.map((item) => [id(item._id), item]));
  const batchById = new Map(batches.map((item) => [id(item._id), item]));
  const planned = [];
  const skipped = [];

  const farmId = "farm-minh-phat-2026";
  const plotId = "plot-khu-a-truong-thanh";
  const seasonId = "season-minh-phat-2026";
  const plan = planById.get("plan-minh-phat-vu-2026");
  const batch = batchById.get("harvest-batch-minh-phat-2026-001");
  const farm = farms.find((item) => id(item._id) === farmId);
  const hasZone = !!farm && (farm.zones || []).some((zone) => id(zone.id || zone._id) === plotId);
  const sourceProven = hasZone && plan && batch
    && plan.farmId === farmId && plan.plotId === plotId && plan.cultivationSeasonId === seasonId
    && batch.farmId === farmId && batch.plotId === plotId && batch.cultivationSeasonId === seasonId;
  if (seasonById.has(seasonId)) {
    const existing = seasonById.get(seasonId);
    if (existing.farmId !== farmId || existing.plotId !== plotId) {
      skipped.push({ collection: "cultivation_seasons", id: seasonId,
        reason: "Existing Season conflicts with source-proven Farm/Zone; manual review required" });
    }
  } else if (sourceProven) {
    const document = {
      _id: seasonId, farmId, plotId, name: "Vụ Minh Phát 2026 - Khu A",
      crop: "DURIAN", variety: "Dona",
      startDate: ISODate("2026-07-01T00:00:00Z"),
      endDate: ISODate("2026-08-25T00:00:00Z"),
      createdBy: plan.createdBy || "seed-owner-farmer",
      createdAt: plan.createdAt || new Date(), updatedAt: new Date(),
    };
    planned.push({ action: "CREATE_SEASON", collection: "cultivation_seasons", id: seasonId,
      oldReference: null, newReference: { farmId, plotId },
      reason: "01_seed_cultivation_calendar.js declares this exact ID/scope; existing Plan and Batch agree",
      confidence: "HIGH", document });
    seasonById.set(seasonId, document);
  } else {
    skipped.push({ collection: "cultivation_seasons", id: seasonId,
      reason: "Source-proven Farm/Zone/Plan/Batch preconditions are not all present" });
  }

  const validSeason = (seasonIdToCheck, farmIdToCheck, plotIdToCheck) => {
    const season = seasonById.get(id(seasonIdToCheck));
    return !!season && season.farmId === farmIdToCheck && season.plotId === plotIdToCheck;
  };
  const validPlan = (item) => validSeason(item.cultivationSeasonId, item.farmId, item.plotId);
  const validActivity = (item) => {
    const parent = planById.get(id(item.cultivationPlanId));
    return !!parent && validPlan(parent) && parent.farmId === item.farmId
      && parent.plotId === item.plotId && parent.cultivationSeasonId === item.cultivationSeasonId;
  };
  const validBatch = (item) => validSeason(item.cultivationSeasonId, item.farmId, item.plotId);
  const validSample = (item) => {
    if (!item.harvestBatchId) return seasonById.has(id(item.cultivationSeasonId));
    const parent = batchById.get(id(item.harvestBatchId));
    return !!parent && validBatch(parent) && parent.cultivationSeasonId === item.cultivationSeasonId;
  };
  const validRelease = (item) => {
    const parent = batchById.get(id(item.harvestBatchId));
    return !!parent && validBatch(parent);
  };
  function markUnverified(collection, item, reason) {
    const resourceId = id(item._id);
    const key = `${collection}:${resourceId}`;
    if (!existingMarkerIds.has(key)) {
      planned.push({ action: "MARK_LEGACY_UNVERIFIED", collection, id: resourceId,
        markerId: key, oldReference: null, newReference: null, reason,
        confidence: "HIGH" });
    }
    skipped.push({ collection, id: resourceId,
      reason: "Business linkage and status left unchanged; marker records unresolved ancestry" });
  }
  plans.filter((item) => !validPlan(item)).forEach((item) =>
    markUnverified("cultivation_plans", item, "Season missing or Plan scope differs from Season"));
  activities.filter((item) => !validActivity(item)).forEach((item) =>
    markUnverified("cultivation_activities", item, "Plan/Season missing or Activity scope differs from Plan"));
  batches.filter((item) => !validBatch(item)).forEach((item) =>
    markUnverified("harvest_batches", item, "Season missing or Batch scope differs from Season"));
  samples.filter((item) => !validSample(item)).forEach((item) =>
    markUnverified("lab_samples", item, "Batch or Season ancestry invalid"));
  releases.filter((item) => !validRelease(item)).forEach((item) =>
    markUnverified("export_releases", item,
      `Batch ancestry invalid; historical business status ${item.status} does not imply verified compliance`));
  schedules.forEach((item) => markUnverified("cultivation_schedules", item,
    "Deprecated care flow lacks canonical Farm/Season relationship"));

  if (mode === "APPLY") {
    for (const change of planned) {
      if (change.action === "CREATE_SEASON") {
        cultivationDb.cultivation_seasons.updateOne({ _id: change.id },
          { $setOnInsert: change.document }, { upsert: true });
      } else {
        cultivationDb.legacy_integrity_markers.updateOne({ _id: change.markerId },
          { $setOnInsert: { collection: change.collection, resourceId: change.id,
            integrityStatus: "LEGACY_UNVERIFIED", reason: change.reason,
            source: "repair_integrity.js", observedAt: new Date() } }, { upsert: true });
      }
    }
  }
  print(JSON.stringify({ mode, sourceEvidence: "infrastructure/mongodb/init/01_seed_cultivation_calendar.js",
    plannedChanges: planned.map(({ document, ...change }) => change), skipped,
    counts: { createSeason: planned.filter((item) => item.action === "CREATE_SEASON").length,
      markUnverified: planned.filter((item) => item.action === "MARK_LEGACY_UNVERIFIED").length,
      skipped: skipped.length } }, null, 2));
})();
