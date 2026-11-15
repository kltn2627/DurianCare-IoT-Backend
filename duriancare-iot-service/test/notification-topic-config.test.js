// Every producer of in-app notification events must resolve to the topic notification-service consumes,
// for the docker-compose deployment and for plain local runs (no env). Reads the real compose file, Spring
// application.yml / config-server files and @Value / @KafkaListener expressions, so drift fails here.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { describe, it } = require("node:test");

const ROOT = path.resolve(__dirname, "..", "..");
const read = (rel) => fs.readFileSync(path.join(ROOT, rel), "utf8");
const DEFAULT_TOPIC = "duriancare.notification.events";

/** environment: block of one compose service as { KEY: rawValue }. */
function composeEnvironment(service) {
  const lines = read("infrastructure/docker-compose.yml").split(/\r?\n/);
  const start = lines.findIndex((line) => line === `  ${service}:`);
  assert.ok(start >= 0, `service ${service} missing from docker-compose.yml`);
  const env = {};
  let inEnv = false;
  for (const line of lines.slice(start + 1)) {
    if (/^ {2}\S/.test(line)) break;
    if (/^ {4}environment:\s*$/.test(line)) { inEnv = true; continue; }
    if (/^ {4}\S/.test(line)) { inEnv = false; continue; }
    const m = inEnv && line.match(/^ {6}([A-Z0-9_]+):\s*(.*)$/);
    if (m) env[m[1]] = m[2].trim();
  }
  return env;
}

/** docker compose interpolation of ${VAR}, ${VAR:-default} against the .env values. */
function interpolate(raw, dotenv) {
  return raw.replace(/\$\{([A-Z0-9_]+)(:-([^}]*))?\}/g, (_, name, hasDefault, fallback) => {
    const value = dotenv[name];
    if (hasDefault) return value === undefined || value === "" ? fallback : value;
    return value === undefined ? "" : value;
  });
}

