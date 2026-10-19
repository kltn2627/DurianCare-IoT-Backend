const { Pool } = require("pg");
const { createHash } = require("node:crypto");

const DEVICE_ID = process.env.IOT_SEED_DEVICE_ID || "demo-synthetic-sensors";
const START_AT = new Date(process.env.IOT_SEED_ACCOUNT_CREATED_AT || NaN);
const END_AT = new Date(process.env.IOT_SEED_TO || new Date().toISOString());

function seededRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function round(value, digits = 1) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function readingAt(timestamp, sequence) {
  const rand = seededRandom(20260601 + sequence * 97);
  const hour = (timestamp.getUTCHours() + 7) % 24;
  const daytime = hour >= 6 && hour < 18;
  const dayOfYear = Math.floor((timestamp.getTime() - START_AT.getTime()) / 86400000);
  const dailyWave = Math.sin(((hour - 6) / 24) * Math.PI * 2);
  const seasonalWave = Math.sin((dayOfYear / 35) * Math.PI * 2);
  const showerPulse = dayOfYear % 11 === 0 && hour >= 12 && hour <= 20;
  const irrigationPulse = dayOfYear % 4 === 0 && (hour === 6 || hour === 18);
  const dryDown = (dayOfYear % 4) * 6 + Math.max(0, hour - 6) * 0.75;

  const temperature = daytime
    ? clamp(31.5 + dailyWave * 2.6 + seasonalWave * 0.8 + rand() * 1.2, 29, 35)
    : clamp(25.5 + dailyWave * 1.1 + rand() * 0.9, 24, 27);
  const humidity = daytime
    ? clamp(68 - dailyWave * 6 - rand() * 4, 60, 75)
    : clamp(86 + rand() * 7, 80, 95);
  let soilMoisture = 82 - dryDown + seasonalWave * 4 + rand() * 5;
  if (irrigationPulse || showerPulse) {
    soilMoisture = 90 + rand() * 10;
  }
  soilMoisture = clamp(soilMoisture, 55, 100);

  return {
    deviceId: DEVICE_ID,
    humidity: round(humidity),
    soilMoisture: round(soilMoisture),
    temperature: round(temperature),
    timestamp: timestamp.toISOString(),
  };
}

