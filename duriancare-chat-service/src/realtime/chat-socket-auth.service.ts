import { createHmac, timingSafeEqual } from "node:crypto";
import { Injectable, UnauthorizedException } from "@nestjs/common";

export type ChatSocketActor = {
  authorization: string;
  role: "FARMER" | "ENGINEER";
  userId: string;
};

type JwtPayload = {
  exp?: number;
  iss?: string;
  role?: string;
  sub?: string;
  type?: string;
};

@Injectable()
export class ChatSocketAuthService {
  private readonly issuer = process.env.JWT_ISSUER || "duriancare-auth-service";
  private readonly secret =
    process.env.JWT_SECRET ||
    "duriancare-local-development-jwt-secret-2026-change-before-production-9f4c2b7a8e1d";

  authenticate(rawCredential: unknown): ChatSocketActor {
    const token = normalizeBearerToken(rawCredential);
    if (!token) {
      throw new UnauthorizedException("Socket authentication token is required");
    }

    const payload = this.verifyAccessToken(token);
    const role = payload.role === "FARMER" ? "FARMER" : payload.role === "ENGINEER" ? "ENGINEER" : null;
    if (!payload.sub || !role) {
      throw new UnauthorizedException("Farmer or engineer socket authentication is required");
    }

    return {
      authorization: `Bearer ${token}`,
      role,
      userId: payload.sub
    };
  }

  private verifyAccessToken(token: string): JwtPayload {
    const parts = token.split(".");
    if (parts.length !== 3) {
      throw new UnauthorizedException("Socket authentication token is malformed");
    }

    const [encodedHeader, encodedPayload, encodedSignature] = parts;
    const header = parseBase64UrlJson<{ alg?: string }>(encodedHeader);
    if (header.alg !== "HS512") {
      throw new UnauthorizedException("Socket authentication token algorithm is unsupported");
    }

    const expectedSignature = createHmac("sha512", this.secret)
      .update(`${encodedHeader}.${encodedPayload}`)
      .digest();
    const actualSignature = Buffer.from(encodedSignature, "base64url");
    if (
      actualSignature.length !== expectedSignature.length ||
      !timingSafeEqual(actualSignature, expectedSignature)
    ) {
      throw new UnauthorizedException("Socket authentication token signature is invalid");
    }

    const payload = parseBase64UrlJson<JwtPayload>(encodedPayload);
    if (payload.iss !== this.issuer) {
      throw new UnauthorizedException("Socket authentication token issuer is invalid");
    }
    if (payload.type !== "access") {
      throw new UnauthorizedException("Socket authentication token type is invalid");
    }
    if (!payload.exp || payload.exp <= Math.floor(Date.now() / 1000)) {
      throw new UnauthorizedException("Socket authentication token is expired");
    }
    return payload;
  }
}

function normalizeBearerToken(value: unknown) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  return trimmed.startsWith("Bearer ") ? trimmed.slice(7).trim() : trimmed;
}

function parseBase64UrlJson<T>(value: string): T {
  try {
    return JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as T;
  } catch {
    throw new UnauthorizedException("Socket authentication token payload is invalid");
  }
}

