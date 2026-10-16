package com.duriancare.farm.service.impl;

import com.duriancare.farm.service.AiCareRecommendationClient;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestAiCareRecommendationClient implements AiCareRecommendationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAiCareRecommendationClient.class);

    private final RestClient restClient;

    public RestAiCareRecommendationClient(
            RestClient.Builder restClientBuilder,
            @Value("${duriancare.ai-service.url:${AI_SERVICE_URL:http://localhost:8000}}") String aiServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(aiServiceUrl.replaceAll("/$", "")).build();
    }

    @Override
    public Optional<Map<String, Object>> fetchRecommendation(String diseaseCode) {
        if (diseaseCode == null || diseaseCode.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/recommendations/{diseaseCode}", diseaseCode.trim())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                        throw new IllegalStateException("AI recommendation unavailable: " + clientResponse.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {
                    });
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return Optional.of(typed);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to fetch AI care recommendation for {}", diseaseCode, exception);
        }
        return Optional.empty();
    }
}
