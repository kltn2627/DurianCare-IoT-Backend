import {
  ConnectedSocket,
  MessageBody,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer
} from "@nestjs/websockets";
import { Server, Socket } from "socket.io";

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
  publishMessage(@MessageBody() message: ChatMessage): void {
    this.server.to(message.roomId).emit("message.created", {
      ...message,
      sentAt: new Date().toISOString()
    });
  }
}
