"use strict";

// Synthetic historical backfill for air temperature/humidity, clearly labelled as such.
//
//   node scripts/backfill-telemetry-history.js                 dry-run (default, read-only)
//   BACKFILL_APPLY=true BACKFILL_CONFIRM=<to_insert> ...       write, in one transaction
//   BACKFILL_APPLY=true BACKFILL_ROLLBACK_RUN_ID=<uuid> ...    delete exactly one run's synthetic rows
//
// Rows are written with source='SYNTHETIC_BACKFILL' and a backfill_run_id (audit row in
// telemetry_backfill_runs). They are inserted directly, NOT through POST /api/v1/sensors/data, so
// they never touch iot_devices.last_seen_at, never publish to Kafka and never evaluate live alerts.
// Soil moisture is left NULL: only air temperature/humidity are backfilled.
const crypto = require("node:crypto");
const { SOURCE_DEVICE, SOURCE_SYNTHETIC, ensureTelemetryProvenanceSchema } = require("../src/telemetryProvenance");

const METHOD = "diurnal-value-noise-v1";
const TZ_OFFSET_MS = 7 * 3600_000; // Asia/Ho_Chi_Minh, no DST
const HOUR_MS = 3600_000;
const DAY_MS = 24 * HOUR_MS;
const BATCH_SIZE = 500;

const DEFAULTS = {
  deviceId: "esp32-01",
  // Not earlier than the farmer account's creation (2026-07-22 06:01:31.848Z, same rule as seed-iot-history.js).
  from: "2026-07-22T06:01:31.848Z",
  to: "2026-09-30T17:00:00Z", // 2026-10-01 00:00 local, exclusive
  cadenceSeconds: 600,
  seed: 20260722,
};

// ── Deterministic smooth noise ───────────────────────────────────────────────
function hashUnit(seed, channel, index) {
  const h = crypto.createHash("sha256").update(`${seed}:${channel}:${index}`).digest();
  return h.readUInt32BE(0) / 0x100000000 * 2 - 1; // [-1, 1)
}

/** Cosine-interpolated value noise: continuous in x, period-free, reproducible from the seed. */
function valueNoise(seed, channel, x) {
  const i = Math.floor(x);
  const t = (1 - Math.cos((x - i) * Math.PI)) / 2;
  return hashUnit(seed, channel, i) * (1 - t) + hashUnit(seed, channel, i + 1) * t;
}

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const round1 = v => Math.round(v * 10) / 10;
/** Smoothly approaches `limit` past `knee` (no flat plateau the way a hard clamp would create). */
const softUpper = (v, knee, limit) => (v <= knee ? v : knee + (limit - knee) * Math.tanh((v - knee) / (limit - knee)));
const softLower = (v, knee, limit) => -softUpper(-v, -knee, -limit);

/**
 * Plausible southern-Vietnam rainy-season air conditions (Jul–Sep): warm afternoons, humid nights.
 * Temperature and humidity are driven by the same smooth signals, so humidity falls as it warms.
 */
function syntheticReading(timestampMs, seed) {
  const hours = (timestampMs + TZ_OFFSET_MS) / HOUR_MS; // continuous local hours since epoch
  const localHour = ((hours % 24) + 24) % 24;
  const day = hours / 24;
  const meanTemp = 28.2 + 1.2 * valueNoise(seed, "mean", day / 3);      // slow multi-day drift
  const amplitude = 3.6 + 0.8 * valueNoise(seed, "amp", day / 2);
  const diurnal = Math.cos(((localHour - 14.5) / 24) * 2 * Math.PI);    // peak ~14:30, low ~02:30
  const rain = Math.max(0, valueNoise(seed, "rain", hours / 5)) ** 2;   // occasional smooth showers
  const afternoon = Math.exp(-(((localHour - 16) / 3) ** 2));           // showers favour late afternoon
  const shower = rain * afternoon;
  const jitter = 0.35 * valueNoise(seed, "jit", hours);                 // small hourly wobble
  const temperature = clamp(softLower(softUpper(meanTemp + amplitude * diurnal - 3.2 * shower + jitter, 34, 36), 24.5, 23), 23, 36);
  const humidity = clamp(softLower(softUpper(
    80 - 4.2 * (temperature - meanTemp) + 14 * shower + 3 * valueNoise(seed, "rh", hours / 2), 90, 97), 62, 55), 55, 97);
  return { temperature: round1(temperature), humidity: round1(humidity) };
}

