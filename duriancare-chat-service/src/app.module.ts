import { Module } from "@nestjs/common";
import { MongooseModule } from "@nestjs/mongoose";
import { ScheduleModule } from "@nestjs/schedule";
import { HealthController } from "./health.controller";
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
      process.env.MONGO_URL || "mongodb://localhost:27017/duriancare_chat"
    ),
    MongooseModule.forFeature([
      { name: ChatMessageDocument.name, schema: ChatMessageSchema }
    ])
  ],
  controllers: [HealthController],
  providers: [ChatGateway, ChatMessageService, RegimenReminderScheduler]
})
export class AppModule {}