/** Flattened `a.b.c: value` map of a simple Spring YAML file (block mappings only). */
function flattenYaml(rel) {
  if (!fs.existsSync(path.join(ROOT, rel))) return {};
  const out = {};
  const stack = [];
  for (const line of read(rel).split(/\r?\n/)) {
    if (!line.trim() || line.trim().startsWith("#") || line.trim().startsWith("-")) continue;
    const m = line.match(/^( *)([A-Za-z0-9_.-]+):\s*(.*)$/);
    if (!m) continue;
    const depth = m[1].length / 2;
    stack.length = depth;
    stack[depth] = m[2];
    if (m[3] !== "") out[stack.join(".")] = m[3].replace(/^["']|["']$/g, "");
  }
  return out;
}

function splitPlaceholder(expr) {
  const inner = expr.slice(2, -1);
  let depth = 0;
  for (let i = 0; i < inner.length; i += 1) {
    if (inner.startsWith("${", i)) depth += 1;
    else if (inner[i] === "}") depth -= 1;
    else if (inner[i] === ":" && depth === 0) return [inner.slice(0, i), inner.slice(i + 1)];
  }
  return [inner, undefined];
}

/** Spring placeholder resolution: properties first, then OS env (exact and relaxed name), then the default. */
function resolveSpring(expr, props, env) {
  if (!expr.startsWith("${")) return expr;
  const [key, fallback] = splitPlaceholder(expr);
  if (props[key] !== undefined) return resolveSpring(props[key], props, env);
  const relaxed = key.replace(/[.-]/g, "_").toUpperCase();
  if (env[key] !== undefined) return env[key];
  if (env[relaxed] !== undefined) return env[relaxed];
  if (fallback === undefined) throw new Error(`unresolvable ${expr}`);
  return resolveSpring(fallback, props, env);
}

function springProps(service) {
  return {
    ...flattenYaml(`${service}/src/main/resources/application.yml`),
    ...flattenYaml(`config-server/src/main/resources/config/${service}.yml`),
  };
}

function annotationExpression(rel, pattern) {
  const m = read(rel).match(pattern);
  assert.ok(m, `topic expression not found in ${rel}`);
  return m[1];
}

const SPRING_PRODUCERS = [
  ["duriancare-farm-service", "duriancare-farm-service/src/main/java/com/duriancare/farm/event/publisher/FarmNotificationEventPublisher.java"],
  ["duriancare-auth-service", "duriancare-auth-service/src/main/java/com/duriancare/auth/event/publisher/ConnectionNotificationPublisher.java"],
  ["duriancare-auth-service", "duriancare-auth-service/src/main/java/com/duriancare/auth/event/publisher/KnowledgeNotificationPublisher.java"],
];
const VALUE_PATTERN = /@Value\("(\$\{duriancare\.notification\.events-topic[^"]*)"\)/;
const LISTENER = "duriancare-notification-service/src/main/java/com/duriancare/notification/event/listener/NotificationEventListener.java";

function iotTopic(env) {
  const saved = { ...process.env };
  try {
    for (const key of ["KAFKA_ALERT_TOPIC", "NOTIFICATION_EVENTS_TOPIC"]) delete process.env[key];
    Object.assign(process.env, env);
    delete require.cache[require.resolve("../src/config")];
    return require("../src/config").notificationTopic;
  } finally {
    for (const key of ["KAFKA_ALERT_TOPIC", "NOTIFICATION_EVENTS_TOPIC"]) delete process.env[key];
    Object.assign(process.env, saved);
    delete require.cache[require.resolve("../src/config")];
  }
}

function resolveAll({ dotenv, compose }) {
  const envOf = (service) => (compose
    ? Object.fromEntries(Object.entries(composeEnvironment(service)).map(([k, v]) => [k, interpolate(v, dotenv)]))
    : {});
  const consumer = resolveSpring(annotationExpression(LISTENER, /topics = "([^"]+)"/),
    springProps("duriancare-notification-service"), envOf("duriancare-notification-service"));
  const producers = SPRING_PRODUCERS.map(([service, rel]) => [path.basename(rel, ".java"),
    resolveSpring(annotationExpression(rel, VALUE_PATTERN), springProps(service), envOf(service))]);
  producers.push(["iot-service config.notificationTopic", iotTopic(envOf("duriancare-iot-service"))]);
  return { consumer, producers };
}

describe("notification events topic: producers and consumer agree", () => {
  const scenarios = [
    ["docker compose with the current .env value", { compose: true, dotenv: { KAFKA_ALERT_TOPIC: "iot.alert.email" } }],
    ["docker compose with KAFKA_ALERT_TOPIC unset", { compose: true, dotenv: {} }],
    ["docker compose with a custom topic", { compose: true, dotenv: { KAFKA_ALERT_TOPIC: "env.custom.alerts" } }],
    ["local run without any environment", { compose: false, dotenv: {} }],
  ];
  for (const [name, scenario] of scenarios) {
    it(name, () => {
      const { consumer, producers } = resolveAll(scenario);
      assert.ok(consumer, "consumer topic must not be empty");
      for (const [producer, topic] of producers) {
        assert.equal(topic, consumer, `${producer} publishes to ${topic}, notification-service listens on ${consumer}`);
      }
    });
  }

  it("unset .env falls back to the code default instead of an empty topic", () => {
    assert.equal(resolveAll({ compose: true, dotenv: {} }).consumer, DEFAULT_TOPIC);
  });

  it("iot-service follows the consumer's precedence when both variables are set", () => {
    assert.equal(iotTopic({ KAFKA_ALERT_TOPIC: "a.topic", NOTIFICATION_EVENTS_TOPIC: "b.topic" }), "a.topic");
    assert.equal(iotTopic({ NOTIFICATION_EVENTS_TOPIC: "b.topic" }), "b.topic");
    assert.equal(iotTopic({}), DEFAULT_TOPIC);
  });
});
