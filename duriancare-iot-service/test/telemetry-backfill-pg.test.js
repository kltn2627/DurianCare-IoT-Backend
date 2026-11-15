const { test } = require("node:test");
const assert = require("node:assert/strict");
const { Pool } = require("pg");
const { parseOptions, dryRun, apply, rollback } = require("../scripts/backfill-telemetry-history");

// Real PostgreSQL, zero lasting effect: everything runs in a scratch schema inside ONE outer
// transaction that is always rolled back. The script's BEGIN/COMMIT/ROLLBACK become savepoints.
const uri = process.env.IOT_BACKFILL_FIXTURE_PG_URI;
const source = process.env.IOT_BACKFILL_FIXTURE_SOURCE_TABLE || "duriancare_iot.telemetry";

function savepointClient(client) {
  return {
    query(sql, params) {
      if (sql === "BEGIN" || sql === "BEGIN READ ONLY") return client.query("SAVEPOINT bf");
      if (sql === "COMMIT") return client.query("RELEASE SAVEPOINT bf");
      if (sql === "ROLLBACK") return client.query("ROLLBACK TO SAVEPOINT bf");
      return client.query(sql, params);
    },
  };
}

test("provenance DDL, dry-run, apply, idempotent re-run and rollback on real PostgreSQL", {
  skip: !uri && "set IOT_BACKFILL_FIXTURE_PG_URI to run (outer transaction is always rolled back)",
}, async () => {
  const pool = new Pool({ connectionString: uri, max: 1 });
  const raw = await pool.connect();
  const schema = "iot_backfill_fixture";
  try {
    await raw.query("BEGIN");
    await raw.query(`CREATE SCHEMA ${schema}`);
    await raw.query(`CREATE TABLE ${schema}.telemetry (LIKE ${source} INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)`);
    // Genuine fixture readings every 10 s for one hour on 2026-08-12 (11:00–12:00 local).
    await raw.query(`INSERT INTO ${schema}.telemetry (device_id, temperature, humidity, soil_moisture, "timestamp", received_at)
      SELECT 'esp32-01', 34, 72, 60, ts, ts FROM generate_series(timestamptz '2026-08-12 04:00Z', timestamptz '2026-08-12 05:00Z', interval '10 seconds') ts`);
    const client = savepointClient(raw);
    const options = parseOptions({ BACKFILL_FROM: "2026-08-12T00:00:00Z", BACKFILL_TO: "2026-08-13T00:00:00Z" });

    const preview = await dryRun({ client, schema, options });
    assert.equal(preview.provenanceSchemaPresent, false);
    assert.equal(preview.toInsert, 144 - 7); // 04:00..05:00 slots overlap genuine readings

    const applied = await apply({ client, schema, options, confirm: String(preview.toInsert) });
    assert.equal(applied.inserted, preview.toInsert);
    const counts = await raw.query(`SELECT source, count(*)::int n, count(backfill_run_id)::int runs,
      count(*) FILTER (WHERE soil_moisture IS NOT NULL)::int soil FROM ${schema}.telemetry GROUP BY source ORDER BY source`);
    assert.deepEqual(counts.rows, [
      { source: "DEVICE", n: 361, runs: 0, soil: 361 },
      { source: "SYNTHETIC_BACKFILL", n: 137, runs: 137, soil: 0 },
    ]);
    const run = await raw.query(`SELECT status, planned_count, inserted_count, method, seed FROM ${schema}.telemetry_backfill_runs`);
    assert.deepEqual(run.rows[0], { status: "COMPLETED", planned_count: 137, inserted_count: 137,
      method: "diurnal-value-noise-v1", seed: "20260722" });

    const again = await dryRun({ client, schema, options });
    assert.equal(again.toInsert, 0, "re-run plans nothing");
    await assert.rejects(raw.query(`INSERT INTO ${schema}.telemetry (device_id, temperature, "timestamp", source) VALUES ('x', 30, now(), 'SYNTHETIC_BACKFILL')`));
  } finally {
    await raw.query("ROLLBACK").catch(() => {});
    raw.release();
    await pool.end();
  }
});

test("rollback removes only the run's synthetic rows (real PostgreSQL, rolled back)", {
  skip: !uri && "set IOT_BACKFILL_FIXTURE_PG_URI to run (outer transaction is always rolled back)",
}, async () => {
  const pool = new Pool({ connectionString: uri, max: 1 });
  const raw = await pool.connect();
  const schema = "iot_backfill_fixture";
  try {
    await raw.query("BEGIN");
    await raw.query(`CREATE SCHEMA ${schema}`);
    await raw.query(`CREATE TABLE ${schema}.telemetry (LIKE ${source} INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)`);
    await raw.query(`INSERT INTO ${schema}.telemetry (device_id, temperature, humidity, "timestamp", received_at)
      VALUES ('esp32-01', 33, 70, '2026-08-12 04:00Z', '2026-08-12 04:00Z')`);
    const client = savepointClient(raw);
    const options = parseOptions({ BACKFILL_FROM: "2026-08-12T00:00:00Z", BACKFILL_TO: "2026-08-12T06:00:00Z" });
    const preview = await dryRun({ client, schema, options });
    const applied = await apply({ client, schema, options, confirm: String(preview.toInsert) });
    const undone = await rollback({ client, schema, runId: applied.runId });
    assert.equal(undone.deleted, applied.inserted);
    const left = await raw.query(`SELECT source, count(*)::int n FROM ${schema}.telemetry GROUP BY source`);
    assert.deepEqual(left.rows, [{ source: "DEVICE", n: 1 }]);
    const status = await raw.query(`SELECT status FROM ${schema}.telemetry_backfill_runs`);
    assert.equal(status.rows[0].status, "ROLLED_BACK");
  } finally {
    await raw.query("ROLLBACK").catch(() => {});
    raw.release();
    await pool.end();
  }
});
