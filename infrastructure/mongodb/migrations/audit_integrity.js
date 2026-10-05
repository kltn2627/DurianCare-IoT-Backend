// Read-only mongosh audit. Run against the Farm and Cultivation databases.
// This script intentionally contains no insert, update, delete, or aggregation write stage.
(function () {
  const farmDb = db.getSiblingDB("duriancare_farm");
  const cultivationDb = db.getSiblingDB("duriancare_cultivation");
  const farms = farmDb.farms.find().toArray();
  const seasons = cultivationDb.cultivation_seasons.find().toArray();
  const plans = cultivationDb.cultivation_plans.find().toArray();
  const activities = cultivationDb.cultivation_activities.find().toArray();
  const schedules = cultivationDb.cultivation_schedules.find().toArray();
  const batches = cultivationDb.harvest_batches.find().toArray();
  const samples = cultivationDb.lab_samples.find().toArray();
  const results = cultivationDb.lab_residue_results.find().toArray();
  const releases = cultivationDb.export_releases.find().toArray();
  const markers = cultivationDb.legacy_integrity_markers.find().toArray();
  const markerByKey = new Map(markers.map((marker) =>
    [`${marker.collection}:${String(marker.resourceId)}`, marker]));

  const id = (value) => value == null ? null : String(value);
  const zones = farms.flatMap((farm) => (farm.zones || []).map((zone) => ({
    farmId: id(farm._id), id: id(zone._id || zone.id), name: zone.name,
  })));
  const farmById = new Map(farms.map((farm) => [id(farm._id), farm]));
  const zoneByScope = new Map(zones.map((zone) => [`${zone.farmId}:${zone.id}`, zone]));
  const seasonById = new Map(seasons.map((season) => [id(season._id), season]));
  const planById = new Map(plans.map((plan) => [id(plan._id), plan]));
  const batchById = new Map(batches.map((batch) => [id(batch._id), batch]));
  const sampleById = new Map(samples.map((sample) => [id(sample._id), sample]));
  const records = [];
  const stateByKey = new Map();

  function add(collection, itemId, currentReferences, status, problems, candidates, confidence) {
    const marker = markerByKey.get(`${collection}:${itemId}`);
    if (marker && status !== "VALID") {
      status = "LEGACY_UNVERIFIED";
      problems.push(`Migration marker: ${marker.reason}`);
    }
    const entry = {
      collection, id: itemId, currentReferences, status,
      problem: problems.length ? problems.join("; ") : null,
      possibleParentCandidates: candidates,
      confidence: confidence || (status === "VALID" ? "HIGH" : "NONE"),
    };
    records.push(entry);
    stateByKey.set(`${collection}:${itemId}`, status);
  }

  farms.forEach((farm) => add("farms", id(farm._id), { ownerUserId: farm.ownerUserId },
    "VALID", [], [], "HIGH"));
  zones.forEach((zone) => add("zones", zone.id, { farmId: zone.farmId },
    zone.id && farmById.has(zone.farmId) ? "VALID" : "LEGACY_UNVERIFIED",
    zone.id ? [] : ["Zone has no ID"], [], zone.id ? "HIGH" : "NONE"));

  seasons.forEach((season) => {
    const farmId = id(season.farmId), plotId = id(season.plotId);
    const problems = [];
    if (!farmById.has(farmId)) problems.push("farmId does not exist");
    if (!zoneByScope.has(`${farmId}:${plotId}`)) problems.push("plotId does not belong to farmId");
    add("cultivation_seasons", id(season._id), { farmId, plotId },
      problems.length ? "ORPHAN" : "VALID", problems,
      zones.filter((zone) => zone.id === plotId).map((zone) => ({ farmId: zone.farmId, plotId: zone.id })),
      problems.length ? "LOW" : "HIGH");
  });

  plans.forEach((plan) => {
    const seasonId = id(plan.cultivationSeasonId), season = seasonById.get(seasonId);
    const farmId = id(plan.farmId), plotId = id(plan.plotId);
    const problems = [];
    if (!season) problems.push("cultivationSeasonId does not exist");
    if (season && (season.farmId !== farmId || season.plotId !== plotId)) problems.push("Plan scope differs from Season");
    if (!zoneByScope.has(`${farmId}:${plotId}`)) problems.push("Plan Zone does not belong to Farm");
    const status = !season ? "ORPHAN" : problems.length ? "SCOPE_MISMATCH"
      : stateByKey.get(`cultivation_seasons:${seasonId}`) !== "VALID" ? "LEGACY_UNVERIFIED" : "VALID";
    add("cultivation_plans", id(plan._id), { farmId, plotId, cultivationSeasonId: seasonId },
      status, problems, season ? [{ seasonId, farmId: season.farmId, plotId: season.plotId }]
        : zones.filter((zone) => zone.farmId === farmId && zone.id === plotId)
          .map((zone) => ({ farmId, plotId, seasonId: null })),
      status === "VALID" ? "HIGH" : "LOW");
  });

  activities.forEach((activity) => {
    const planId = id(activity.cultivationPlanId), plan = planById.get(planId);
    const seasonId = id(activity.cultivationSeasonId), season = seasonById.get(seasonId);
    const farmId = id(activity.farmId), plotId = id(activity.plotId);
    const problems = [];
    if (!plan) problems.push("cultivationPlanId does not exist");
    if (!season) problems.push("cultivationSeasonId does not exist");
    const mismatch = plan && (plan.farmId !== farmId || plan.plotId !== plotId || plan.cultivationSeasonId !== seasonId);
    if (mismatch) problems.push("Activity farmId/plotId/seasonId differs from Plan");
    const status = mismatch ? "SCOPE_MISMATCH" : !plan || !season ? "ORPHAN"
      : stateByKey.get(`cultivation_plans:${planId}`) !== "VALID" ? "LEGACY_UNVERIFIED" : "VALID";
    const candidates = plans.filter((candidate) => candidate.farmId === farmId
      && candidate.plotId === plotId && candidate.cultivationSeasonId === seasonId)
      .map((candidate) => ({ cultivationPlanId: id(candidate._id) }));
    add("cultivation_activities", id(activity._id),
      { cultivationPlanId: planId, cultivationSeasonId: seasonId, farmId, plotId },
      status, problems, candidates, status === "VALID" ? "HIGH" : candidates.length === 1 ? "MEDIUM" : "LOW");
  });

  batches.forEach((batch) => {
    const seasonId = id(batch.cultivationSeasonId), season = seasonById.get(seasonId);
    const farmId = id(batch.farmId), plotId = id(batch.plotId);
    const problems = [];
    if (!season) problems.push("cultivationSeasonId does not exist");
    if (season && (season.farmId !== farmId || season.plotId !== plotId)) problems.push("Batch scope differs from Season");
    const status = !season ? "ORPHAN" : problems.length ? "SCOPE_MISMATCH"
      : stateByKey.get(`cultivation_seasons:${seasonId}`) !== "VALID" ? "LEGACY_UNVERIFIED" : "VALID";
    add("harvest_batches", id(batch._id), { cultivationSeasonId: seasonId, farmId, plotId },
      status, problems, season ? [{ seasonId }] : [], status === "VALID" ? "HIGH" : "NONE");
  });

  samples.forEach((sample) => {
    const batchId = id(sample.harvestBatchId), batch = batchById.get(batchId);
    const seasonId = id(sample.cultivationSeasonId);
    const problems = [];
    if (batchId && !batch) problems.push("harvestBatchId does not exist");
    if (batch && batch.cultivationSeasonId !== seasonId) problems.push("Sample Season differs from Batch");
    const status = !batchId ? seasonById.has(seasonId) ? "VALID" : "ORPHAN" : !batch ? "ORPHAN"
      : batch.cultivationSeasonId !== seasonId ? "SCOPE_MISMATCH"
      : stateByKey.get(`harvest_batches:${batchId}`) !== "VALID" ? "LEGACY_UNVERIFIED" : "VALID";
    add("lab_samples", id(sample._id), { harvestBatchId: batchId, cultivationSeasonId: seasonId },
      status, problems, batch ? [{ harvestBatchId: batchId }] : [], status === "VALID" ? "HIGH" : "LOW");
  });

  results.forEach((result) => {
    const sampleId = id(result.labSampleId), sample = sampleById.get(sampleId);
    const status = !sample ? "ORPHAN"
      : stateByKey.get(`lab_samples:${sampleId}`) !== "VALID" ? "LEGACY_UNVERIFIED" : "VALID";
    add("lab_residue_results", id(result._id), { labSampleId: sampleId }, status,
      sample ? [] : ["labSampleId does not exist"], sample ? [{ labSampleId: sampleId }] : [],
      sample ? "HIGH" : "NONE");
  });

  releases.forEach((release) => {
    const batchId = id(release.harvestBatchId), batch = batchById.get(batchId);
    const status = !batch ? "ORPHAN"
      : stateByKey.get(`harvest_batches:${batchId}`) !== "VALID" ? "LEGACY_UNVERIFIED" : "VALID";
    const problems = !batch ? ["harvestBatchId does not exist"]
      : status === "LEGACY_UNVERIFIED" ? ["Batch ancestry is invalid"] : [];
    add("export_releases", id(release._id), { harvestBatchId: batchId, businessStatus: release.status },
      status, problems, batch ? [{ harvestBatchId: batchId, cultivationSeasonId: batch.cultivationSeasonId }] : [],
      status === "VALID" ? "HIGH" : "LOW");
  });

  schedules.forEach((schedule) => add("cultivation_schedules", id(schedule._id),
    { zoneId: id(schedule.zoneId), cropId: id(schedule.cropId) }, "LEGACY_UNVERIFIED",
    ["Legacy schedule has no canonical farmId/cultivationSeasonId relationship"],
    seasonById.has(id(schedule.cropId)) ? [{ cultivationSeasonId: id(schedule.cropId) }] : [], "LOW"));

  const collections = ["farms", "zones", "cultivation_seasons", "cultivation_plans",
    "cultivation_activities", "cultivation_schedules", "harvest_batches", "lab_samples",
    "lab_residue_results", "export_releases"];
  const counts = Object.fromEntries(collections.map((collection) => [collection, {
    total: records.filter((record) => record.collection === collection).length,
    VALID: records.filter((record) => record.collection === collection && record.status === "VALID").length,
    ORPHAN: records.filter((record) => record.collection === collection && record.status === "ORPHAN").length,
    SCOPE_MISMATCH: records.filter((record) => record.collection === collection && record.status === "SCOPE_MISMATCH").length,
    LEGACY_UNVERIFIED: records.filter((record) => record.collection === collection && record.status === "LEGACY_UNVERIFIED").length,
  }]));
  print(JSON.stringify({ generatedAt: new Date().toISOString(), mode: "READ_ONLY", counts,
    legacyMarkerCount: markers.length, records }, null, 2));
})();
