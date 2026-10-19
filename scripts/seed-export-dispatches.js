/* Explicitly synthetic fixtures only; no real lot is repaired or reused. Run with authenticated mongosh. */
function buildExportFixturePlan(farm, seasons, ownerId, now = new Date()) {
  if (!farm || String(farm._id) !== "farm-khoa-luan-2026" || farm.ownerUserId !== ownerId || !ownerId) {
    throw new Error("FIXTURE_FARM_OWNER_MISMATCH");
  }
  const scenarios = [
    {key:"ri6",variety:"Ri6",quantity:"3500",zone:"zone-d-khu-tay",season:"season-2026-khu-tay",harvest:"2026-08-28T02:00:00Z",ship:"2026-08-29T02:00:00Z",delivered:false},
    {key:"monthong",variety:"Monthong",quantity:"5000",zone:"zone-c-khu-dong",season:"season-2026-khu-dong",harvest:"2026-09-14T02:00:00Z",ship:"2026-09-15T02:00:00Z",delivered:true},
  ];
  const plan = [];
  for (const s of scenarios) {
    const season = seasons.find(value=>String(value._id)===s.season);
    if (!season || season.farmId !== String(farm._id) || season.plotId !== s.zone ||
        !farm.zones.some(zone=>zone.id===s.zone) || !String(season.variety || "").toLowerCase().includes(String(s.variety).toLowerCase()) ||
        String(season.startDate)>s.harvest.slice(0,10) || (season.endDate && String(season.endDate)<s.harvest.slice(0,10)) ||
        Date.parse(s.ship)>now.getTime() || (s.delivered && Date.parse("2026-09-16T02:00:00Z")>now.getTime())) throw new Error("FIXTURE_SEASON_ZONE_DATE_MISMATCH");
    const prefix = `demo-synthetic-export-2026-${s.key}`;
    const code = `DEMO-SYNTHETIC-2026-${s.key.toUpperCase()}`;
    const inspection = {sourceRef:`demo://${prefix}/inspection`,inspectedBy:ownerId,inspectedAt:s.harvest,
      phiChecked:true,pestCheckPassed:true,qualityPassed:true,measuredBrix:"31.5",dataOrigin:"DEMO_SYNTHETIC",
      scenarioPhiDays:21,realLabEvidence:false,warning:"SIMULATION ONLY - no real residue laboratory result or shipment evidence"};
    const snapshot = {dataOrigin:"DEMO_SYNTHETIC",fixtureGroup:"export-dispatch-2026-v1",farmId:String(farm._id),
      plotId:s.zone,cultivationSeasonId:s.season,harvestBatchId:`${prefix}-lot`,variety:s.variety,
      farmerInspection:inspection,demoDispatchStatus:s.delivered?"DELIVERED":"DISPATCHED"};
    plan.push({collection:"harvest_batches",document:{_id:`${prefix}-lot`,batchCode:`${code}-LOT`,
      cultivationSeasonId:s.season,farmId:String(farm._id),plotId:s.zone,harvestedAt:s.harvest,quantity:s.quantity,
      quantityUnit:"KG",expectedDestinationMarket:"VN",latestSafeHarvestDate:s.harvest.slice(0,10),
      chemicalRiskLevel:"LOW",status:"HARVESTED",createdBy:ownerId,createdAt:now.toISOString(),
      _fixture:"export-dispatch-2026-v1",dataOrigin:"DEMO_SYNTHETIC"}});
    plan.push({collection:"export_releases",document:{_id:`${prefix}-release`,releaseCode:code,harvestBatchId:`${prefix}-lot`,
      targetMarketCode:"VN",traceabilitySnapshot:snapshot,complianceAssessmentSnapshot:{dataOrigin:"DEMO_SYNTHETIC",
      eligibleForExportRelease:true,realLabEvidence:false,warning:"Synthetic scenario, not real compliance certification"},
      status:"RELEASED",submittedBy:ownerId,submittedAt:s.harvest,reviewedBy:ownerId,reviewedAt:s.harvest,
      releasedAt:s.harvest,createdAt:now.toISOString(),_fixture:"export-dispatch-2026-v1"}});
    const e={harvestSourceRef:`demo://${prefix}/harvest`,gradingSourceRef:`demo://${prefix}/grade`,packingSourceRef:`demo://${prefix}/pack`,
      dispatchSourceRef:`demo://${prefix}/dispatch`,harvestedQuantity:s.quantity,qualifiedQuantity:s.quantity,rejectedQuantity:"0",
      packedQuantity:s.quantity,shippedQuantity:s.quantity,quantityUnit:"KG",packedAt:s.harvest,shippedAt:s.ship,
      destination:"DEMO_SYNTHETIC - buyer/carrier scenario, not a real customer"};
    plan.push({collection:"harvest_dispatches",document:{_id:`${prefix}-lot`,exportReleaseId:`${prefix}-release`,
      farmId:String(farm._id),plotId:s.zone,cultivationSeasonId:s.season,evidence:e,recordedBy:ownerId,
      recordedAt:now.toISOString(),confirmedBy:ownerId,confirmedAt:now.toISOString(),version:0,
      delivery:s.delivered?{sourceRef:`demo://${prefix}/delivery`,deliveredAt:"2026-09-16T02:00:00Z",recordedBy:ownerId,recordedAt:now.toISOString()}:null,
      _fixture:"export-dispatch-2026-v1",dataOrigin:"DEMO_SYNTHETIC"}});
  }
  return plan;
}

