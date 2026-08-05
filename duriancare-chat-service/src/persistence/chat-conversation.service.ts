import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from "@nestjs/common";
import { InjectModel } from "@nestjs/mongoose";
import { Model } from "mongoose";
import { AcceptedConnection, CurrentProfile } from "../auth-connection.client";
import {
  ChatConversationDocument,
  ChatConversationMongoDocument,
  ChatParticipantRole,
  ChatConversationStatus
} from "./chat-conversation.schema";
import { ChatMessageService } from "./chat-message.service";
import { TreatmentRegimenPayload } from "./chat-message.schema";

type Actor = {
  role: "FARMER" | "ENGINEER";
  userId: string;
};

type CreateConversationInput = {
  connection: AcceptedConnection;
  currentProfile: CurrentProfile;
  currentRole: "FARMER" | "ENGINEER";
  currentUserId: string;
  farm?: string;
  zone?: string;
  location?: string;
  cropContext?: string;
  sensorContext?: string;
  initialMessage?: string;
};

type SendMessageInput = {
  content?: string;
  image?: string | null;
};

@Injectable()
export class ChatConversationService {
  private static readonly MAX_TEXT_MESSAGE_LENGTH = 2000;

  constructor(
    @InjectModel(ChatConversationDocument.name)
    private readonly conversationModel: Model<ChatConversationDocument>,
    private readonly messageService: ChatMessageService
  ) {}

  async upsertForConnection(input: CreateConversationInput): Promise<ChatConversationMongoDocument> {
    const farmerId = input.currentRole === "FARMER" ? input.currentUserId : input.connection.user.id;
    const engineerId = input.currentRole === "ENGINEER" ? input.currentUserId : input.connection.user.id;
    const normalizedPairKey = pairKey(farmerId, engineerId);
    const now = new Date();
    const currentParticipant = {
      name: input.currentProfile.fullName || input.currentUserId,
      phoneNumber: input.currentProfile.phoneNumber ?? null,
      role: input.currentRole,
      userId: input.currentUserId
    };
    const peerParticipant = {
      name: input.connection.user.fullName,
      phoneNumber: input.connection.user.phoneNumber,
      role: normalizeParticipantRole(input.connection.user.role),
      userId: input.connection.user.id
    };
    const farmer = input.currentRole === "FARMER" ? currentParticipant : peerParticipant;
    const engineer = input.currentRole === "ENGINEER" ? currentParticipant : peerParticipant;

    const conversation = await this.conversationModel
      .findOneAndUpdate(
        { pairKey: normalizedPairKey },
        {
          $setOnInsert: {
            pairKey: normalizedPairKey,
            farmerId,
            engineerId,
            farmer,
            engineer,
            farm: input.farm?.trim() || "Vườn sầu riêng",
            zone: input.zone?.trim() || "Khu canh tác",
            location: input.location?.trim() || input.connection.user.region || "Chưa cập nhật khu vực",
            cropContext: input.cropContext?.trim() || "Kết nối tư vấn kỹ thuật qua DurianCare.",
            sensorContext: input.sensorContext?.trim() || "Chưa có snapshot IoT được chia sẻ.",
            lastMessage: "",
            lastMessageAt: now
          }
        },
        { new: true, upsert: true }
      )
      .exec();

    if (input.initialMessage?.trim()) {
      await this.addTextMessage(conversation.id, { userId: input.currentUserId, role: input.currentRole }, {
        content: input.initialMessage
      });
      return this.getForParticipant(conversation.id, input.currentUserId);
    }

    return conversation;
  }

