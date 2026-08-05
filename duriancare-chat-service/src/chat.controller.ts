import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  Param,
  Patch,
  Post,
  Query,
  UnauthorizedException
} from "@nestjs/common";
import { AuthConnectionClient } from "./auth-connection.client";
import {
  ChatConversationService
} from "./persistence/chat-conversation.service";
import {
  ChatConversationMongoDocument,
  ChatConversationStatus
} from "./persistence/chat-conversation.schema";
import { ChatMessageService } from "./persistence/chat-message.service";
import { TreatmentRegimenPayload } from "./persistence/chat-message.schema";
import { ChatGateway } from "./realtime/chat.gateway";

type CreateConversationRequest = {
  peerUserId?: string;
  peerPhoneNumber?: string;
  engineerPhoneNumber?: string;
  farmerPhoneNumber?: string;
  farm?: string;
  zone?: string;
  location?: string;
  cropContext?: string;
  sensorContext?: string;
  initialMessage?: string;
};

type SendMessageRequest = {
  content?: string;
  image?: string | null;
};

type PublishRegimenRequest = {
  regimen: TreatmentRegimenPayload;
};

type UpdateStatusRequest = {
  status: ChatConversationStatus;
};

@Controller("api/chat/conversations")
export class ChatController {
  constructor(
    private readonly authConnectionClient: AuthConnectionClient,
    private readonly conversationService: ChatConversationService,
    private readonly chatMessageService: ChatMessageService,
    private readonly chatGateway: ChatGateway
  ) {}

  @Get()
  async listConversations(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Query("peerPhone") peerPhone?: string
  ) {
    const actor = resolveActor(userId, role);
    const acceptedConnections =
      await this.authConnectionClient.listAcceptedConnections(authorization);
    const currentProfile =
      await this.authConnectionClient.currentProfile(authorization);

    await Promise.all(
      acceptedConnections.map((connection) =>
        this.conversationService.upsertForConnection({
          connection,
          currentProfile,
          currentRole: actor.role,
          currentUserId: actor.userId
        })
      )
    );

    const acceptedPeerUserIds = new Set(
      acceptedConnections.map((connection) => connection.user.id)
    );
    const conversations = (
      await this.conversationService.listForUser(
        actor.userId,
        peerPhone
      )
    ).filter((conversation) => {
      const peerUserId =
        actor.role === "FARMER"
          ? conversation.engineerId
          : conversation.farmerId;
      return acceptedPeerUserIds.has(peerUserId);
    });
    const engineers = acceptedConnections
      .filter((connection) => connection.user.role === "ENGINEER")
      .map((connection) => ({
        initials: initials(connection.user.fullName),
        name: connection.user.fullName,
        phoneNumber: connection.user.phoneNumber ?? "",
        specialty: connection.user.specialization ?? "Kỹ thuật sầu riêng"
      }));

    return {
      conversations: await Promise.all(conversations.map((item) => this.toResponse(item, actor.role))),
      engineers
    };
  }

  @Post()
  async createConversation(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Body() body: CreateConversationRequest
  ) {
    const actor = resolveActor(userId, role);
    const peerPhoneNumber =
      actor.role === "FARMER"
        ? body.peerPhoneNumber ?? body.engineerPhoneNumber
        : body.peerPhoneNumber ?? body.farmerPhoneNumber;
    const [connection, currentProfile] = await Promise.all([
      this.authConnectionClient.findAcceptedConnection(
        authorization,
        body.peerUserId,
        peerPhoneNumber
      ),
      this.authConnectionClient.currentProfile(authorization)
    ]);
    const conversation = await this.conversationService.upsertForConnection({
      connection,
      currentProfile,
      currentRole: actor.role,
      currentUserId: actor.userId,
      farm: body.farm,
      zone: body.zone,
      location: body.location,
      cropContext: body.cropContext,
      sensorContext: body.sensorContext,
      initialMessage: body.initialMessage
    });

    const response = await this.toResponse(conversation, actor.role);
    this.chatGateway.emitConversationUpdated(response.id, response);
    return { conversation: response };
  }

  @Post(":conversationId/messages")
  async sendMessage(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Param("conversationId") conversationId: string,
    @Body() body: SendMessageRequest
  ) {
    const actor = resolveActor(userId, role);
    const existingConversation = await this.conversationService.getForParticipant(
      conversationId,
      actor.userId
    );
    await this.assertAcceptedConnection(authorization, actor, existingConversation);
    const conversation = await this.conversationService.addTextMessage(
      conversationId,
      actor,
      body
    );
    const response = await this.toResponse(conversation, actor.role);
    this.chatGateway.emitConversationUpdated(response.id, response);
    return { conversation: response };
  }

