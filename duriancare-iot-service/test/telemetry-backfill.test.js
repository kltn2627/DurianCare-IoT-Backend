const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  syntheticReading, validateReading, parseOptions, buildSlots, planBackfill, summarize, localDay, dryRun, apply, rollback,
} = require("../scripts/backfill-telemetry-history");
const { provenanceStatements } = require("../src/telemetryProvenance");

const env = { BACKFILL_FROM: "2026-07-22T06:01:31.848Z", BACKFILL_TO: "2026-09-30T17:00:00Z" };
const options = parseOptions(env);
const ms = iso => Date.parse(iso);

test("generator is deterministic, plausible, smooth and day/night shaped", () => {
  const plan = planBackfill({ options, realTimestamps: [], syntheticTimestamps: [] });
  assert.deepEqual(plan.rows.slice(0, 50), planBackfill({ options, realTimestamps: [], syntheticTimestamps: [] }).rows.slice(0, 50));
  const summary = summarize(options, plan);
  assert.ok(summary.temperature.min >= 23 && summary.temperature.max <= 36);
  assert.ok(summary.humidity.min >= 55 && summary.humidity.max <= 97);
  assert.ok(summary.maxStepBetweenConsecutiveSlots.temperature <= 1.5, JSON.stringify(summary.maxStepBetweenConsecutiveSlots));
  assert.ok(summary.maxStepBetweenConsecutiveSlots.humidity <= 6);
  for (const row of plan.rows) {
    assert.ok(Number.isFinite(row.temperature) && Number.isFinite(row.humidity));
  }
  const afternoon = syntheticReading(ms("2026-08-20T07:30:00Z"), options.seed); // 14:30 local
  const night = syntheticReading(ms("2026-08-20T19:30:00Z"), options.seed);     // 02:30 local
  assert.ok(afternoon.temperature > night.temperature + 3);
  assert.ok(night.humidity > afternoon.humidity + 8);
  assert.ok(new Set(plan.rows.slice(0, 144).map(r => r.temperature)).size > 30, "not flat");
});

test("invalid synthetic values are rejected before any write", () => {
  for (const bad of [{ temperature: NaN, humidity: 80 }, { temperature: 30, humidity: 101 }, { temperature: 60, humidity: 50 },
    { temperature: 30, humidity: null }]) {
    assert.throws(() => validateReading(bad), /INVALID_SYNTHETIC_READING/);
  }
});

test("options reject future ranges, bad cadence and unsafe device ids", () => {
  for (const extra of [{ BACKFILL_TO: "2099-01-01T00:00:00Z" }, { BACKFILL_CADENCE_SECONDS: "10" }, { BACKFILL_DEVICE_ID: "x'; drop" },
    { BACKFILL_FROM: "2026-10-01T00:00:00Z" }]) {
    assert.throws(() => parseOptions({ ...env, ...extra }));
  }
});

test("slots align to the cadence and local-day boundaries use Asia/Ho_Chi_Minh", () => {
  const slots = buildSlots(new Date("2026-07-22T06:01:31.848Z"), new Date("2026-07-22T07:00:00Z"), 600);
  assert.equal(new Date(slots[0]).toISOString(), "2026-07-22T06:10:00.000Z");
  assert.equal(slots.length, 5);
  assert.equal(localDay(ms("2026-09-30T16:59:59Z")), "2026-09-30");
  assert.equal(localDay(ms("2026-09-30T17:00:00Z")), "2026-10-01");
  const summary = summarize(options, planBackfill({ options, realTimestamps: [], syntheticTimestamps: [] }));
  assert.equal(Object.keys(summary.perDay)[0], "2026-07-22");
  assert.equal(Object.keys(summary.perDay).at(-1), "2026-09-30");
  assert.equal(summary.perDay["2026-08-01"], 144);
  assert.equal(summary.perDay["2026-07-22"], 65); // 13:10 .. 23:50 local
});

test("genuine readings win: slots within half a cadence of a real reading are skipped", () => {
  const real = [];
  for (let t = ms("2026-08-12T04:09:21Z"); t <= ms("2026-08-12T14:04:00Z"); t += 10_000) real.push(t);
  const plan = planBackfill({ options, realTimestamps: real, syntheticTimestamps: [] });
  const half = 300_000;
  for (const row of plan.rows) {
    assert.ok(!real.some(t => Math.abs(t - row.timestamp) < half), new Date(row.timestamp).toISOString());
  }
  const aug12 = plan.rows.filter(r => localDay(r.timestamp) === "2026-08-12");
  assert.ok(aug12.length > 0 && aug12.length < 144);
  assert.ok(plan.skippedReal >= 59);
});

test("re-running plans zero rows once synthetic rows exist (idempotent)", () => {
  const first = planBackfill({ options, realTimestamps: [], syntheticTimestamps: [] });
  const second = planBackfill({ options, realTimestamps: [], syntheticTimestamps: first.rows.map(r => r.timestamp) });
  assert.equal(second.rows.length, 0);
  assert.equal(second.skippedSynthetic, first.rows.length);
});

