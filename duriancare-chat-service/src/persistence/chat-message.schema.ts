import { Prop, Schema, SchemaFactory } from "@nestjs/mongoose";
import { HydratedDocument } from "mongoose";

export type ChatMessageMongoDocument =
  HydratedDocument<ChatMessageDocument>;

export type TreatmentStepPayload = {
  completed: boolean;
  day: number;
  task: string;
};

export type TreatmentRegimenPayload = {
  diagnosis?: string;
  expectedOutcome?: string;
  followUpDate?: string;
  steps: TreatmentStepPayload[];
  title: string;
};

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

  @Prop({ type: Object, default: null })
  payload?: TreatmentRegimenPayload | null;

  @Prop({ required: true, default: Date.now, index: true })
  sentAt!: Date;
}

export const ChatMessageSchema =
  SchemaFactory.createForClass(ChatMessageDocument);

ChatMessageSchema.index({ roomId: 1, sentAt: -1 });
