"use strict";

const crypto = require("node:crypto");

/**
 * Live camera view (PUSH mode), all in memory — never written to disk, the captures table or the AI service.
 *
 *  - Viewer:  POST /api/v1/camera/live/start  -> opens/renews a live session (LIVE_WINDOW_MS) and gets a
 *             short-lived token for  GET /api/v1/public/cameras/:id/live.mjpeg?token=
 *  - Camera:  learns about the session from its command poll ("live" field), then pushes frames to
 *             POST /api/v1/public/cameras/:id/live-frame; each reply says how long to keep going.
 *  - Stream:  every pushed frame is fanned out to the open MJPEG responses of that camera.
 * When viewers stop renewing, the session ends and the camera stops pushing within LIVE_WINDOW_MS.
 * Older "preview" capture commands also land here (setFrame) for the snapshot endpoint.
 */

const MAX_AGE_MS = 2 * 60_000;      // a frame older than this is not served as "latest"
const LIVE_WINDOW_MS = 30_000;      // one start/renew keeps the camera streaming this long
const TOKEN_TTL_MS = 2 * 60_000;    // time to open the stream with a token (an open stream is not cut)
const TOKEN_SECRET = process.env.CAMERA_LIVE_TOKEN_SECRET
  ? Buffer.from(process.env.CAMERA_LIVE_TOKEN_SECRET)
  : crypto.randomBytes(32);         // per-process: a restart only forces viewers to fetch a new token

const frames = new Map();       // deviceId -> { buffer, capturedAt: Date }
const sessions = new Map();     // deviceId -> live session end (epoch ms)
const subscribers = new Map();  // deviceId -> Set<(frame) => void>

function setFrame(deviceId, buffer, capturedAt) {
  const at = capturedAt instanceof Date ? capturedAt : new Date(capturedAt);
  const current = frames.get(deviceId);
  if (current && current.capturedAt > at) return false;  // out-of-order upload: keep the newer frame
  const frame = { buffer, capturedAt: at };
  frames.set(deviceId, frame);
  for (const listener of subscribers.get(deviceId) ?? []) {
    try { listener(frame); } catch { /* a broken viewer must not stop the others */ }
  }
  return true;
}

/** Newest frame of the camera if it is still fresh, else null. */
function getFrame(deviceId, now = Date.now()) {
  const frame = frames.get(deviceId);
  if (!frame) return null;
  if (now - frame.capturedAt.getTime() > MAX_AGE_MS) {
    frames.delete(deviceId);
    return null;
  }
  return frame;
}

/** Opens or renews the live session; returns its end (epoch ms). */
function startSession(deviceId, now = Date.now()) {
  const until = now + LIVE_WINDOW_MS;
  sessions.set(deviceId, Math.max(until, sessions.get(deviceId) ?? 0));
  return sessions.get(deviceId);
}

/** Milliseconds left in the camera's live session (0 = no session). */
function sessionRemainingMs(deviceId, now = Date.now()) {
  const until = sessions.get(deviceId);
  if (!until) return 0;
  if (until <= now) { sessions.delete(deviceId); return 0; }
  return until - now;
}

function subscribe(deviceId, listener) {
  if (!subscribers.has(deviceId)) subscribers.set(deviceId, new Set());
  subscribers.get(deviceId).add(listener);
  return () => {
    const set = subscribers.get(deviceId);
    if (!set) return;
    set.delete(listener);
    if (set.size === 0) subscribers.delete(deviceId);
  };
}

function viewerCount(deviceId) {
  return subscribers.get(deviceId)?.size ?? 0;
}

function sign(payload) {
  return crypto.createHmac("sha256", TOKEN_SECRET).update(payload).digest("base64url");
}

/** Short-lived token that lets a browser <img> open the MJPEG stream of one camera. */
function issueToken(deviceId, now = Date.now()) {
  const expiresAt = now + TOKEN_TTL_MS;
  return { token: `${expiresAt}.${sign(`${deviceId}.${expiresAt}`)}`, expiresAt };
}

function verifyToken(deviceId, token, now = Date.now()) {
  if (typeof token !== "string") return false;
  const [exp, mac] = token.split(".");
  const expiresAt = Number(exp);
  if (!Number.isSafeInteger(expiresAt) || expiresAt < now || !mac) return false;
  const expected = Buffer.from(sign(`${deviceId}.${expiresAt}`));
  const given = Buffer.from(mac);
  return given.length === expected.length && crypto.timingSafeEqual(given, expected);
}

function clear() {
  frames.clear();
  sessions.clear();
  subscribers.clear();
}

module.exports = {
  MAX_AGE_MS, LIVE_WINDOW_MS, TOKEN_TTL_MS,
  setFrame, getFrame, startSession, sessionRemainingMs, subscribe, viewerCount, issueToken, verifyToken, clear,
};
