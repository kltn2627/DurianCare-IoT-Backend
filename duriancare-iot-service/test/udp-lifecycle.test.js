const { test } = require("node:test");
const assert = require("node:assert/strict");
const { fork } = require("node:child_process");
const path = require("node:path");

for (const signal of ["SIGINT", "SIGTERM"]) test(`${signal} handler stops discovery once before HTTP and DB shutdown (child fixture)`, async () => {
  const child = fork(path.join(__dirname, "../src/main.js"), [], { silent: true,
    env: {...process.env,FIXTURE_DELAYED_TELEMETRY:"1"},
    execArgv: ["-r", path.join(__dirname, "../test-support/main-lifecycle.cjs")] });
  let output = ""; child.stdout.on("data", chunk => { output += chunk; });
  child.on("message", value => { if (value === "ready") child.send(signal); });
  let timeout;
  try {
    const exit = await Promise.race([
      new Promise(resolve => child.on("exit", code => resolve(code))),
      new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error("lifecycle fixture timed out")), 5000); }),
    ]);
    assert.equal(exit, 0);
    const steps = output.split(/\r?\n/).filter(line => line.startsWith("FIXTURE:")).map(line => line.slice(8));
    assert.ok(steps.indexOf("listen") < steps.indexOf("discovery-start"));
    assert.equal(steps.filter(step => step === "discovery-stop").length, 1);
    assert.ok(steps.indexOf("discovery-stop") < steps.indexOf("http-close"));
    assert.ok(steps.indexOf("http-close") < steps.indexOf("db-end"));
    assert.ok(steps.indexOf("camera-stop") < steps.indexOf("db-end"));
    assert.ok(steps.indexOf("mqtt-end") < steps.indexOf("kafka-end"));
    assert.ok(steps.indexOf("capture-drain") < steps.indexOf("kafka-end"));
    assert.ok(steps.indexOf("kafka-end") < steps.indexOf("db-end"));
    assert.equal(steps.filter(step=>step==="mqtt-work-start").length,1);
    assert.ok(steps.indexOf("mqtt-work-persisted") < steps.indexOf("kafka-end"));
  } finally { clearTimeout(timeout); if (child.exitCode === null) child.kill(); }
});

test("failed HTTP bind never starts discovery and exits unsuccessfully (child fixture)", async () => {
  const child = fork(path.join(__dirname, "../src/main.js"), [], { silent: true,
    env: { ...process.env, FIXTURE_LISTEN_ERROR: "1" },
    execArgv: ["-r", path.join(__dirname, "../test-support/main-lifecycle.cjs")] });
  let output = ""; child.stdout.on("data", chunk => { output += chunk; });
  let timeout;
  try {
    const code = await Promise.race([new Promise(resolve => child.on("exit", resolve)),
      new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error("failed-bind fixture timed out")), 5000); })]);
    assert.equal(code, 1);
    assert.equal(output.includes("FIXTURE:discovery-start"), false);
  } finally { clearTimeout(timeout); if (child.exitCode === null) child.kill(); }
});

test("stalled worker hits bounded shutdown deadline and exits unsuccessfully (accelerated child fixture)", async () => {
  const child = fork(path.join(__dirname,"../src/main.js"),[],{silent:true,
    env:{...process.env,FIXTURE_DELAYED_TELEMETRY:"1",FIXTURE_STUCK_SHUTDOWN:"1"},
    execArgv:["-r",path.join(__dirname,"../test-support/main-lifecycle.cjs")]});
  let stderr="",timer;
  child.stderr.on("data",chunk=>{stderr+=chunk;});
  child.on("message",value=>{if(value==="ready")child.send("SIGTERM");});
  try {
    const code=await Promise.race([new Promise(resolve=>child.on("exit",resolve)),
      new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error("Deadline fixture timed out")),5000);})]);
    assert.equal(code,1);assert.match(stderr,/shutdown deadline exceeded/);
  }finally{clearTimeout(timer);if(child.exitCode===null)child.kill();}
});