function validateReading(reading) {
  const { temperature, humidity } = reading;
  if (!Number.isFinite(temperature) || !Number.isFinite(humidity) ||
      temperature < 15 || temperature > 45 || humidity < 0 || humidity > 100) {
    throw new Error("INVALID_SYNTHETIC_READING");
  }
  return reading;
}

// ── Planning (pure) ─────────────────────────────────────────────────────────
function parseOptions(env = process.env) {
  const options = {
    deviceId: (env.BACKFILL_DEVICE_ID || DEFAULTS.deviceId).trim(),
    from: new Date(env.BACKFILL_FROM || DEFAULTS.from),
    to: new Date(env.BACKFILL_TO || DEFAULTS.to),
    cadenceSeconds: Number(env.BACKFILL_CADENCE_SECONDS || DEFAULTS.cadenceSeconds),
    seed: Number(env.BACKFILL_SEED || DEFAULTS.seed),
  };
  if (!/^[A-Za-z0-9._-]{1,150}$/.test(options.deviceId)) throw new Error("INVALID_DEVICE_ID");
  if (!Number.isFinite(options.from.getTime()) || !Number.isFinite(options.to.getTime()) || options.from >= options.to) {
    throw new Error("INVALID_RANGE");
  }
  if (options.to.getTime() > Date.now()) throw new Error("FUTURE_RANGE_NOT_ALLOWED");
  if (options.to - options.from > 120 * DAY_MS) throw new Error("RANGE_TOO_LONG");
  if (!Number.isInteger(options.cadenceSeconds) || options.cadenceSeconds < 60 || options.cadenceSeconds > 3600) {
    throw new Error("INVALID_CADENCE");
  }
  if (!Number.isSafeInteger(options.seed)) throw new Error("INVALID_SEED");
  return options;
}

/** Slots aligned to the cadence (e.g. :00/:10/:20 …), from (inclusive, rounded up) to (exclusive). */
function buildSlots(from, to, cadenceSeconds) {
  const step = cadenceSeconds * 1000;
  const slots = [];
  for (let t = Math.ceil(from.getTime() / step) * step; t < to.getTime(); t += step) slots.push(t);
  return slots;
}

/**
 * A slot is skipped when a genuine reading lies within ±cadence/2 (real data always wins) or when a
 * synthetic row already exists at that exact timestamp (re-runs are no-ops).
 */
function planBackfill({ options, realTimestamps, syntheticTimestamps }) {
  const half = (options.cadenceSeconds * 1000) / 2;
  const real = [...realTimestamps].sort((a, b) => a - b);
  const synthetic = new Set(syntheticTimestamps);
  let r = 0;
  const rows = [];
  let skippedReal = 0;
  let skippedSynthetic = 0;
  for (const slot of buildSlots(options.from, options.to, options.cadenceSeconds)) {
    while (r < real.length && real[r] < slot - half) r++;
    if (r < real.length && real[r] < slot + half) { skippedReal++; continue; }
    if (synthetic.has(slot)) { skippedSynthetic++; continue; }
    rows.push({ timestamp: slot, ...validateReading(syntheticReading(slot, options.seed)) });
  }
  return { rows, skippedReal, skippedSynthetic };
}

function localDay(ms) {
  return new Date(ms + TZ_OFFSET_MS).toISOString().slice(0, 10);
}

