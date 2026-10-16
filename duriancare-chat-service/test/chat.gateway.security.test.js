const assert = require("node:assert/strict");
const { createHmac } = require("node:crypto");
const test = require("node:test");

process.env.JWT_SECRET = process.env.JWT_SECRET || "duriancare-local-development-jwt-secret-2026-change-before-production-9f4c2b7a8e1d";
process.env.JWT_ISSUER = process.env.JWT_ISSUER || "duriancare-auth-service";

const { ChatGateway } = require("../dist/realtime/chat.gateway");
const { ChatSocketAuthService } = require("../dist/realtime/chat-socket-auth.service");

function token(userId, role, overrides = {}) {
  const header = { alg: "HS512", typ: "JWT" };
  const payload = {
    email: `${userId}@example.test`,
    exp: Math.floor(Date.now() / 1000) + 3600,
    iss: process.env.JWT_ISSUER,
    jti: `${userId}-token`,
    role,
    sub: userId,
    type: "access",
    ...overrides,
  };
  const encodedHeader = Buffer.from(JSON.stringify(header)).toString("base64url");
  const encodedPayload = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = createHmac("sha512", process.env.JWT_SECRET)
    .update(`${encodedHeader}.${encodedPayload}`)
    .digest("base64url");
  return `${encodedHeader}.${encodedPayload}.${signature}`;
}

function socketFor(userId, role) {
  return {
    data: {},
    disconnected: false,
    handshake: {
      auth: { token: token(userId, role) },
      headers: {},
      query: {},
    },
    joined: [],
    left: [],
    async join(roomId) {
      this.joined.push(roomId);
    },
    async leave(roomId) {
      this.left.push(roomId);
    },
    disconnect() {
      this.disconnected = true;
    },
  };
}

function createGateway({ accepted = true } = {}) {
  const conversation = {
    id: "conversation-ab",
    farmerId: "farmer-a",
    engineerId: "engineer-b",
  };
  const messages = [];
  const emitted = [];
  const authConnectionClient = {
    async findAcceptedConnection(authorization, peerUserId) {
      assert.match(authorization, /^Bearer /);
      if (!accepted) throw new Error("not accepted");
      return { id: "connection-1", status: "ACCEPTED", user: { id: peerUserId } };
    },
  };
  const chatConversationService = {
    async getForParticipant(conversationId, userId) {
      if (
        conversationId === conversation.id &&
        (userId === conversation.farmerId || userId === conversation.engineerId)
      ) {
        return conversation;
      }
      throw new Error("not participant");
    },
    async addTextMessage(conversationId, actor, body) {
      messages.push({
        id: `message-${messages.length + 1}`,
        roomId: conversationId,
        senderId: actor.userId,
        senderRole: actor.role,
        content: body.content ?? "",
        messageType: body.image ? "IMAGE" : "TEXT",
        payload: body.image ? { image: body.image } : null,
        sentAt: new Date(),
      });
      return conversation;
    },
    async addRegimenMessage(conversationId, actor, regimen) {
      if (actor.role !== "ENGINEER") throw new Error("engineer only");
      messages.push({
        id: `message-${messages.length + 1}`,
        roomId: conversationId,
        senderId: actor.userId,
        senderRole: actor.role,
        content: regimen.title,
        messageType: "TREATMENT_REGIMEN",
        payload: regimen,
        sentAt: new Date(),
      });
      return conversation;
    },
  };
  const chatMessageService = {
    async latestRoomMessage() {
      return messages[messages.length - 1] ?? null;
    },
    async updateRegimenStep(roomId, messageId, day, completed) {
      if (roomId !== conversation.id || messageId !== "regimen-1" || day !== 1) return null;
      return {
        id: messageId,
        payload: { title: "Regimen", steps: [{ day, task: "Task", completed }] },
        roomId,
        sentAt: new Date(),
      };
    },
  };
  const gateway = new ChatGateway(
    authConnectionClient,
    chatConversationService,
    chatMessageService,
    new ChatSocketAuthService(),
  );
  gateway.server = {
    emit() {
      throw new Error("global emit must not be used for private chat");
    },
    to(roomId) {
      return {
        emit(event, payload) {
          emitted.push({ event, payload, roomId });
        },
      };
    },
  };
  return { conversation, emitted, gateway, messages };
}

test("authenticated participant can join own conversation room", async () => {
  const { conversation, gateway } = createGateway();
  const socket = socketFor("farmer-a", "FARMER");
  gateway.handleConnection(socket);

  await gateway.joinRoom(conversation.id, socket);

  assert.deepEqual(socket.joined, [conversation.id]);
});

