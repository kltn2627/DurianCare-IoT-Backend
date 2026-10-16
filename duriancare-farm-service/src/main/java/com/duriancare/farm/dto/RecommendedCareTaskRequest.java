package com.duriancare.farm.dto;

import java.time.LocalDate;

public record RecommendedCareTaskRequest(
        String title,
        String description,
        LocalDate scheduledDate,
        Integer sortOrder) {
}
