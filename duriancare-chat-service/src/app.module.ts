import { Module } from "@nestjs/common";
import { MongooseModule } from "@nestjs/mongoose";
import { ScheduleModule } from "@nestjs/schedule";
import { ChatController } from "./chat.controller";
import { HealthController } from "./health.controller";
import { AuthConnectionClient } from "./auth-connection.client";
import {
  ChatConversationDocument,
  ChatConversationSchema
} from "./persistence/chat-conversation.schema";
import { ChatConversationService } from "./persistence/chat-conversation.service";
import {
  ChatMessageDocument,
  ChatMessageSchema
} from "./persistence/chat-message.schema";
import { ChatMessageService } from "./persistence/chat-message.service";
import { ChatGateway } from "./realtime/chat.gateway";
import { RegimenReminderScheduler } from "./scheduler/regimen-reminder.scheduler";

@Module({
  imports: [
    ScheduleModule.forRoot(),
    MongooseModule.forRoot(
      process.env.MONGO_URL || "mongodb://localhost:27018/duriancare_chat"
    ),
    MongooseModule.forFeature([
      { name: ChatConversationDocument.name, schema: ChatConversationSchema },
      { name: ChatMessageDocument.name, schema: ChatMessageSchema }
    ])
  ],
  controllers: [HealthController, ChatController],
  providers: [
    AuthConnectionClient,
    ChatConversationService,
    ChatGateway,
    ChatMessageService,
    RegimenReminderScheduler
  ]
})
export class AppModule {}
