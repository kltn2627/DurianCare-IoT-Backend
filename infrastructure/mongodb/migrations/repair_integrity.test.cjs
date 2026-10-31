const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const migrationSource = fs.readFileSync(path.join(__dirname, "repair_integrity.js"), "utf8");
const seedSource = fs.readFileSync(path.join(__dirname, "..", "init", "01_seed_cultivation_calendar.js"), "utf8");

function fixture() {
  return {
    duriancare_farm: {
      farms: [{ _id: "farm-minh-phat-2026", zones: [{ id: "plot-khu-a-truong-thanh" }, { id: "plot-khu-b-ra-hoa" }] }],
    },
    duriancare_cultivation: {
      cultivation_seasons: [],
      cultivation_plans: [
        { _id: "plan-minh-phat-vu-2026", farmId: "farm-minh-phat-2026", plotId: "plot-khu-a-truong-thanh", cultivationSeasonId: "season-minh-phat-2026", createdBy: "owner" },
        { _id: "demo-plan", farmId: "demo-farm", plotId: "demo-zone", cultivationSeasonId: "demo-season" },
      ],
      cultivation_activities: [
        { _id: "zone-b-activity", farmId: "farm-minh-phat-2026", plotId: "plot-khu-b-ra-hoa", cultivationSeasonId: "season-minh-phat-2026", cultivationPlanId: "plan-minh-phat-vu-2026" },
      ],
      harvest_batches: [
        { _id: "harvest-batch-minh-phat-2026-001", farmId: "farm-minh-phat-2026", plotId: "plot-khu-a-truong-thanh", cultivationSeasonId: "season-minh-phat-2026" },
        { _id: "demo-batch", farmId: "demo-farm", plotId: "demo-zone", cultivationSeasonId: "demo-season" },
      ],
      lab_samples: [
        { _id: "sample", harvestBatchId: "harvest-batch-minh-phat-2026-001", cultivationSeasonId: "season-minh-phat-2026" },
      ],
      export_releases: [{ _id: "demo-release", harvestBatchId: "demo-batch", status: "RELEASED" }],
      cultivation_schedules: [{ _id: "demo-schedule", cropId: "demo-season" }],
      legacy_integrity_markers: [],
    },
  };
}

function run(source, data, mode = "DRY_RUN", backupConfirmed = true) {
  let output = "";
  let writes = 0;
  const databases = new Map();
  const db = {
    getSiblingDB(name) {
      if (!databases.has(name)) databases.set(name, new Proxy({}, {
        get(_, collectionName) {
          if (!data[name][collectionName]) data[name][collectionName] = [];
          const rows = data[name][collectionName];
          return {
            find: () => ({ toArray: () => rows }),
            updateOne(filter, update, options = {}) {
              writes++;
              const row = rows.find((item) => item._id === filter._id);
              if (row) Object.assign(row, update.$set || {});
              else if (options.upsert) rows.push({ _id: filter._id, ...(update.$set || update.$setOnInsert || {}) });
            },
          };
        },
      }));
      return databases.get(name);
    },
  };
  vm.runInNewContext(source, {
    db, process: { env: { MIGRATION_MODE: mode, BACKUP_CONFIRMED: backupConfirmed ? "true" : "false" } },
    print: (value) => { output = value; },
    ISODate: (value) => new Date(value), NumberDecimal: Number, Date, Map, Set,
  });
  return { report: output.startsWith("{") ? JSON.parse(output) : null, writes };
}

test("dry-run leaves every collection unchanged", () => {
  const data = fixture();
  const before = JSON.stringify(data);
  const { writes } = run(migrationSource, data);
  assert.equal(writes, 0);
  assert.equal(JSON.stringify(data), before);
});

test("apply refuses to run without a verified backup", () => {
  assert.throws(() => run(migrationSource, fixture(), "APPLY", false), /verified BSON backup/);
});

test("migration plans only the source-proven Minh Phat Season", () => {
  const { report } = run(migrationSource, fixture());
  assert.deepEqual(report.plannedChanges.filter((item) => item.action === "CREATE_SEASON").map((item) => item.id),
    ["season-minh-phat-2026"]);
});

test("apply creates exact Farm and Zone ancestry without inventing demo Season", () => {
  const data = fixture();
  run(migrationSource, data, "APPLY");
  assert.equal(data.duriancare_cultivation.cultivation_seasons.length, 1);
  assert.equal(data.duriancare_cultivation.cultivation_seasons[0].farmId, "farm-minh-phat-2026");
  assert.equal(data.duriancare_cultivation.cultivation_seasons[0].plotId, "plot-khu-a-truong-thanh");
});

test("apply marks released demo ancestry unverified without changing business status", () => {
  const data = fixture();
  run(migrationSource, data, "APPLY");
  assert.equal(data.duriancare_cultivation.export_releases[0].status, "RELEASED");
  assert.equal(data.duriancare_cultivation.legacy_integrity_markers.find((item) => item._id === "export_releases:demo-release").integrityStatus,
    "LEGACY_UNVERIFIED");
});

test("Zone B activity with Zone A Plan is marked and its IDs remain intact", () => {
  const data = fixture();
  run(migrationSource, data, "APPLY");
  assert.equal(data.duriancare_cultivation.cultivation_activities[0].cultivationPlanId, "plan-minh-phat-vu-2026");
  assert.ok(data.duriancare_cultivation.legacy_integrity_markers.some((item) => item.resourceId === "zone-b-activity"));
});

test("missing source precondition skips Season creation", () => {
  const data = fixture();
  data.duriancare_cultivation.harvest_batches.shift();
  const { report } = run(migrationSource, data, "APPLY");
  assert.equal(report.counts.createSeason, 0);
  assert.equal(data.duriancare_cultivation.cultivation_seasons.length, 0);
});

test("reapplying migration makes no new changes", () => {
  const data = fixture();
  run(migrationSource, data, "APPLY");
  const second = run(migrationSource, data, "APPLY");
  assert.equal(second.writes, 0);
  assert.equal(second.report.plannedChanges.length, 0);
});

test("new seed creates complete Farm Zone Season Plan Activity Batch Sample chain", () => {
  const data = { duriancare_farm: {}, duriancare_cultivation: {} };
  run(seedSource, data);
  const farm = data.duriancare_farm.farms[0];
  const c = data.duriancare_cultivation;
  const seasons = new Map(c.cultivation_seasons.map((item) => [item._id, item]));
  const plans = new Map(c.cultivation_plans.map((item) => [item._id, item]));
  for (const plan of c.cultivation_plans) {
    assert.equal(seasons.get(plan.cultivationSeasonId).plotId, plan.plotId);
    assert.ok(farm.zones.some((zone) => zone.id === plan.plotId));
  }
  for (const activity of c.cultivation_activities) {
    const plan = plans.get(activity.cultivationPlanId);
    assert.equal(activity.plotId, plan.plotId);
    assert.equal(activity.cultivationSeasonId, plan.cultivationSeasonId);
  }
  const batch = c.harvest_batches[0];
  assert.equal(seasons.get(batch.cultivationSeasonId).plotId, batch.plotId);
  assert.equal(c.lab_samples[0].harvestBatchId, batch._id);
});