function summarize(options, plan) {
  const perDay = new Map();
  for (const row of plan.rows) {
    const day = localDay(row.timestamp);
    perDay.set(day, (perDay.get(day) ?? 0) + 1);
  }
  const temps = plan.rows.map(r => r.temperature);
  const hums = plan.rows.map(r => r.humidity);
  const stat = values => values.length
    ? { min: Math.min(...values), max: Math.max(...values), mean: round1(values.reduce((a, b) => a + b, 0) / values.length) }
    : null;
  let maxStep = { temperature: 0, humidity: 0 };
  for (let i = 1; i < plan.rows.length; i++) {
    if (plan.rows[i].timestamp - plan.rows[i - 1].timestamp !== options.cadenceSeconds * 1000) continue;
    maxStep = {
      temperature: Math.max(maxStep.temperature, round1(Math.abs(plan.rows[i].temperature - plan.rows[i - 1].temperature))),
      humidity: Math.max(maxStep.humidity, round1(Math.abs(plan.rows[i].humidity - plan.rows[i - 1].humidity))),
    };
  }
  return {
    source: SOURCE_SYNTHETIC,
    method: METHOD,
    deviceId: options.deviceId,
    from: options.from.toISOString(),
    to: options.to.toISOString(),
    timezone: "Asia/Ho_Chi_Minh",
    cadenceSeconds: options.cadenceSeconds,
    seed: options.seed,
    slots: plan.rows.length + plan.skippedReal + plan.skippedSynthetic,
    skippedRealOverlap: plan.skippedReal,
    skippedExistingSynthetic: plan.skippedSynthetic,
    toInsert: plan.rows.length,
    days: perDay.size,
    perDay: Object.fromEntries(perDay),
    temperature: stat(temps),
    humidity: stat(hums),
    maxStepBetweenConsecutiveSlots: maxStep,
  };
}

// ── Database ────────────────────────────────────────────────────────────────
function telemetryTables(schema) {
  if (!/^[a-z_][a-z0-9_]{0,62}$/.test(schema)) throw new Error("INVALID_SCHEMA");
  return { telemetry: `"${schema}".telemetry`, runs: `"${schema}".telemetry_backfill_runs` };
}

async function hasProvenanceColumn(client, schema) {
  const result = await client.query(
    `SELECT 1 FROM information_schema.columns WHERE table_schema = $1 AND table_name = 'telemetry' AND column_name = 'source'`,
    [schema]);
  return result.rowCount > 0;
}

async function loadExisting(client, schema, options) {
  const { telemetry } = telemetryTables(schema);
  const half = (options.cadenceSeconds * 1000) / 2;
  const provenance = await hasProvenanceColumn(client, schema);
  const params = [options.deviceId, new Date(options.from.getTime() - half).toISOString(),
    new Date(options.to.getTime() + half).toISOString()];
  // Before the provenance migration every stored row is a genuine device reading.
  const real = await client.query(
    `SELECT "timestamp" FROM ${telemetry}
     WHERE LOWER(device_id) = LOWER($1) AND "timestamp" >= $2 AND "timestamp" < $3
       ${provenance ? `AND source = '${SOURCE_DEVICE}'` : ""}`, params);
  const synthetic = provenance ? await client.query(
    `SELECT "timestamp" FROM ${telemetry}
     WHERE LOWER(device_id) = LOWER($1) AND "timestamp" >= $2 AND "timestamp" < $3 AND source = '${SOURCE_SYNTHETIC}'`,
    params) : { rows: [] };
  return {
    provenance,
    realTimestamps: real.rows.map(r => new Date(r.timestamp).getTime()),
    syntheticTimestamps: synthetic.rows.map(r => new Date(r.timestamp).getTime()),
  };
}

async function dryRun({ client, schema, options }) {
  await client.query("BEGIN READ ONLY");
  try {
    const existing = await loadExisting(client, schema, options);
    const plan = planBackfill({ options, ...existing });
    return { mode: "DRY_RUN", writes: 0, provenanceSchemaPresent: existing.provenance,
      realReadingsInWindow: existing.realTimestamps.length, ...summarize(options, plan) };
  } finally {
    await client.query("ROLLBACK");
  }
}

