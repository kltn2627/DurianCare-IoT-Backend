import { Module } from "@nestjs/common";
import { ScheduleModule } from "@nestjs/schedule";
import { HealthController } from "./health.controller";
import { ChatGateway } from "./realtime/chat.gateway";
import { RegimenReminderScheduler } from "./scheduler/regimen-reminder.scheduler";

@Module({
  imports: [ScheduleModule.forRoot()],
  controllers: [HealthController],
  providers: [ChatGateway, RegimenReminderScheduler]
})
export class AppModule {}