test("missing token disconnects socket and cannot join", async () => {
  const { conversation, gateway } = createGateway();
  const socket = socketFor("farmer-a", "FARMER");
  socket.handshake.auth = {};
  gateway.handleConnection(socket);

  assert.equal(socket.disconnected, true);
  await assert.rejects(() => gateway.joinRoom(conversation.id, socket), /Socket authentication is required/);
});

test("non-participant cannot join arbitrary conversation room", async () => {
  const { conversation, gateway } = createGateway();
  const socket = socketFor("user-c", "FARMER");
  gateway.handleConnection(socket);

  await assert.rejects(() => gateway.joinRoom(conversation.id, socket), /Chat socket access is denied/);
  assert.deepEqual(socket.joined, []);
});

test("non-participant cannot send into arbitrary conversation room", async () => {
  const { conversation, gateway, messages } = createGateway();
  const socket = socketFor("user-c", "FARMER");
  gateway.handleConnection(socket);

  await assert.rejects(
    () => gateway.publishMessage({
      content: "attack",
      roomId: conversation.id,
      senderId: "farmer-a",
      senderRole: "FARMER",
    }, socket),
    /Chat socket access is denied/,
  );
  assert.equal(messages.length, 0);
});

test("client-supplied sender is ignored for socket message send", async () => {
  const { conversation, gateway, messages } = createGateway();
  const socket = socketFor("farmer-a", "FARMER");
  gateway.handleConnection(socket);

  await gateway.publishMessage({
    content: "hello",
    roomId: conversation.id,
    senderId: "engineer-b",
    senderRole: "ENGINEER",
  }, socket);

  assert.equal(messages[0].senderId, "farmer-a");
  assert.equal(messages[0].senderRole, "FARMER");
});

test("pending or disconnected connection denies socket write", async () => {
  const { conversation, gateway, messages } = createGateway({ accepted: false });
  const socket = socketFor("farmer-a", "FARMER");
  gateway.handleConnection(socket);

  await assert.rejects(
    () => gateway.publishMessage({ content: "hello", roomId: conversation.id }, socket),
    /Chat socket access is denied/,
  );
  assert.equal(messages.length, 0);
});

test("farmer cannot publish regimen through socket", async () => {
  const { conversation, gateway } = createGateway();
  const socket = socketFor("farmer-a", "FARMER");
  gateway.handleConnection(socket);

  await assert.rejects(
    () => gateway.publishMessage({
      messageType: "TREATMENT_REGIMEN",
      payload: { title: "Regimen", steps: [{ completed: false, day: 1, task: "Task" }] },
      roomId: conversation.id,
    }, socket),
    /Only engineers can publish treatment regimens/,
  );
});

test("engineer can publish regimen and participant can update regimen step", async () => {
  const { conversation, emitted, gateway, messages } = createGateway();
  const engineerSocket = socketFor("engineer-b", "ENGINEER");
  gateway.handleConnection(engineerSocket);

  await gateway.publishMessage({
    messageType: "TREATMENT_REGIMEN",
    payload: { title: "Regimen", steps: [{ completed: false, day: 1, task: "Task" }] },
    roomId: conversation.id,
  }, engineerSocket);

  assert.equal(messages[0].senderId, "engineer-b");
  assert.equal(messages[0].messageType, "TREATMENT_REGIMEN");

  const farmerSocket = socketFor("farmer-a", "FARMER");
  gateway.handleConnection(farmerSocket);
  await gateway.updateTreatmentStep({
    completed: true,
    day: 1,
    messageId: "regimen-1",
    roomId: conversation.id,
  }, farmerSocket);

  assert.equal(emitted.at(-1).event, "regimen.step.updated");
  assert.equal(emitted.at(-1).roomId, conversation.id);
});

test("non-participant cannot update regimen step", async () => {
  const { conversation, gateway } = createGateway();
  const socket = socketFor("user-c", "ENGINEER");
  gateway.handleConnection(socket);

  await assert.rejects(
    () => gateway.updateTreatmentStep({
      completed: true,
      day: 1,
      messageId: "regimen-1",
      roomId: conversation.id,
    }, socket),
    /Chat socket access is denied/,
  );
});

test("private broadcasts target the conversation room only", () => {
  const { conversation, emitted, gateway } = createGateway();

  gateway.emitConversationUpdated(conversation.id, { id: conversation.id });

  assert.deepEqual(emitted, [
    { event: "conversation.updated", payload: { id: conversation.id }, roomId: conversation.id },
  ]);
});
