import { Injectable } from "@nestjs/common";
import { InjectModel } from "@nestjs/mongoose";
import { Model } from "mongoose";
import {
  ChatMessageDocument,
  ChatMessageMongoDocument,
  TreatmentRegimenPayload,
  TreatmentStepPayload
} from "./chat-message.schema";

export type PersistChatMessage = {
  content?: string;
  messageType?: "TEXT" | "IMAGE" | "TREATMENT_REGIMEN";
  payload?: TreatmentRegimenPayload | null;
  roomId: string;
  senderId: string;
};

@Injectable()
export class ChatMessageService {
  constructor(
    @InjectModel(ChatMessageDocument.name)
    private readonly messageModel: Model<ChatMessageDocument>
  ) {}

  async create(message: PersistChatMessage): Promise<ChatMessageMongoDocument> {
    const messageType = message.messageType ?? "TEXT";
    const content = (message.content ?? "").trim();

    return this.messageModel.create({
      roomId: message.roomId.trim(),
      senderId: message.senderId.trim(),
      content,
      messageType,
      payload: message.payload ?? null,
      sentAt: new Date()
    });
  }

  async listRoomMessages(
    roomId: string,
    limit = 100
  ): Promise<ChatMessageMongoDocument[]> {
    return this.messageModel
      .find({ roomId: roomId.trim() })
      .sort({ sentAt: 1 })
      .limit(Math.min(Math.max(limit, 1), 200))
      .exec();
  }

  async updateRegimenStep(
    messageId: string,
    day: number,
    completed: boolean
  ): Promise<ChatMessageMongoDocument | null> {
    const message = await this.messageModel.findById(messageId).exec();
    if (!message?.payload?.steps) return null;

    message.payload.steps = message.payload.steps.map((step: TreatmentStepPayload) =>
      step.day === day ? { ...step, completed } : step
    );
    message.markModified("payload");
    return message.save();
  }
}
