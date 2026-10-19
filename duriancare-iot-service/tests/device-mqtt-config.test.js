"use strict";

const assert = require("assert/strict");
const express = require("express");
const { test } = require("node:test");
const { mqttDeviceConfig, createDeviceConfigRouter } = require("../src/public/deviceConfigRoutes");

async function get(env) {
  const app = express();
  app.use("/api/v1/public/devices", createDeviceConfigRouter({ env }));
  const server = app.listen(0);
  try {
    const res = await fetch(`http://127.0.0.1:${server.address().port}/api/v1/public/devices/mqtt-config`);
    return { status: res.status, body: await res.json(), cache: res.headers.get("cache-control") };
  } finally {
    server.close();
  }
}

test("returns configured broker settings", async () => {
  const res = await get({ MQTT_PUBLIC_HOST: "192.168.1.100", MQTT_PUBLIC_PORT: "1883",
    MQTT_REGISTER_TOPIC: "esp32/camera/register" });
  assert.equal(res.status, 200);
  assert.deepEqual(res.body, { mqtt_server: "192.168.1.100", mqtt_port: 1883, mqtt_topic: "esp32/camera/register" });
  assert.equal(res.cache, "no-store");
});

test("defaults port and topic", () => {
  assert.deepEqual(mqttDeviceConfig({ MQTT_PUBLIC_HOST: "broker.local" }),
    { mqtt_server: "broker.local", mqtt_port: 1883, mqtt_topic: "esp32/camera/register" });
});

test("returns 503 when host is not configured", async () => {
  const res = await get({});
  assert.equal(res.status, 503);
  assert.equal(res.body.code, "MQTT_CONFIG_UNAVAILABLE");
});

test("rejects invalid port, host and wildcard topic", () => {
  assert.equal(mqttDeviceConfig({ MQTT_PUBLIC_HOST: "10.0.0.1", MQTT_PUBLIC_PORT: "70000" }), null);
  assert.equal(mqttDeviceConfig({ MQTT_PUBLIC_HOST: "mqtt://10.0.0.1" }), null);
  assert.equal(mqttDeviceConfig({ MQTT_PUBLIC_HOST: "10.0.0.1", MQTT_REGISTER_TOPIC: "esp32/+/register" }), null);
});