function validateExistingFixture(existing, item) {
  if (!existing) return;
  if (existing._fixture !== "export-dispatch-2026-v1" || String(existing._id)!==item.document._id ||
      (item.collection === "export_releases" ? existing.releaseCode!==item.document.releaseCode ||
       existing.harvestBatchId!==item.document.harvestBatchId || existing.traceabilitySnapshot?.dataOrigin!=="DEMO_SYNTHETIC"
       : existing.farmId!==item.document.farmId || existing.plotId!==item.document.plotId || existing.cultivationSeasonId!==item.document.cultivationSeasonId) ||
      (item.collection === "harvest_batches" && (existing.batchCode!==item.document.batchCode || String(existing.quantity)!==item.document.quantity)) ||
      (item.collection === "harvest_dispatches" && (existing.exportReleaseId!==item.document.exportReleaseId ||
       existing.evidence?.dispatchSourceRef!==item.document.evidence.dispatchSourceRef || String(existing.evidence?.shippedQuantity)!==item.document.evidence.shippedQuantity))) {
    throw new Error("FIXTURE_COLLISION_NO_OVERWRITE");
  }
}

if (typeof module !== "undefined") module.exports={buildExportFixturePlan,validateExistingFixture};
if (typeof db !== "undefined") {
  if (process.env.DURIANCARE_EXPORT_FIXTURE_ENV !== "local-demo") throw new Error("EXPLICIT_LOCAL_DEMO_ENV_REQUIRED");
  const ownerId=process.env.DURIANCARE_EXPORT_FIXTURE_OWNER_ID;
  const farm=db.getSiblingDB("duriancare_farm").farms.findOne({_id:"farm-khoa-luan-2026"});
  const source=db.getSiblingDB("duriancare_cultivation");
  const plan=buildExportFixturePlan(farm,source.cultivation_seasons.find({farmId:"farm-khoa-luan-2026"}).toArray(),ownerId);
  let existingCount=0;
  for (const item of plan) {
    const collection=source.getCollection(item.collection);
    const existing=collection.findOne({_id:item.document._id});
    validateExistingFixture(existing,item);
    if(existing)existingCount++;
    const codeField=item.collection==="harvest_batches"?"batchCode":item.collection==="export_releases"?"releaseCode":null;
    if(codeField && collection.findOne({[codeField]:item.document[codeField],_id:{$ne:item.document._id}})) throw new Error("FIXTURE_CODE_COLLISION_NO_OVERWRITE");
  }
  const apply=process.env.DURIANCARE_EXPORT_FIXTURE_APPLY === "true";
  const inserted=[];
  function bson(value,key) {
    if (value===null) return null;
    if (Array.isArray(value)) return value.map(v=>bson(v));
    if (typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,bson(v,k)]));
    if (typeof value === "string" && /At$/.test(key||"")) return new Date(value);
    if (typeof value === "string" && /^(quantity|harvestedQuantity|qualifiedQuantity|rejectedQuantity|packedQuantity|shippedQuantity)$/.test(key||"")) return NumberDecimal(value);
    return value;
  }
  if (apply) {
    if (process.env.DURIANCARE_EXPORT_FIXTURE_ACK !== "SYNTHETIC_NOT_BUSINESS_EVIDENCE") throw new Error("EXPLICIT_SYNTHETIC_ACK_REQUIRED");
    // Standalone Mongo cannot atomically commit multiple collections. Stable IDs make interrupted runs resumable.
    for (const item of plan) {
      const result=source.getCollection(item.collection).updateOne({_id:item.document._id},{$setOnInsert:bson(item.document)},{upsert:true});
      if (result.upsertedCount) inserted.push(item.document._id);
    }
  }
  print(JSON.stringify({mode:apply?"APPLIED_SYNTHETIC_FIXTURES":"DRY_RUN",dataOrigin:"DEMO_SYNTHETIC",farmId:farm._id,
    candidateRecords:plan.length,inserted,existingBeforeRun:existingCount,pendingBeforeRun:plan.length-existingCount,ids:plan.map(i=>i.document._id),
    realShipmentEvidence:false,partialFailureStrategy:"Rerun same script after collision audit; never delete existing records"}));
}
