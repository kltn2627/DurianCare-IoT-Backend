"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const express = require("express");
const {Pool} = require("pg");
const {createRegistrationRouter, cameraAccess} = require("../src/camera/registryRuntime");
const {createCameraFixtureSchema} = require("../test-support/camera-schema.cjs");
const {upsertCamera, getCamera} = require("../src/camera/deviceService");

test("HTTP registry and ownership gates use real isolated PG; farm-level retry preserves scope", {
  skip: !process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI && "isolated PostgreSQL required"
}, async () => {
  const previous = process.env.CAMERA_DEVICE_BINDINGS;
  const key = crypto.randomBytes(32).toString("hex");
  const schema = "camera_fixture_" + crypto.randomUUID().replaceAll("-", "");
  const pool = new Pool({connectionString:process.env.DURIANCARE_CAMERA_FIXTURE_PG_URI});
  const client = await pool.connect(); let server;
  try {
    assert.equal((await client.query("SELECT current_database() AS name")).rows[0].name,"camera_registry_fixture");
    await client.query("BEGIN"); await createCameraFixtureSchema(client,schema);
    const db={query:(...args)=>client.query(...args)};
    await upsertCamera(db,schema,{deviceId:"fixture",farmId:"fixture-farm",ipAddress:"10.1.1.1"});
    process.env.CAMERA_DEVICE_BINDINGS=JSON.stringify({fixture:{key,farmId:"fixture-farm"},missing:{key,farmId:"fixture-farm"}});
    const app=express(); app.use(express.json());
    app.use("/public",createRegistrationRouter({pool:db,schema}));
    app.use("/private",cameraAccess({pool:db,schema,farmAccessClient:{
      canReadIot:async actor=>actor.userId==="owner",canManageIot:async()=>false
    }}));
    app.get("/private/devices/fixture",(req,res)=>res.json(req.cameraDevice));
    app.post("/private/capture-now",(_req,res)=>res.json({unexpected:true}));
    server=app.listen(0,"127.0.0.1"); await new Promise(r=>server.once("listening",r));
    const base=`http://127.0.0.1:${server.address().port}`;
    const body={cameraId:"fixture",ipAddress:"10.1.1.2",port:80,protocol:"http",announcedAt:Date.now()+100};
    const post=(value,credential=key,route="register")=>fetch(base+"/public/"+route,{method:"POST",
      headers:{"Content-Type":"application/json",...(credential===null?{}:{"X-Camera-Key":credential})},body:JSON.stringify(value)});
    assert.equal((await post(body,null)).status,401);
    assert.equal((await post(body,"wrong")).status,401);
    assert.equal((await post({...body,cameraId:"unknown"})).status,401);
    assert.equal((await post({...body,cameraId:"missing"})).status,404);
    for(const invalid of [{announcedAt:0},{announcedAt:Date.now()/1000},{ipAddress:"127.0.0.1"},{protocol:"ftp"},{port:70000}])
      assert.equal((await post({...body,...invalid})).status,400);
    for(const field of ["farmId","zoneId","treeId"])
      assert.equal((await post({...body,[field]:"spoof"})).status,403);
    const first=await post(body); assert.equal(first.status,200); assert.equal((await first.json()).device.zone_id,null);
    const before=await getCamera(db,schema,"fixture");
    assert.equal((await post(body)).status,200);
    assert.equal(new Date((await getCamera(db,schema,"fixture")).updated_at).getTime(),new Date(before.updated_at).getTime());
    assert.equal((await post({...body,announcedAt:body.announcedAt-1})).status,409);
    assert.equal((await post({...body,announcedAt:body.announcedAt+1},key,"heartbeat")).status,200);
    process.env.CAMERA_DEVICE_BINDINGS=JSON.stringify({fixture:{key,farmId:"foreign"}});
    assert.equal((await post(body)).status,403);
    process.env.CAMERA_DEVICE_BINDINGS=JSON.stringify({fixture:{key,farmId:"fixture-farm",zoneId:"foreign-zone"}});
    assert.equal((await post(body)).status,403);
    const headers={"X-Auth-User-Id":"owner","X-Auth-Role":"FARMER"};
    assert.equal((await fetch(base+"/private/devices/fixture")).status,401);
    assert.equal((await fetch(base+"/private/devices/fixture",{headers})).status,200);
    assert.equal((await fetch(base+"/private/devices/fixture",{headers:{...headers,"X-Auth-User-Id":"other"}})).status,403);
    assert.equal((await fetch(base+"/private/capture-now?device_id=fixture",{method:"POST",headers})).status,403);
  } finally {
    if(server)await new Promise(r=>server.close(r));
    await client.query("ROLLBACK");
    assert.equal((await client.query("SELECT 1 FROM information_schema.schemata WHERE schema_name=$1",[schema])).rowCount,0);
    client.release(); await pool.end();
    if(previous===undefined)delete process.env.CAMERA_DEVICE_BINDINGS;else process.env.CAMERA_DEVICE_BINDINGS=previous;
  }
});
