import {
  ForbiddenException,
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException
} from "@nestjs/common";

type RelationStatus = "CONNECTED" | "REQUEST_SENT" | "REQUEST_RECEIVED" | "NONE" | "BLOCKED";

export type ConnectionUser = {
  id: string;
  fullName: string;
  phoneNumber: string | null;
  avatar: string | null;
  role: "FARMER" | "ENGINEER" | "OWNER";
  status: string;
  region: string | null;
  specialization: string | null;
  relationStatus: RelationStatus;
  connectionId: string | null;
};

export type AcceptedConnection = {
  id: string;
  status: "ACCEPTED";
  user: ConnectionUser;
};

export type CurrentProfile = {
  userId: string;
  fullName: string;
  phoneNumber: string | null;
  role: "FARMER" | "ENGINEER" | "OWNER";
  address?: string | null;
  provinceCity?: string | null;
};

type ConnectionPage = {
  items: AcceptedConnection[];
};

@Injectable()
export class AuthConnectionClient {
  private readonly authBaseUrls = [
    process.env.AUTH_SERVICE_URL,
    process.env.DURIANCARE_AUTH_SERVICE_URL,
    "http://localhost:8081",
    process.env.DURIANCARE_API_URL,
    "http://localhost:8080"
  ]
    .filter((value): value is string => Boolean(value?.trim()))
    .map((value) => value.replace(/\/$/, ""))
    .filter((value, index, values) => values.indexOf(value) === index);

  async listAcceptedConnections(authorization: string): Promise<AcceptedConnection[]> {
    if (!authorization?.startsWith("Bearer ")) {
      throw new UnauthorizedException("Bearer token is required");
    }

    const response = await this.fetchAuth("/api/connections?page=0&size=100", authorization);

    if (response.status === 401) {
      throw new UnauthorizedException("Authentication is required");
    }
    if (response.status === 403) {
      throw new ForbiddenException("Connection access is forbidden");
    }
    if (!response.ok) {
      throw new ServiceUnavailableException("Unable to verify accepted connections");
    }

    const payload = (await response.json()) as ConnectionPage;
    return Array.isArray(payload.items)
      ? payload.items.filter((item) => item.status === "ACCEPTED")
      : [];
  }

  async findAcceptedConnection(
    authorization: string,
    peerUserId?: string,
    peerPhoneNumber?: string
  ): Promise<AcceptedConnection> {
    const normalizedPhone = normalizePhone(peerPhoneNumber);
    const connections = await this.listAcceptedConnections(authorization);
    const connection = connections.find((item) => {
      if (peerUserId && item.user.id === peerUserId) return true;
      return Boolean(normalizedPhone) && normalizePhone(item.user.phoneNumber) === normalizedPhone;
    });

    if (!connection) {
      throw new ForbiddenException("Chat is only available for accepted farmer-engineer connections");
    }
    return connection;
  }

  async currentProfile(authorization: string): Promise<CurrentProfile> {
    if (!authorization?.startsWith("Bearer ")) {
      throw new UnauthorizedException("Bearer token is required");
    }
    const response = await this.fetchAuth("/api/users/me", authorization);
    if (response.status === 401) {
      throw new UnauthorizedException("Authentication is required");
    }
    if (!response.ok) {
      throw new ServiceUnavailableException("Unable to load current profile");
    }
    return (await response.json()) as CurrentProfile;
  }

  private async fetchAuth(path: string, authorization: string): Promise<Response> {
    let lastError: unknown;
    for (const baseUrl of this.authBaseUrls) {
      try {
        const response = await fetch(`${baseUrl}${path}`, {
          headers: { authorization },
          cache: "no-store"
        });
        if (response.status >= 500 && baseUrl !== this.authBaseUrls[this.authBaseUrls.length - 1]) {
          lastError = new Error(`Auth service returned ${response.status}`);
          continue;
        }
        return response;
      } catch (error) {
        lastError = error;
      }
    }
    throw new ServiceUnavailableException("Unable to connect to authentication service", {
      cause: lastError
    });
  }
}

function normalizePhone(phoneNumber?: string | null) {
  return (phoneNumber ?? "").replace(/[\s().-]/g, "").trim();
}
