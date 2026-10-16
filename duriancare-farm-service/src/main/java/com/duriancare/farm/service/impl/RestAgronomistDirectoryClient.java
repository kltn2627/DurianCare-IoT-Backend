package com.duriancare.farm.service.impl;

import com.duriancare.farm.dto.AgronomistSummaryResponse;
import com.duriancare.farm.service.AgronomistDirectoryClient;
import com.duriancare.farm.service.FarmInvalidRequestException;
import com.duriancare.farm.service.FarmNotFoundException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RestAgronomistDirectoryClient implements AgronomistDirectoryClient {

    private final RestClient restClient;
    private final String internalToken;

    public RestAgronomistDirectoryClient(
            RestClient.Builder restClientBuilder,
            @Value("${duriancare.auth-service.url:${AUTH_SERVICE_URL:http://localhost:8081}}") String authServiceUrl,
            @Value("${duriancare.internal.auth-token:${INTERNAL_SERVICE_TOKEN:local-internal-token}}")
            String internalToken) {
        this.restClient = restClientBuilder.baseUrl(authServiceUrl.replaceAll("/$", "")).build();
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    public List<AgronomistSummaryResponse> search(String query) {
        String uri = UriComponentsBuilder.fromPath("/internal/v1/auth/agronomists")
                .queryParamIfPresent("query", java.util.Optional.ofNullable(query).filter(value -> !value.isBlank()))
                .build()
                .toUriString();
        AgronomistSummaryResponse[] response = restClient.get()
                .uri(uri)
                .headers(this::addInternalHeaders)
                .retrieve()
                .body(AgronomistSummaryResponse[].class);
        return response == null ? List.of() : Arrays.asList(response);
    }

    @Override
    public AgronomistSummaryResponse getEligible(String agronomistId) {
        AgronomistSummaryResponse response = restClient.get()
                .uri("/internal/v1/auth/agronomists/{userId}", agronomistId)
                .headers(this::addInternalHeaders)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                    if (clientResponse.getStatusCode().value() == 404) {
                        throw new FarmNotFoundException("Agronomist was not found");
                    }
                    throw new FarmInvalidRequestException("Agronomist is not eligible for invitation");
                })
                .body(AgronomistSummaryResponse.class);
        if (response == null || !response.eligible()) {
            throw new FarmInvalidRequestException("Agronomist is not eligible for invitation");
        }
        return response;
    }

    private void addInternalHeaders(HttpHeaders headers) {
        if (!internalToken.isBlank()) {
            headers.set("X-Internal-Token", internalToken);
        }
    }
}
