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

type ChatMessage = {
  roomId: string;
  senderId: string;
  content: string;
  sentAt?: string;
};

@WebSocketGateway({
  namespace: "/chat",
  cors: { origin: "*" }
})
export class ChatGateway {
  constructor(private readonly chatMessageService: ChatMessageService) {}

  @WebSocketServer()
  private readonly server!: Server;

  @SubscribeMessage("room.join")
  async joinRoom(
    @MessageBody() roomId: string,
    @ConnectedSocket() client: Socket
  ): Promise<void> {
    await client.join(roomId);
  }

  @SubscribeMessage("message.send")
  async publishMessage(@MessageBody() message: ChatMessage): Promise<void> {
    if (!message.roomId?.trim() || !message.senderId?.trim() || !message.content?.trim()) {
      throw new WsException("roomId, senderId and content are required");
    }

    try {
      const persistedMessage = await this.chatMessageService.create(message);
      this.server.to(message.roomId).emit("message.created", {
        id: persistedMessage.id,
        roomId: persistedMessage.roomId,
        senderId: persistedMessage.senderId,
        content: persistedMessage.content,
        sentAt: persistedMessage.sentAt.toISOString()
      });
    } catch {
      throw new WsException("Unable to persist chat message");
    }
  }
}
