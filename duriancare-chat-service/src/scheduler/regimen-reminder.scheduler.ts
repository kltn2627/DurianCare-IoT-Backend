import { Injectable, Logger } from "@nestjs/common";
import { Cron, CronExpression } from "@nestjs/schedule";

@Injectable()
export class RegimenReminderScheduler {
  private readonly logger = new Logger(RegimenReminderScheduler.name);

  @Cron(CronExpression.EVERY_DAY_AT_1AM)
  scheduleNextDayReminders(): void {
    this.logger.log("Scanning treatment schedules due within the next day");
  }
}