  @Post(":conversationId/regimens")
  async publishRegimen(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Param("conversationId") conversationId: string,
    @Body() body: PublishRegimenRequest
  ) {
    const actor = resolveActor(userId, role);
    const existingConversation = await this.conversationService.getForParticipant(
      conversationId,
      actor.userId
    );
    await this.assertAcceptedConnection(authorization, actor, existingConversation);
    const conversation = await this.conversationService.addRegimenMessage(
      conversationId,
      actor,
      body.regimen
    );
    const response = await this.toResponse(conversation, actor.role);
    this.chatGateway.emitConversationUpdated(response.id, response);
    return { conversation: response };
  }

  @Patch(":conversationId/status")
  async setStatus(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Param("conversationId") conversationId: string,
    @Body() body: UpdateStatusRequest
  ) {
    const actor = resolveActor(userId, role);
    const existingConversation = await this.conversationService.getForParticipant(
      conversationId,
      actor.userId
    );
    await this.assertAcceptedConnection(authorization, actor, existingConversation);
    const conversation = await this.conversationService.setStatus(
      conversationId,
      actor,
      body.status
    );
    const response = await this.toResponse(conversation, actor.role);
    this.chatGateway.emitConversationUpdated(response.id, response);
    return { conversation: response };
  }

  @Post(":conversationId/read")
  async markRead(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Param("conversationId") conversationId: string
  ) {
    const actor = resolveActor(userId, role);
    const existingConversation = await this.conversationService.getForParticipant(
      conversationId,
      actor.userId
    );
    await this.assertAcceptedConnection(authorization, actor, existingConversation);
    const conversation = await this.conversationService.markRead(conversationId, actor);
    const response = await this.toResponse(conversation, actor.role);
    this.chatGateway.emitConversationUpdated(response.id, response);
    return { conversation: response };
  }

  @Delete(":conversationId")
  async deleteConversation(
    @Headers("authorization") authorization: string,
    @Headers("x-auth-user-id") userId: string,
    @Headers("x-auth-role") role: string,
    @Param("conversationId") conversationId: string
  ) {
    const actor = resolveActor(userId, role);
    const existingConversation = await this.conversationService.getForParticipant(
      conversationId,
      actor.userId
    );
    await this.assertAcceptedConnection(authorization, actor, existingConversation);
    await this.conversationService.deleteForParticipant(conversationId, actor);
    this.chatGateway.emitConversationDeleted(conversationId, { id: conversationId });
    return { ok: true };
  }

  private async toResponse(
    conversation: ChatConversationMongoDocument,
    readerRole?: "FARMER" | "ENGINEER"
  ) {
    const messages = await this.chatMessageService.listRoomMessages(conversation.id, 100);
    const unreadCount = readerRole
      ? await this.chatMessageService.countUnreadForRole(
          conversation.id,
          readerRole,
          readerRole === "FARMER"
            ? conversation.farmerReadAt ?? null
            : conversation.engineerReadAt ?? null
        )
      : 0;
    return {
      id: conversation.id,
      farmer: {
        name: conversation.farmer.name,
        phoneNumber: conversation.farmer.phoneNumber ?? "",
        role: "FARMER",
        userId: conversation.farmerId
      },
      engineer: {
        name: conversation.engineer.name,
        phoneNumber: conversation.engineer.phoneNumber ?? "",
        role: "ENGINEER",
        userId: conversation.engineerId
      },
      farm: conversation.farm,
      zone: conversation.zone,
      location: conversation.location,
      status: conversation.status,
      activityLabel: conversation.lastMessage || "Chưa có tin nhắn",
      lastMessage: conversation.lastMessage || "Chưa có tin nhắn",
      lastMessageAt: conversation.lastMessageAt.toISOString(),
      cropContext: conversation.cropContext,
      sensorContext: conversation.sensorContext,
      createdAt: conversation.createdAt.toISOString(),
      updatedAt: conversation.updatedAt.toISOString(),
      unreadCount,
      messages: messages.map((message) => ({
        id: message.id,
        conversationId: conversation.id,
        sender: message.senderRole,
        content: message.content,
        sentAt: message.sentAt.toISOString(),
        type: message.messageType,
        image: message.messageType === "IMAGE" && message.payload && "image" in message.payload
          ? message.payload.image
          : null,
        regimen: message.messageType === "TREATMENT_REGIMEN" ? message.payload : null
      }))
    };
  }

  private async assertAcceptedConnection(
    authorization: string,
    actor: { userId: string; role: "FARMER" | "ENGINEER" },
    conversation: ChatConversationMongoDocument
  ) {
    const peerUserId =
      actor.role === "FARMER"
        ? conversation.engineerId
        : conversation.farmerId;
    await this.authConnectionClient.findAcceptedConnection(
      authorization,
      peerUserId
    );
  }
}

function resolveActor(userId: string, role: string): { userId: string; role: "FARMER" | "ENGINEER" } {
  const normalizedRole = role === "OWNER" ? "FARMER" : role;
  if (!userId || (normalizedRole !== "FARMER" && normalizedRole !== "ENGINEER")) {
    throw new UnauthorizedException("Farmer or engineer authentication is required");
  }
  return { userId, role: normalizedRole };
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}
