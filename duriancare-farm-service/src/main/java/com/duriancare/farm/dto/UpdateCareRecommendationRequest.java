package com.duriancare.farm.dto;

import java.time.LocalDate;
import java.util.List;

public record UpdateCareRecommendationRequest(
        String treatment,
        LocalDate followUpDate,
        List<RecommendedCareTaskRequest> tasks) {
}
