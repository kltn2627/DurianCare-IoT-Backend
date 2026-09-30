import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer
} from "@nestjs/websockets";
import { WsException } from "@nestjs/websockets";
import { Server, Socket } from "socket.io";
import { AuthConnectionClient } from "../auth-connection.client";
import { ChatConversationService } from "../persistence/chat-conversation.service";
import { ChatMessageService } from "../persistence/chat-message.service";
import { TreatmentRegimenPayload } from "../persistence/chat-message.schema";
import { ChatSocketActor, ChatSocketAuthService } from "./chat-socket-auth.service";

type ChatMessage = {
  content?: string;
  messageType?: "TEXT" | "IMAGE" | "TREATMENT_REGIMEN";
  payload?: TreatmentRegimenPayload | { image?: string };
  roomId: string;
  senderId?: string;
  senderRole?: "FARMER" | "ENGINEER";
  sentAt?: string;
};

type TreatmentStepUpdate = {
  completed: boolean;
  day: number;
  messageId: string;
  roomId: string;
};

@WebSocketGateway({
  namespace: "/chat",
  cors: { origin: "*" }
})
export class ChatGateway {
  constructor(
    private readonly authConnectionClient: AuthConnectionClient,
    private readonly chatConversationService: ChatConversationService,
    private readonly chatMessageService: ChatMessageService,
    private readonly socketAuthService: ChatSocketAuthService
  ) {}

  @WebSocketServer()
  private readonly server!: Server;

  emitConversationUpdated(roomId: string, conversation: unknown): void {
    if (!roomId?.trim()) return;
    this.server.to(roomId).emit("conversation.updated", conversation);
  }

  emitConversationDeleted(roomId: string, payload: unknown): void {
    if (!roomId?.trim()) return;
    this.server.to(roomId).emit("conversation.deleted", payload);
  }

  handleConnection(client: Socket): void {
    try {
      client.data.actor = this.socketAuthService.authenticate(extractSocketCredential(client));
    } catch {
      client.disconnect(true);
    }
  }

  @SubscribeMessage("room.join")
  async joinRoom(
    @MessageBody() payload: string | { roomId?: string },
    @ConnectedSocket() client: Socket
  ): Promise<void> {
    const roomId = extractRoomId(payload);
    const actor = this.requireActor(client);
    await this.assertParticipantWithAcceptedConnection(roomId, actor);
    await client.join(roomId);
  }

  @SubscribeMessage("room.leave")
  async leaveRoom(
    @MessageBody() payload: string | { roomId?: string },
    @ConnectedSocket() client: Socket
  ): Promise<void> {
    const roomId = extractRoomId(payload);
    this.requireActor(client);
    await client.leave(roomId);
  }

  @SubscribeMessage("message.send")
  async publishMessage(
    @MessageBody() message: ChatMessage,
    @ConnectedSocket() client: Socket
  ): Promise<void> {
    const actor = this.requireActor(client);
    const messageType = message.messageType ?? "TEXT";
    const hasText = Boolean(message.content?.trim());
    const imagePayload = imageFromPayload(message.payload);
    const regimenPayload = regimenFromPayload(message.payload);
    const hasImage = messageType === "IMAGE" && Boolean(imagePayload);
    const hasRegimen =
      messageType === "TREATMENT_REGIMEN" &&
      Boolean(regimenPayload?.title?.trim()) &&
      Array.isArray(regimenPayload?.steps) &&
      regimenPayload.steps.length > 0;

    if (!message.roomId?.trim() || (!hasText && !hasImage && !hasRegimen)) {
      throw new WsException("roomId and message content are required");
    }
    const conversation = await this.assertParticipantWithAcceptedConnection(message.roomId, actor);
    if (messageType === "TREATMENT_REGIMEN" && actor.role !== "ENGINEER") {
      throw new WsException("Only engineers can publish treatment regimens");
    }

    try {
      if (messageType === "TREATMENT_REGIMEN") {
        await this.chatConversationService.addRegimenMessage(
          conversation.id,
          actor,
          regimenPayload as TreatmentRegimenPayload
        );
      } else {
        await this.chatConversationService.addTextMessage(conversation.id, actor, {
          content: message.content,
          image: hasImage ? imagePayload : null
        });
      }
      const persistedMessage = await this.chatMessageService.latestRoomMessage(conversation.id);
      if (!persistedMessage) {
        throw new WsException("Unable to load persisted chat message");
      }
      this.server.to(message.roomId).emit("message.created", {
        id: persistedMessage.id,
        roomId: persistedMessage.roomId,
        senderId: persistedMessage.senderId,
        senderRole: persistedMessage.senderRole,
        content: persistedMessage.content,
        messageType: persistedMessage.messageType,
        payload: persistedMessage.payload,
        sentAt: persistedMessage.sentAt.toISOString()
      });
    } catch {
      throw new WsException("Unable to persist chat message");
    }
  }

  @SubscribeMessage("regimen.step.update")
  async updateTreatmentStep(
    @MessageBody() update: TreatmentStepUpdate,
    @ConnectedSocket() client: Socket
  ): Promise<void> {
    const actor = this.requireActor(client);
    if (!update.roomId?.trim() || !update.messageId?.trim() || !Number.isInteger(update.day)) {
      throw new WsException("roomId, messageId and day are required");
    }
    await this.assertParticipantWithAcceptedConnection(update.roomId, actor);

    const message = await this.chatMessageService.updateRegimenStep(
      update.roomId,
      update.messageId,
      update.day,
      Boolean(update.completed)
    );

    if (!message) {
      throw new WsException("Treatment regimen message was not found");
    }

    this.server.to(update.roomId).emit("regimen.step.updated", {
      id: message.id,
      payload: message.payload,
      roomId: message.roomId,
      sentAt: message.sentAt.toISOString()
    });
  }

  private requireActor(client: Socket): ChatSocketActor {
    const actor = client.data.actor as ChatSocketActor | undefined;
    if (!actor?.userId || !actor.role || !actor.authorization) {
      throw new WsException("Socket authentication is required");
    }
    return actor;
  }

  private async assertParticipantWithAcceptedConnection(roomId: string, actor: ChatSocketActor) {
    if (!roomId?.trim()) {
      throw new WsException("roomId is required");
    }
    try {
      const conversation = await this.chatConversationService.getForParticipant(
        roomId,
        actor.userId
      );
      const peerUserId = actor.role === "FARMER" ? conversation.engineerId : conversation.farmerId;
      await this.authConnectionClient.findAcceptedConnection(actor.authorization, peerUserId);
      return conversation;
    } catch {
      throw new WsException("Chat socket access is denied");
    }
  }
}

function extractRoomId(payload: string | { roomId?: string }) {
  const roomId = typeof payload === "string" ? payload : payload?.roomId;
  if (!roomId?.trim()) {
    throw new WsException("roomId is required");
  }
  return roomId.trim();
}

function extractSocketCredential(client: Socket) {
  const auth = client.handshake.auth ?? {};
  return auth.token ??
    auth.authorization ??
    client.handshake.headers.authorization ??
    client.handshake.query.token;
}

function imageFromPayload(payload: ChatMessage["payload"]) {
  return payload && "image" in payload && typeof payload.image === "string"
    ? payload.image.trim()
    : "";
}

function regimenFromPayload(payload: ChatMessage["payload"]) {
  return payload && "title" in payload && "steps" in payload
    ? payload
    : null;
}