  async listForUser(userId: string, peerQuery?: string): Promise<ChatConversationMongoDocument[]> {
    const normalizedQuery = peerQuery?.trim().toLowerCase();
    const conversations = await this.conversationModel
      .find({ $or: [{ farmerId: userId }, { engineerId: userId }] })
      .sort({ updatedAt: -1 })
      .exec();

    if (!normalizedQuery) return conversations;
    return conversations.filter((conversation) => {
      const peer = conversation.farmerId === userId ? conversation.engineer : conversation.farmer;
      return [
        peer.name,
        peer.phoneNumber ?? "",
        conversation.farm,
        conversation.zone,
        conversation.location
      ]
        .join(" ")
        .toLowerCase()
        .includes(normalizedQuery);
    });
  }

  async getForParticipant(conversationId: string, userId: string): Promise<ChatConversationMongoDocument> {
    const conversation = await this.conversationModel.findById(conversationId).exec();
    if (!conversation) {
      throw new NotFoundException("Chat conversation was not found");
    }
    if (conversation.farmerId !== userId && conversation.engineerId !== userId) {
      throw new ForbiddenException("Only conversation participants can access this chat");
    }
    return conversation;
  }

  async addTextMessage(
    conversationId: string,
    actor: Actor,
    message: SendMessageInput
  ): Promise<ChatConversationMongoDocument> {
    const conversation = await this.getForParticipant(conversationId, actor.userId);
    const content = (message.content ?? "").trim();
    if (!content && !message.image) {
      throw new ForbiddenException("Message content is required");
    }
    if (content.length > ChatConversationService.MAX_TEXT_MESSAGE_LENGTH) {
      throw new BadRequestException("Message content must not exceed 2000 characters");
    }

    await this.messageService.create({
      content,
      messageType: message.image ? "IMAGE" : "TEXT",
      payload: message.image ? { image: message.image } as never : null,
      roomId: conversation.id,
      senderId: actor.userId,
      senderRole: actor.role
    });
    conversation.lastMessage = content || "Đã gửi hình ảnh";
    conversation.lastMessageAt = new Date();
    await conversation.save();
    return conversation;
  }

  async addRegimenMessage(
    conversationId: string,
    actor: Actor,
    regimen: TreatmentRegimenPayload
  ): Promise<ChatConversationMongoDocument> {
    if (actor.role !== "ENGINEER") {
      throw new ForbiddenException("Only engineers can publish treatment regimens");
    }
    const conversation = await this.getForParticipant(conversationId, actor.userId);
    await this.messageService.create({
      content: regimen.title,
      messageType: "TREATMENT_REGIMEN",
      payload: regimen,
      roomId: conversation.id,
      senderId: actor.userId,
      senderRole: actor.role
    });
    conversation.lastMessage = `Phác đồ: ${regimen.title}`;
    conversation.lastMessageAt = new Date();
    conversation.status = "IN_PROGRESS";
    await conversation.save();
    return conversation;
  }

  async setStatus(
    conversationId: string,
    actor: Actor,
    status: ChatConversationStatus
  ): Promise<ChatConversationMongoDocument> {
    if (actor.role !== "ENGINEER") {
      throw new ForbiddenException("Only engineers can update chat status");
    }
    const conversation = await this.getForParticipant(conversationId, actor.userId);
    conversation.status = status;
    return conversation.save();
  }

  async markRead(
    conversationId: string,
    actor: Actor
  ): Promise<ChatConversationMongoDocument> {
    const conversation = await this.getForParticipant(conversationId, actor.userId);
    if (actor.role === "FARMER") {
      conversation.farmerReadAt = new Date();
    } else {
      conversation.engineerReadAt = new Date();
    }
    return conversation.save();
  }

  async deleteForParticipant(conversationId: string, actor: Actor): Promise<void> {
    const conversation = await this.getForParticipant(conversationId, actor.userId);
    await this.messageService.deleteRoomMessages(conversation.id);
    await this.conversationModel.deleteOne({ _id: conversation.id }).exec();
  }
}

function pairKey(firstUserId: string, secondUserId: string) {
  return [firstUserId, secondUserId].sort().join(":");
}

function normalizeParticipantRole(role: string): ChatParticipantRole {
  return role === "ENGINEER" ? "ENGINEER" : "FARMER";
}