function buildReadings() {
  if (!Number.isFinite(START_AT.getTime()) || !Number.isFinite(END_AT.getTime()) ||
      START_AT > END_AT || END_AT > new Date() || END_AT - START_AT > 5 * 366 * 86400000) {
    throw new Error("VALID_ACCOUNT_TIMESTAMP_AND_NON_FUTURE_RANGE_REQUIRED");
  }
  if (!/^demo-synthetic-[a-z0-9-]+$/.test(DEVICE_ID)) throw new Error("SYNTHETIC_DEVICE_REQUIRED");
  const readings = [];
  const cursor = new Date(Date.UTC(
    START_AT.getUTCFullYear(),
    START_AT.getUTCMonth(),
    START_AT.getUTCDate(),
  ));
  let sequence = 0;
  while (cursor.getTime() <= END_AT.getTime()) {
    const dayIndex = Math.floor((cursor.getTime() - START_AT.getTime()) / 86400000);
    const sampleHours = dayIndex % 5 === 0 ? [0, 6, 10, 14, 18, 22] : [2, 6, 10, 14, 18, 22];
    const todaysHours = dayIndex % 3 === 0 ? sampleHours.slice(1) : sampleHours;
    for (const hour of todaysHours) {
      const timestamp = new Date(cursor);
      timestamp.setUTCHours(hour, dayIndex % 2 === 0 ? 0 : 30, 0, 0);
      if (timestamp.getTime() < START_AT.getTime() || timestamp.getTime() > END_AT.getTime()) {
        continue;
      }
      readings.push(readingAt(timestamp, sequence));
      sequence += 1;
    }
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return readings;
}

async function seed(dependencies = {}) {
  const readings = buildReadings();
  const summary = { source: "DEMO_SYNTHETIC", mode: "DRY_RUN", candidates: readings.length,
    from: START_AT.toISOString(), to: END_AT.toISOString(), timezone: "Asia/Ho_Chi_Minh", writes: 0 };
  if (process.env.IOT_SEED_APPLY !== "true") {
    console.log(JSON.stringify(summary));
    return;
  }
  const schema = process.env.IOT_SEED_SCHEMA || "";
  const farmId = process.env.IOT_SEED_FARM_ID || "";
  const zoneId = process.env.IOT_SEED_ZONE_ID || "";
  if (process.env.IOT_SEED_ENVIRONMENT !== "test" || !process.env.IOT_SEED_FIXTURE_PG_URI ||
      !/^iot_demo_fixture_[a-z0-9_]{1,40}$/.test(schema) || !/^fixture-/.test(farmId) || !/^fixture-/.test(zoneId)) {
    throw new Error("EXPLICIT_ISOLATED_FIXTURE_REQUIRED");
  }
  const telemetryTable = `"${schema}".telemetry`;
  const PoolClass = dependencies.Pool || Pool;
  const pool = new PoolClass({ connectionString: process.env.IOT_SEED_FIXTURE_PG_URI });
  let client;
  let inserted = 0;
  try {
    client = await pool.connect();
    await client.query("BEGIN");
    const account = await client.query("SELECT created_at AT TIME ZONE 'UTC' AS created_at FROM duriancare_auth.users WHERE id=$1 AND role='FARMER' AND status='ACTIVE'", [process.env.IOT_SEED_OWNER_ID]);
    if (account.rows.length !== 1 || new Date(account.rows[0].created_at).getTime() !== START_AT.getTime()) throw new Error("ACCOUNT_TIMESTAMP_NOT_VERIFIED");
    const registry = await client.query(`SELECT name FROM "${schema}".iot_devices WHERE device_uid=$1 AND farm_id=$2 AND cultivation_area_id=$3 AND status='ACTIVE'`, [DEVICE_ID, farmId, zoneId]);
    if (registry.rows.length !== 1 || !registry.rows[0].name.startsWith("DEMO_SYNTHETIC")) throw new Error("FIXTURE_SCOPE_NOT_VERIFIED");
    await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", [`${schema}:${DEVICE_ID}`]);
    for (const reading of readings) {
      const result = await client.query(
        `INSERT INTO ${telemetryTable}
          (device_id, temperature, humidity, soil_moisture, "timestamp", received_at, id)
         SELECT $1, $2, $3, $4, $5, $5, $6
         WHERE NOT EXISTS (
           SELECT 1
           FROM ${telemetryTable}
           WHERE LOWER(device_id) = LOWER($1)
             AND "timestamp" = $5
         )`,
        [
          reading.deviceId,
          reading.temperature,
          reading.humidity,
          reading.soilMoisture,
          reading.timestamp,
          stableId(reading),
        ],
      );
      inserted += result.rowCount;
    }
    await client.query("COMMIT");
    console.log(JSON.stringify({
      source: "DEMO_SYNTHETIC", mode: "ISOLATED_FIXTURE",
      device_id: DEVICE_ID,
      from: START_AT.toISOString(),
      inserted,
      skipped_existing: readings.length - inserted,
      to: END_AT.toISOString(),
      total_candidates: readings.length,
    }, null, 2));
  } catch (error) {
    if (client) await client.query("ROLLBACK");
    throw error;
  } finally {
    client?.release();
    await pool.end();
  }
}

function stableId(reading) {
  const h = createHash("sha256").update(`${reading.deviceId}:${reading.timestamp}`).digest("hex");
  return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-a${h.slice(17,20)}-${h.slice(20,32)}`;
}

module.exports = { buildReadings, stableId, seed };
if (require.main === module) seed().catch(() => {
  console.error("SEED_BLOCKED: verify explicit fixture configuration and account timestamp locally.");
  process.exitCode = 1;
});
