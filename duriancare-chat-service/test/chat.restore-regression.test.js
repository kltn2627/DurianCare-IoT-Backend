const assert = require("node:assert/strict");
const test = require("node:test");
const { ChatController } = require("../dist/chat.controller");
const { ChatMessageService } = require("../dist/persistence/chat-message.service");
const { ChatSocketAuthService } = require("../dist/realtime/chat-socket-auth.service");
const { createHmac } = require("node:crypto");

test("regimen updates scope the query to room and type and reject missing days", async () => {
  let query;
  let saves = 0;
  const message = {
    payload: { title: "Care", steps: [{ day: 1, completed: false, task: "Inspect" }] },
    markModified(field) { assert.equal(field, "payload"); },
    async save() { saves++; return this; },
  };
  const service = new ChatMessageService({
    findOne(filter) { query = filter; return { async exec() { return message; } }; },
  });
  assert.equal(await service.updateRegimenStep(" room-a ", "regimen-a", 2, true), null);
  assert.equal(saves, 0);
  assert.deepEqual(query, { _id: "regimen-a", messageType: "TREATMENT_REGIMEN", roomId: "room-a" });
  await service.updateRegimenStep("room-a", "regimen-a", 1, true);
  assert.equal(saves, 1);
  assert.equal(message.payload.steps[0].completed, true);
});

test("regimen from a different room cannot be updated", async () => {
  const service = new ChatMessageService({
    findOne(filter) {
      assert.equal(filter.roomId, "room-b");
      return { async exec() { return null; } };
    },
  });
  assert.equal(await service.updateRegimenStep("room-b", "room-a-regimen", 1, true), null);
});

test("conversation list excludes disconnected peers", async () => {
  const controller = new ChatController({
    async listAcceptedConnections() { return [{ user: { id: "engineer-b", role: "ENGINEER", fullName: "B" } }]; },
    async currentProfile() { return {}; },
  }, {
    async upsertForConnection() {},
    async listForUser() { return [{ id: "allowed", engineerId: "engineer-b" }, { id: "blocked", engineerId: "engineer-c" }]; },
  }, {}, {});
  controller.toResponse = async conversation => ({ id: conversation.id });
  const result = await controller.listConversations("Bearer test", "farmer-a", "FARMER");
  assert.deepEqual(result.conversations, [{ id: "allowed" }]);
});

test("delete response and event support both web and mobile contracts", async () => {
  const events = [];
  let deleted = false;
  const controller = new ChatController({
    async findAcceptedConnection(auth, peer) { assert.equal(peer, "engineer-b"); },
  }, {
    async getForParticipant(id, user) { assert.equal(user, "farmer-a"); return { id, engineerId: "engineer-b" }; },
    async deleteForParticipant() { deleted = true; },
  }, {}, {
    emitConversationDeleted(room, payload) { assert.equal(deleted, true); events.push({ room, payload }); },
  });
  assert.deepEqual(await controller.deleteConversation("Bearer test", "farmer-a", "FARMER", "room-a"),
    { ok: true, conversationId: "room-a" });
  assert.deepEqual(events, [{ room: "room-a", payload: { id: "room-a", conversationId: "room-a" } }]);
});

test("REST regimen update checks accepted connection before persistence", async () => {
  let writes = 0;
  const controller = new ChatController({
    async findAcceptedConnection() { throw new Error("disconnected"); },
  }, {
    async getForParticipant() { return { id: "room-a", engineerId: "engineer-b" }; },
  }, { async updateRegimenStep() { writes++; } }, {});
  await assert.rejects(() => controller.updateRegimenStep("Bearer test", "farmer-a", "FARMER", "room-a", "msg", "1", { completed: true }), /disconnected/);
  assert.equal(writes, 0);
});

test("socket auth rejects expired, wrong issuer, refresh and forged tokens", () => {
  const secret = "test-only-restore-regression-key-".repeat(3);
  const savedSecret = process.env.JWT_SECRET;
  process.env.JWT_SECRET = secret;
  const auth = new ChatSocketAuthService();
  if (savedSecret === undefined) delete process.env.JWT_SECRET; else process.env.JWT_SECRET = savedSecret;
  function sign(overrides, signingKey = secret) {
    const header = Buffer.from(JSON.stringify({ alg: "HS512" })).toString("base64url");
    const payload = Buffer.from(JSON.stringify({ sub: "farmer-a", role: "FARMER", type: "access",
      iss: process.env.JWT_ISSUER || "duriancare-auth-service", exp: Math.floor(Date.now() / 1000) + 3600, ...overrides })).toString("base64url");
    return `${header}.${payload}.${createHmac("sha512", signingKey).update(`${header}.${payload}`).digest("base64url")}`;
  }
  assert.equal(auth.authenticate(sign({})).userId, "farmer-a");
  for (const invalid of [sign({ exp: 1 }), sign({ iss: "attacker" }), sign({ type: "refresh" }), sign({}, "wrong-key")]) {
    assert.throws(() => auth.authenticate(invalid));
  }
});
