import { NestFactory } from "@nestjs/core";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

function loadBackendEnv(): void {
  const envPath = resolve(__dirname, "../../.env");
  if (!existsSync(envPath)) return;

  const lines = readFileSync(envPath, "utf8").split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const separatorIndex = trimmed.indexOf("=");
    if (separatorIndex < 1) continue;

    const key = trimmed.slice(0, separatorIndex).trim();
    const value = trimmed.slice(separatorIndex + 1).trim();
    process.env[key] ??= value;
  }
}

async function bootstrap(): Promise<void> {
  loadBackendEnv();
  const { AppModule } = await import("./app.module");
  const app = await NestFactory.create(AppModule);
  app.enableCors({ origin: true, credentials: true });
  await app.listen(Number(process.env.PORT || 3002));
}

void bootstrap();
