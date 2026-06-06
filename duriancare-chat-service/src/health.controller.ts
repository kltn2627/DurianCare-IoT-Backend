import { Controller, Get } from "@nestjs/common";

@Controller("actuator")
export class HealthController {
  @Get("health")
  health(): { status: string; service: string } {
    return { status: "UP", service: "duriancare-chat-service" };
  }
}
