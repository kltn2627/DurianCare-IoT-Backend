"use strict";

const express = require("express");

const DEFAULT_PORT = 1883;
const DEFAULT_TOPIC = "esp32/camera/register";

// The broker address must be the one devices reach on the LAN, not the
// in-cluster MQTT_URL (mqtt://emqx:1883), so it has its own explicit config.
function mqttDeviceConfig(env = process.env) {
  const host = (env.MQTT_PUBLIC_HOST || "").trim();
  const port = env.MQTT_PUBLIC_PORT === undefined || env.MQTT_PUBLIC_PORT === ""
    ? DEFAULT_PORT : Number(env.MQTT_PUBLIC_PORT);
  const topic = (env.MQTT_REGISTER_TOPIC || DEFAULT_TOPIC).trim();
  if (!/^[A-Za-z0-9.-]{1,253}$/.test(host) || !Number.isInteger(port) || port < 1 || port > 65535 ||
      !topic || topic.length > 200 || /[+#\s]/.test(topic)) {
    return null;
  }
  return { mqtt_server: host, mqtt_port: port, mqtt_topic: topic };
}

function createDeviceConfigRouter({ env = process.env } = {}) {
  const router = express.Router();

  // ── GET /mqtt-config ────────────────────────────────────────────────────
  // Public endpoint — devices fetch broker settings before connecting.
  router.get("/mqtt-config", (_req, res) => {
    const config = mqttDeviceConfig(env);
    if (!config) {
      return res.status(503).json({ error: "MQTT device configuration unavailable",
        code: "MQTT_CONFIG_UNAVAILABLE" });
    }
    res.setHeader("Cache-Control", "no-store");
    return res.json(config);
  });

  return router;
}

module.exports = { mqttDeviceConfig, createDeviceConfigRouter };
