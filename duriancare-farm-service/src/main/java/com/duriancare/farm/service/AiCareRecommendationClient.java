package com.duriancare.farm.service;

import java.util.Map;
import java.util.Optional;

public interface AiCareRecommendationClient {

    Optional<Map<String, Object>> fetchRecommendation(String diseaseCode);
}
