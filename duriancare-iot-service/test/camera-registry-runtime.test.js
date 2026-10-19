"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const express = require("express");
const { cameraOrigin, cameraView, createRegistrationRouter, cameraAccess } = require("../src/camera/registryRuntime");
const { resolveCameraUrl } = require("../src/camera/deviceService");

test("registry configuration needs no static IP and ignores legacy address unless explicitly enabled", () => {
  const savedFlag = process.env.CAMERA_LEGACY_ENABLED;
  const savedUrl = process.env.ESP32_CAMERA_URL;
  try {
    process.env.CAMERA_LEGACY_ENABLED = "false";
    process.env.ESP32_CAMERA_URL = "http://old-camera.local";
    const config = require("../src/config");
    assert.equal(config.getCameraConfig().esp32CameraUrl, null);
    process.env.CAMERA_LEGACY_ENABLED = "true";
    assert.equal(config.getCameraConfig().esp32CameraUrl, "http://old-camera.local");
    delete process.env.ESP32_CAMERA_URL;
    assert.equal(config.getCameraConfig().esp32CameraUrl, "");
  } finally {
    if (savedFlag === undefined) delete process.env.CAMERA_LEGACY_ENABLED; else process.env.CAMERA_LEGACY_ENABLED = savedFlag;
    if (savedUrl === undefined) delete process.env.ESP32_CAMERA_URL; else process.env.ESP32_CAMERA_URL = savedUrl;
  }
});

test("camera address validation rejects unsupported schemes, credentials, metadata and public destinations", () => {
  assert.deepEqual(cameraOrigin("http://10.2.3.4:81"), {ipAddress:"10.2.3.4",port:81,protocol:"http"});
  assert.equal(cameraOrigin("http://camera.local").ipAddress, "camera.local");
  for (const value of ["file:///capture", "http://localhost", "http://127.0.0.1", "http://169.254.169.254",
    "http://999.1.1.1", "http://user:pass@10.2.3.4", "http://10.2.3.4/path", "http://10.2.3.4?x=1"]) {
    assert.throws(() => cameraOrigin(value));
  }
});

test("read-time status expires heartbeat without deleting origin and endpoint version changes with IP", async () => {
  const now = Date.now();
  const row = { device_id: "fixture", ip_address: "10.2.3.4", port: 80, protocol: "http", online: true,
    last_seen: new Date(now).toISOString() };
  assert.equal(cameraView(row, now).status, "ONLINE");
  assert.equal(cameraView({...row,last_seen:new Date(now+1000).toISOString()},now).status,"ONLINE");
  assert.equal(cameraView(row, now + 3600000).status, "OFFLINE");
  assert.equal(cameraView({...row,last_seen:null}).status, "UNKNOWN");
  assert.equal(cameraView(row).streamAvailable, false);
  assert.notEqual(cameraView(row).endpointVersion, cameraView({...row,ip_address:"10.2.3.5"}).endpointVersion);
  const pool = {query:async()=>({rows:[{...row,last_seen:new Date(now-3600000).toISOString()}]})};
  const resolved = await resolveCameraUrl(pool, "fixture", "fixture", "http://old.invalid");
  assert.equal(resolved.online, false);
  assert.equal(resolved.url, "http://10.2.3.4");
  assert.equal((await resolveCameraUrl({query:async()=>({rows:[]})},"fixture","missing")).url, null);
});

