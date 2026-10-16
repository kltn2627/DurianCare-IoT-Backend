package com.duriancare.cultivation.security;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FarmAccessClient {

    private final RestClient restClient;
    private final String internalToken;

    public FarmAccessClient(
            RestClient.Builder restClientBuilder,
            @Value("${duriancare.farm-service.url:${FARM_SERVICE_URL:http://localhost:8082}}") String farmServiceUrl,
            @Value("${duriancare.internal.auth-token:${INTERNAL_SERVICE_TOKEN:local-internal-token}}")
            String internalToken) {
        this.restClient = restClientBuilder.baseUrl(farmServiceUrl.replaceAll("/$", "")).build();
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    public boolean canAccess(
            CultivationActor actor,
            FarmPermissionType permission,
            String farmId,
            String cultivationAreaId) {
        FarmAccessResponse response = restClient.post()
                .uri("/internal/v1/farm-access/check")
                .headers(this::addInternalHeaders)
                .body(new FarmAccessRequest(actor.userId(), actor.role(), farmId, cultivationAreaId, permission.name()))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                    if (clientResponse.getStatusCode().value() == 401) {
                        throw new CultivationAuthenticationException("Authenticated user is required");
                    }
                    throw new CultivationAccessDeniedException("Farm access denied");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, clientResponse) -> {
                    throw new CultivationAccessDeniedException("Farm access service is unavailable");
                })
                .body(FarmAccessResponse.class);
        return response != null && response.allowed();
    }

    private void addInternalHeaders(HttpHeaders headers) {
        if (!internalToken.isBlank()) {
            headers.set("X-Internal-Token", internalToken);
        }
    }

    private record FarmAccessRequest(
            String userId,
            String role,
            String farmId,
            String cultivationAreaId,
            String permission) {
    }

    private record FarmAccessResponse(boolean allowed) {
    }
}
