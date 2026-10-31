const {test}=require("node:test");
const assert=require("node:assert/strict");
const {buildExportFixturePlan,validateExistingFixture}=require("./seed-export-dispatches");
const farm={_id:"farm-khoa-luan-2026",ownerUserId:"owner",zones:[{id:"zone-d-khu-tay"},{id:"zone-c-khu-dong"}]};
const seasons=[{_id:"season-2026-khu-tay",farmId:farm._id,plotId:"zone-d-khu-tay",variety:"Ri6",startDate:"2026-03-01",endDate:"2026-11-30"},
{_id:"season-2026-khu-dong",farmId:farm._id,plotId:"zone-c-khu-dong",variety:"Monthong",startDate:"2026-03-01",endDate:"2026-11-30"}];
test("two deterministic labelled fixture chains, never existing business lot IDs",()=>{
 const now=new Date("2026-10-15T00:00:00Z");
 const plan=buildExportFixturePlan(farm,seasons,"owner",now);
 assert.deepEqual(plan,buildExportFixturePlan(farm,seasons,"owner",now)); assert.equal(plan.length,6);
 for(const item of plan){assert.match(item.document._id,/^demo-synthetic-/);assert.equal(item.document._fixture,"export-dispatch-2026-v1");validateExistingFixture(item.document,item);}
 assert.equal(plan.filter(i=>i.collection==="export_releases").length,2);
 assert.ok(plan.filter(i=>i.collection==="export_releases").every(i=>i.document.traceabilitySnapshot.dataOrigin==="DEMO_SYNTHETIC"));
});
test("rejects wrong ownership, missing zone, season mismatch and future scenarios",()=>{
 assert.throws(()=>buildExportFixturePlan(farm,seasons,"foreign"));
 assert.throws(()=>buildExportFixturePlan({...farm,zones:[]},seasons,"owner"));
 assert.throws(()=>buildExportFixturePlan(farm,[{...seasons[0],farmId:"foreign"},seasons[1]],"owner"));
 assert.throws(()=>buildExportFixturePlan(farm,seasons,"owner",new Date("2026-01-01")));
});
test("existing business ID collision cannot be overwritten",()=>{
 const item=buildExportFixturePlan(farm,seasons,"owner")[0];
 assert.throws(()=>validateExistingFixture({...item.document,_fixture:undefined},item),/COLLISION/);
 assert.throws(()=>validateExistingFixture({...item.document,farmId:"foreign"},item),/COLLISION/);
 assert.throws(()=>validateExistingFixture({...item.document,quantity:"9999"},item),/COLLISION/);
 const dispatch=buildExportFixturePlan(farm,seasons,"owner")[2];
 assert.throws(()=>validateExistingFixture({...dispatch.document,exportReleaseId:"real-release"},dispatch),/COLLISION/);
});
