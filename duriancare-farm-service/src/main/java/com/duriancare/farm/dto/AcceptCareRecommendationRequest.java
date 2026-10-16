package com.duriancare.farm.dto;

import java.time.LocalDate;
import java.util.List;

public record AcceptCareRecommendationRequest(
        String treatment,
        LocalDate startDate,
        LocalDate followUpDate,
        List<RecommendedCareTaskRequest> tasks) {
}