async function apply({ client, schema, options, confirm }) {
  const { telemetry, runs } = telemetryTables(schema);
  await ensureTelemetryProvenanceSchema(client, schema);
  await client.query("BEGIN");
  try {
    await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", [`${schema}:backfill:${options.deviceId}`]);
    const existing = await loadExisting(client, schema, options);
    const plan = planBackfill({ options, ...existing });
    if (String(plan.rows.length) !== String(confirm)) {
      throw new Error(`CONFIRM_MISMATCH: plan has ${plan.rows.length} rows; re-run the dry-run and confirm that number`);
    }
    const runId = crypto.randomUUID();
    await client.query(
      `INSERT INTO ${runs} (id, device_id, range_from, range_to, cadence_seconds, method, seed, planned_count)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [runId, options.deviceId, options.from.toISOString(), options.to.toISOString(), options.cadenceSeconds,
        METHOD, options.seed, plan.rows.length]);
    const half = options.cadenceSeconds / 2;
    let inserted = 0;
    for (let i = 0; i < plan.rows.length; i += BATCH_SIZE) {
      const batch = plan.rows.slice(i, i + BATCH_SIZE);
      const result = await client.query(
        `INSERT INTO ${telemetry} (device_id, temperature, humidity, "timestamp", received_at, source, backfill_run_id)
         SELECT $1::varchar, v.temperature, v.humidity, v.ts, CURRENT_TIMESTAMP, '${SOURCE_SYNTHETIC}', $2::uuid
         FROM unnest($3::timestamptz[], $4::numeric[], $5::numeric[]) AS v(ts, temperature, humidity)
         WHERE NOT EXISTS (
           SELECT 1 FROM ${telemetry} real
           WHERE LOWER(real.device_id) = LOWER($1::varchar) AND real.source = '${SOURCE_DEVICE}'
             AND real."timestamp" > v.ts - make_interval(secs => $6::double precision) AND real."timestamp" < v.ts + make_interval(secs => $6::double precision))
         ON CONFLICT (device_id, "timestamp") WHERE source = '${SOURCE_SYNTHETIC}' DO NOTHING`,
        [options.deviceId, runId, batch.map(r => new Date(r.timestamp).toISOString()),
          batch.map(r => r.temperature), batch.map(r => r.humidity), half]);
      inserted += result.rowCount;
    }
    await client.query(
      `UPDATE ${runs} SET inserted_count = $2, status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE id = $1`,
      [runId, inserted]);
    await client.query("COMMIT");
    return { mode: "APPLIED", runId, planned: plan.rows.length, inserted, ...summarize(options, plan), perDay: undefined };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  }
}

async function rollback({ client, schema, runId }) {
  if (!/^[0-9a-f-]{36}$/i.test(runId)) throw new Error("INVALID_RUN_ID");
  const { telemetry, runs } = telemetryTables(schema);
  await client.query("BEGIN");
  try {
    const run = await client.query(`SELECT status FROM ${runs} WHERE id = $1 FOR UPDATE`, [runId]);
    if (run.rowCount !== 1) throw new Error("RUN_NOT_FOUND");
    // The CHECK constraint guarantees genuine rows never carry a backfill_run_id.
    const deleted = await client.query(
      `DELETE FROM ${telemetry} WHERE backfill_run_id = $1 AND source = '${SOURCE_SYNTHETIC}'`, [runId]);
    await client.query(
      `UPDATE ${runs} SET status = 'ROLLED_BACK', rolled_back_at = CURRENT_TIMESTAMP WHERE id = $1`, [runId]);
    await client.query("COMMIT");
    return { mode: "ROLLED_BACK", runId, deleted: deleted.rowCount };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  }
}

async function main(env = process.env, dependencies = {}) {
  const { Pool } = dependencies.pg ?? require("pg");
  const config = dependencies.config ?? require("../src/config");
  const schema = config.postgres.schema;
  const writing = env.BACKFILL_APPLY === "true";
  if (writing && !["localhost", "127.0.0.1", "::1"].includes(config.postgres.host)) {
    throw new Error("WRITE_REQUIRES_LOCAL_DEV_DATABASE");
  }
  const pool = new Pool({ ...config.postgres, max: 1 });
  const client = await pool.connect();
  try {
    if (writing && env.BACKFILL_ROLLBACK_RUN_ID) return await rollback({ client, schema, runId: env.BACKFILL_ROLLBACK_RUN_ID });
    const options = parseOptions(env);
    if (writing) return await apply({ client, schema, options, confirm: env.BACKFILL_CONFIRM });
    return await dryRun({ client, schema, options });
  } finally {
    client.release();
    await pool.end();
  }
}

module.exports = {
  METHOD, DEFAULTS, syntheticReading, validateReading, parseOptions, buildSlots, planBackfill, summarize,
  localDay, dryRun, apply, rollback, main,
};

if (require.main === module) {
  main().then(result => console.log(JSON.stringify(result, null, 2))).catch(error => {
    // Never echo connection strings or credentials.
    console.error(`BACKFILL_BLOCKED: ${String(error.message).split("\n")[0].slice(0, 200)}`);
    process.exitCode = 1;
  });
}
