import { Injectable } from "@nestjs/common";
import { InjectModel } from "@nestjs/mongoose";
import { Model } from "mongoose";
import {
  ChatMessageDocument,
  ChatMessageMongoDocument
} from "./chat-message.schema";

export type PersistChatMessage = {
  roomId: string;
  senderId: string;
  content: string;
};

@Injectable()
export class ChatMessageService {
  constructor(
    @InjectModel(ChatMessageDocument.name)
    private readonly messageModel: Model<ChatMessageDocument>
  ) {}

  async create(message: PersistChatMessage): Promise<ChatMessageMongoDocument> {
    return this.messageModel.create({
      roomId: message.roomId.trim(),
      senderId: message.senderId.trim(),
      content: message.content.trim(),
      messageType: "TEXT",
      sentAt: new Date()
    });
  }
}