test("authenticated registration updates one bound record, retry is idempotent, old IP/foreign scope cannot overwrite", async () => {
  const key = crypto.randomBytes(32).toString("hex");
  const previous = process.env.CAMERA_DEVICE_BINDINGS;
  process.env.CAMERA_DEVICE_BINDINGS = JSON.stringify({fixture:{key,farmId:"farm"}});
  let row = {device_id:"fixture",farm_id:"farm",zone_id:"zone",protocol:"http",port:80,
    ip_address:"10.2.3.4",last_seen:null,online:false};
  let writes = 0;
  const pool = {query: async (sql, args) => {
    if (sql.startsWith("SELECT")) return {rows:[{...row}]};
    assert.match(sql,/farm_id = \$6/);
    assert.match(sql,/last_seen < \$4/);
    if (row.farm_id !== args[5] || row.last_seen && new Date(row.last_seen) >= new Date(args[3])) return {rows:[]};
    row = {...row,ip_address:args[1],port:args[2],last_seen:args[3],protocol:args[4],online:true};
    writes++;
    return {rows:[{...row}]};
  }};
  const app=express();app.use(express.json());app.use(createRegistrationRouter({pool,schema:"fixture"}));
  const server=app.listen(0,"127.0.0.1");await new Promise(resolve=>server.once("listening",resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  const post=(body,credential=key,route="/register",extra={})=>fetch(base+route,{method:"POST",
    headers:{"Content-Type":"application/json","X-Camera-Key":credential,...extra},body:JSON.stringify(body)});
  try {
    const announcedAt=Date.now()-100;
    const body={cameraId:"fixture",ipAddress:"10.2.3.5",announcedAt};
    assert.equal((await post(body,"wrong")).status,401);
    assert.equal((await post({...body,ipAddress:undefined},key,"/register",{"X-Forwarded-For":"10.2.3.5"})).status,400);
    assert.equal((await post({...body,farmId:"other"})).status,403);
    assert.equal((await post({...body,port:70000})).status,400);
    assert.equal((await post({...body,protocol:"ftp"})).status,400);
    assert.equal((await post({...body,ipAddress:"127.0.0.1"})).status,400);
    assert.equal(writes,0);
    const first=await post(body);assert.equal(first.status,200);
    assert.equal((await first.json()).device.status,"ONLINE");
    assert.equal((await post(body)).status,200);assert.equal(writes,1);
    const latest=await post({...body,ipAddress:"10.2.3.6",announcedAt:announcedAt+1},key,"/heartbeat");
    assert.equal(latest.status,200);
    assert.equal((await post(body)).status,409);assert.equal(row.ip_address,"10.2.3.6");
    row.farm_id="foreign";
    assert.equal((await post({...body,announcedAt:announcedAt+2})).status,403);
  } finally {
    await new Promise(resolve=>server.close(resolve));
    if(previous===undefined)delete process.env.CAMERA_DEVICE_BINDINGS;else process.env.CAMERA_DEVICE_BINDINGS=previous;
  }
});

test("UI reads require identity and farm scope, filter list and retain offline record", async () => {
  const row={device_id:"fixture",farm_id:"farm",online:true,last_seen:"2000-01-01T00:00:00Z",
    protocol:"http",ip_address:"10.2.3.4",port:80};
  const app=express();
  app.use(cameraAccess({pool:{query:async()=>({rows:[row]})},schema:"fixture",
    farmAccessClient:{canReadIot:async actor=>actor.userId==="owner",canManageIot:async()=>false}}));
  app.get("/devices/:id",(req,res)=>res.json(req.cameraDevice));
  const server=app.listen(0,"127.0.0.1");await new Promise(resolve=>server.once("listening",resolve));
  const url=`http://127.0.0.1:${server.address().port}`;
  const headers=id=>({"X-Auth-User-Id":id,"X-Auth-Role":"FARMER"});
  try {
    assert.equal((await fetch(url+"/devices/fixture")).status,401);
    assert.equal((await fetch(url+"/devices/fixture",{headers:headers("other")})).status,403);
    assert.deepEqual((await (await fetch(url+"/devices",{headers:headers("other")})).json()).devices,[]);
    const device=await (await fetch(url+"/devices/fixture",{headers:headers("owner")})).json();
    assert.equal(device.status,"OFFLINE");assert.equal(device.ip_address,row.ip_address);
  }finally{await new Promise(resolve=>server.close(resolve));}
});
