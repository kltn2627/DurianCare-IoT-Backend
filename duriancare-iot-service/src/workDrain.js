"use strict";
function createWorkDrain() {
  let stopped = false;
  const pending = new Set();
  return {
    run(work) {
      if (stopped) return Promise.resolve();
      const promise = Promise.resolve().then(work);
      pending.add(promise);
      promise.then(() => pending.delete(promise), () => pending.delete(promise));
      return promise;
    },
    stop() { stopped = true; },
    async drain() { await Promise.allSettled([...pending]); }
  };
}
module.exports = { createWorkDrain };
