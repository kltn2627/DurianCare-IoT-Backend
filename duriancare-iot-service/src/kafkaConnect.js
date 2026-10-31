"use strict";

// Kafka stays optional, but a broker that is not ready at startup (for example after a
// Docker Desktop restart, where depends_on health conditions are not re-applied) must
// not disable event publishing for the life of the process. Retry with capped backoff.
function connectWithRetry({ connect, onReady, log = console, minMs = 5000, maxMs = 60000,
  setTimer = setTimeout, clearTimer = clearTimeout }) {
  let stopped = false;
  let timer = null;
  let delay = minMs;

  async function attempt(first) {
    if (stopped) return;
    try {
      await connect();
      if (stopped) return;
      onReady();
      log.log(first ? "[kafka] Producer connected" : "[kafka] Producer connected after retry");
    } catch (err) {
      if (stopped) return;
      if (first) log.warn("[kafka] Broker unavailable — retrying in background:", err.message);
      timer = setTimer(() => { timer = null; void attempt(false); }, delay);
      timer?.unref?.();
      delay = Math.min(delay * 2, maxMs);
    }
  }

  const started = attempt(true);
  return {
    started,
    stop() {
      stopped = true;
      if (timer) clearTimer(timer);
      timer = null;
    },
  };
}

module.exports = { connectWithRetry };
