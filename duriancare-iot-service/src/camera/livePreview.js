"use strict";

/**
 * Live view frames (PUSH mode). While someone watches the camera page, the Web queues small `preview`
 * captures every few seconds. Those frames are not photos of record: they are kept in memory only
 * (newest per camera), never written to disk or the captures table and never sent to the AI service.
 * A restart simply drops them; the viewer falls back to the newest stored capture.
 */

const MAX_AGE_MS = 2 * 60_000;  // older live frames are ignored (the stored capture is shown instead)
const frames = new Map();        // deviceId → { buffer, capturedAt: Date }

function setFrame(deviceId, buffer, capturedAt) {
  const at = capturedAt instanceof Date ? capturedAt : new Date(capturedAt);
  const current = frames.get(deviceId);
  if (current && current.capturedAt >= at) return false;  // out-of-order upload: keep the newer frame
  frames.set(deviceId, { buffer, capturedAt: at });
  return true;
}

/** Newest live frame of the camera if it is still fresh, else null. */
function getFrame(deviceId, now = Date.now()) {
  const frame = frames.get(deviceId);
  if (!frame) return null;
  if (now - frame.capturedAt.getTime() > MAX_AGE_MS) {
    frames.delete(deviceId);
    return null;
  }
  return frame;
}

function clear() {
  frames.clear();
}

module.exports = { MAX_AGE_MS, setFrame, getFrame, clear };
