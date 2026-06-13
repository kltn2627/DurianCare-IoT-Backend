import { Prop, Schema, SchemaFactory } from "@nestjs/mongoose";
import { HydratedDocument } from "mongoose";

export type ChatMessageMongoDocument =
  HydratedDocument<ChatMessageDocument>;

@Schema({
  collection: "messages",
  timestamps: { createdAt: "createdAt", updatedAt: "updatedAt" }
})
export class ChatMessageDocument {
  @Prop({ required: true, index: true, trim: true })
  roomId!: string;

  @Prop({ required: true, index: true, trim: true })
  senderId!: string;

  @Prop({ required: true, trim: true })
  content!: string;

  @Prop({ required: true, default: "TEXT" })
  messageType!: string;

  @Prop({ required: true, default: Date.now, index: true })
  sentAt!: Date;
}

export const ChatMessageSchema =
  SchemaFactory.createForClass(ChatMessageDocument);

ChatMessageSchema.index({ roomId: 1, sentAt: -1 });
