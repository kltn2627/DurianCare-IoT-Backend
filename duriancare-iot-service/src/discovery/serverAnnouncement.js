"use strict";

// Server discovery contract (UDP 4210). Mirrored by the ESP32 sensor firmware parser.
//   v1:     {"service":"duriancare-iot","v":1,"ip":"<lan-ipv4>","port":<http-port>,"path":"/api/v1/sensors/data"}
//   legacy: DURIANCARE_SERVER:http://<lan-ipv4>:<http-port>   (kept only for already-flashed firmware)
const net = require("node:net");

const SERVICE = "duriancare-iot";
const VERSION = 1;
const DISCOVERY_PORT = 4210;
const SENSOR_PATH = "/api/v1/sensors/data";
const PATH_PATTERN = /^\/[A-Za-z0-9._~\/-]{0,63}$/;
const MAX_PACKET_BYTES = 256;

function validIPv4(ip) {
  if (!net.isIPv4(ip)) return false;
  const [a] = ip.split(".").map(Number);
  return a !== 0 && a !== 127 && a < 224 && ip !== "255.255.255.255";
}

function validPort(port) {
  return Number.isInteger(port) && port >= 1 && port <= 65535;
}

function buildServerAnnouncement({ ip, port, path = SENSOR_PATH }) {
  if (!validIPv4(ip) || !validPort(port) || !PATH_PATTERN.test(path) || path.includes("..")) {
    throw new Error("INVALID_SERVER_ANNOUNCEMENT");
  }
  return JSON.stringify({ service: SERVICE, v: VERSION, ip, port, path });
}

function buildLegacyAnnouncement({ ip, port }) {
  if (!validIPv4(ip) || !validPort(port)) throw new Error("INVALID_SERVER_ANNOUNCEMENT");
  return `DURIANCARE_SERVER:http://${ip}:${port}`;
}

/** Returns { ip, port, path, url } for a valid v1 packet, otherwise null (never throws). */
function parseServerAnnouncement(packet) {
  const text = Buffer.isBuffer(packet) ? packet.toString("utf8") : packet;
  if (typeof text !== "string" || Buffer.byteLength(text) > MAX_PACKET_BYTES) return null;
  let message;
  try { message = JSON.parse(text); } catch { return null; }
  if (!message || typeof message !== "object" || message.service !== SERVICE || message.v !== VERSION) return null;
  const { ip, port, path } = message;
  if (typeof ip !== "string" || !validIPv4(ip) || !validPort(port) ||
      typeof path !== "string" || !PATH_PATTERN.test(path) || path.includes("..")) return null;
  return { ip, port, path, url: `http://${ip}:${port}${path}` };
}

/** Directed broadcast of the interface subnet (e.g. 192.168.1.255), or null if it cannot be derived. */
function directedBroadcast(ip, netmask) {
  if (!validIPv4(ip) || !net.isIPv4(netmask ?? "")) return null;
  const a = ip.split(".").map(Number);
  const m = netmask.split(".").map(Number);
  const result = a.map((octet, i) => (octet & m[i]) | (~m[i] & 255));
  const text = result.join(".");
  return text === ip ? null : text;
}

module.exports = {
  SERVICE, VERSION, DISCOVERY_PORT, SENSOR_PATH,
  buildServerAnnouncement, buildLegacyAnnouncement, parseServerAnnouncement, directedBroadcast, validIPv4,
};
