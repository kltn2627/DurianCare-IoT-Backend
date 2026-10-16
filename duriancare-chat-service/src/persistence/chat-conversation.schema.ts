import { Prop, Schema, SchemaFactory } from "@nestjs/mongoose";
import { HydratedDocument } from "mongoose";

export type ChatParticipantRole = "FARMER" | "ENGINEER";
export type ChatConversationStatus = "WAITING" | "IN_PROGRESS" | "RESOLVED";

export type ChatParticipantSnapshot = {
  name: string;
  phoneNumber: string | null;
  role: ChatParticipantRole;
  userId: string;
};

export type ChatConversationMongoDocument =
  HydratedDocument<ChatConversationDocument>;

@Schema({
  collection: "conversations",
  timestamps: { createdAt: "createdAt", updatedAt: "updatedAt" }
})
export class ChatConversationDocument {
  @Prop({ required: true, unique: true, trim: true })
  pairKey!: string;

  @Prop({ required: true, index: true, trim: true })
  farmerId!: string;

  @Prop({ required: true, index: true, trim: true })
  engineerId!: string;

  @Prop({ required: true, type: Object })
  farmer!: ChatParticipantSnapshot;

  @Prop({ required: true, type: Object })
  engineer!: ChatParticipantSnapshot;

  @Prop({ required: true, default: "WAITING", index: true })
  status!: ChatConversationStatus;

  @Prop({ required: true, default: "Vườn sầu riêng" })
  farm!: string;

  @Prop({ required: true, default: "Khu canh tác" })
  zone!: string;

  @Prop({ required: true, default: "Chưa cập nhật khu vực" })
  location!: string;

  @Prop({ required: true, default: "" })
  cropContext!: string;

  @Prop({ required: true, default: "" })
  sensorContext!: string;

  @Prop({ required: true, default: "" })
  lastMessage!: string;

  @Prop({ required: true, default: Date.now, index: true })
  lastMessageAt!: Date;

  @Prop({ type: Date, default: null })
  farmerReadAt?: Date | null;

  @Prop({ type: Date, default: null })
  engineerReadAt?: Date | null;

  createdAt!: Date;

  updatedAt!: Date;
}

export const ChatConversationSchema =
  SchemaFactory.createForClass(ChatConversationDocument);

ChatConversationSchema.index({ farmerId: 1, updatedAt: -1 });
ChatConversationSchema.index({ engineerId: 1, updatedAt: -1 });