function mockClient({ provenance = true, real = [], synthetic = [] } = {}) {
  const queries = [];
  return {
    queries,
    async query(sql, params) {
      queries.push({ sql, params });
      if (sql.includes("information_schema.columns")) return { rowCount: provenance ? 1 : 0, rows: [] };
      if (sql.includes(`SELECT "timestamp"`) && sql.includes("SYNTHETIC_BACKFILL")) {
        return { rows: synthetic.map(t => ({ timestamp: new Date(t) })) };
      }
      if (sql.includes(`SELECT "timestamp"`)) return { rows: real.map(t => ({ timestamp: new Date(t) })) };
      if (sql.startsWith("INSERT INTO") && sql.includes("unnest")) return { rowCount: params[2].length };
      if (sql.includes("FOR UPDATE")) return { rowCount: 1, rows: [{ status: "COMPLETED" }] };
      if (sql.startsWith("DELETE")) return { rowCount: 7 };
      return { rowCount: 0, rows: [] };
    },
  };
}

test("dry-run is read-only and reports what would be inserted", async () => {
  const client = mockClient({ provenance: false });
  const result = await dryRun({ client, schema: "duriancare_iot", options });
  assert.equal(result.mode, "DRY_RUN");
  assert.equal(result.writes, 0);
  assert.equal(result.provenanceSchemaPresent, false);
  assert.ok(result.toInsert > 9000);
  assert.equal(client.queries[0].sql, "BEGIN READ ONLY");
  assert.equal(client.queries.at(-1).sql, "ROLLBACK");
  assert.ok(client.queries.every(q => !/^\s*(INSERT|UPDATE|DELETE|ALTER|CREATE)/i.test(q.sql)));
});

test("apply labels rows, audits the run, needs the dry-run count and never touches live device state", async () => {
  const preview = await dryRun({ client: mockClient(), schema: "duriancare_iot", options });
  const wrong = mockClient();
  await assert.rejects(apply({ client: wrong, schema: "duriancare_iot", options, confirm: "1" }), /CONFIRM_MISMATCH/);
  assert.equal(wrong.queries.at(-1).sql, "ROLLBACK");
  assert.ok(!wrong.queries.some(q => q.sql.includes("unnest")));

  const client = mockClient();
  const result = await apply({ client, schema: "duriancare_iot", options, confirm: String(preview.toInsert) });
  assert.equal(result.inserted, preview.toInsert);
  const inserts = client.queries.filter(q => q.sql.includes("unnest"));
  assert.ok(inserts.length > 1 && inserts.every(q => q.params[2].length <= 500), "bounded batches");
  assert.ok(inserts.every(q => q.sql.includes("'SYNTHETIC_BACKFILL', $2::uuid") && q.params[1] === result.runId));
  assert.ok(inserts.every(q => /ON CONFLICT \(device_id, "timestamp"\) WHERE source = 'SYNTHETIC_BACKFILL' DO NOTHING/.test(q.sql)));
  assert.ok(inserts.every(q => q.sql.includes("real.source = 'DEVICE'")), "re-checks genuine overlap at write time");
  assert.ok(client.queries.some(q => q.sql.includes("telemetry_backfill_runs") && q.sql.startsWith("INSERT")));
  assert.ok(client.queries.some(q => q.sql.includes("status = 'COMPLETED'")));
  assert.equal(client.queries.at(-1).sql, "COMMIT");
  const all = client.queries.map(q => q.sql).join("\n");
  assert.doesNotMatch(all, /iot_devices|last_seen|iot_alerts/);
  assert.doesNotMatch(all, /UPDATE[^\n]*telemetry\s+SET|DELETE FROM/);
});

test("rollback deletes only one run's synthetic rows and keeps the audit record", async () => {
  const client = mockClient();
  const result = await rollback({ client, schema: "duriancare_iot", runId: "8f2c5a7e-1b3d-4c5e-9f00-123456789abc" });
  assert.equal(result.deleted, 7);
  const del = client.queries.find(q => q.sql.startsWith("DELETE"));
  assert.match(del.sql, /WHERE backfill_run_id = \$1 AND source = 'SYNTHETIC_BACKFILL'/);
  assert.ok(client.queries.some(q => q.sql.includes("status = 'ROLLED_BACK'")));
  await assert.rejects(rollback({ client, schema: "duriancare_iot", runId: "x" }), /INVALID_RUN_ID/);
});

test("provenance schema keeps genuine rows unattributed and synthetic rows tied to a run", () => {
  const sql = provenanceStatements("duriancare_iot").join("\n");
  assert.match(sql, /source VARCHAR\(32\) NOT NULL DEFAULT 'DEVICE'/);
  assert.match(sql, /source = 'DEVICE' AND backfill_run_id IS NULL/);
  assert.match(sql, /source = 'SYNTHETIC_BACKFILL' AND backfill_run_id IS NOT NULL/);
  assert.match(sql, /UNIQUE INDEX IF NOT EXISTS uq_telemetry_synthetic_device_ts/);
});
