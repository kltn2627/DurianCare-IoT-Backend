import {
  Body,
  Controller,
  Get,
  NotFoundException,
  Param,
  Patch,
  Post,
  Query
} from "@nestjs/common";
import { ChatMessageService } from "./persistence/chat-message.service";
import { TreatmentRegimenPayload } from "./persistence/chat-message.schema";

type CreateMessageRequest = {
  content?: string;
  messageType?: "TEXT" | "IMAGE" | "TREATMENT_REGIMEN";
  payload?: TreatmentRegimenPayload;
  senderId: string;
};

type UpdateStepRequest = {
  completed: boolean;
};

@Controller("rooms/:roomId")
export class ChatController {
  constructor(private readonly chatMessageService: ChatMessageService) {}

  @Get("messages")
  async listMessages(
    @Param("roomId") roomId: string,
    @Query("limit") limit?: string
  ) {
    const messages = await this.chatMessageService.listRoomMessages(
      roomId,
      limit ? Number(limit) : 100
    );

    return messages.map((message) => ({
      id: message.id,
      content: message.content,
      messageType: message.messageType,
      payload: message.payload,
      roomId: message.roomId,
      senderId: message.senderId,
      sentAt: message.sentAt.toISOString()
    }));
  }

  @Post("messages")
  async createMessage(
    @Param("roomId") roomId: string,
    @Body() body: CreateMessageRequest
  ) {
    const message = await this.chatMessageService.create({
      content: body.content,
      messageType: body.messageType,
      payload: body.payload,
      roomId,
      senderId: body.senderId
    });

    return {
      id: message.id,
      content: message.content,
      messageType: message.messageType,
      payload: message.payload,
      roomId: message.roomId,
      senderId: message.senderId,
      sentAt: message.sentAt.toISOString()
    };
  }

  @Patch("regimens/:messageId/steps/:day")
  async updateRegimenStep(
    @Param("messageId") messageId: string,
    @Param("day") day: string,
    @Body() body: UpdateStepRequest
  ) {
    const message = await this.chatMessageService.updateRegimenStep(
      messageId,
      Number(day),
      Boolean(body.completed)
    );

    if (!message) {
      throw new NotFoundException("Treatment regimen message was not found");
    }

    return {
      id: message.id,
      payload: message.payload,
      roomId: message.roomId,
      sentAt: message.sentAt.toISOString()
    };
  }
}
