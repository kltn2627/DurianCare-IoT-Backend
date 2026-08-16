import {
  ConnectedSocket,
  MessageBody,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer
} from "@nestjs/websockets";
import { WsException } from "@nestjs/websockets";
import { Server, Socket } from "socket.io";
import { ChatMessageService } from "../persistence/chat-message.service";
import { TreatmentRegimenPayload } from "../persistence/chat-message.schema";

type ChatMessage = {
  content?: string;
  messageType?: "TEXT" | "IMAGE" | "TREATMENT_REGIMEN";
  payload?: TreatmentRegimenPayload;
  roomId: string;
  senderId: string;
  senderRole: "FARMER" | "ENGINEER";
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
  constructor(private readonly chatMessageService: ChatMessageService) {}

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

  @SubscribeMessage("room.join")
  async joinRoom(
    @MessageBody() roomId: string,
    @ConnectedSocket() client: Socket
  ): Promise<void> {
    await client.join(roomId);
  }

  @SubscribeMessage("message.send")
  async publishMessage(@MessageBody() message: ChatMessage): Promise<void> {
    const messageType = message.messageType ?? "TEXT";
    const hasText = Boolean(message.content?.trim());
    const hasRegimen =
      messageType === "TREATMENT_REGIMEN" &&
      Boolean(message.payload?.title?.trim()) &&
      Array.isArray(message.payload?.steps) &&
      message.payload.steps.length > 0;

    if (!message.roomId?.trim() || !message.senderId?.trim() || !message.senderRole || (!hasText && !hasRegimen)) {
      throw new WsException("roomId, senderId, senderRole and message content are required");
    }

    try {
      const persistedMessage = await this.chatMessageService.create(message);
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
    @MessageBody() update: TreatmentStepUpdate
  ): Promise<void> {
    if (!update.roomId?.trim() || !update.messageId?.trim() || !Number.isInteger(update.day)) {
      throw new WsException("roomId, messageId and day are required");
    }

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
}
