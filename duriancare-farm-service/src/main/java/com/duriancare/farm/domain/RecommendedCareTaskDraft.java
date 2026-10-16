package com.duriancare.farm.domain;

import java.time.LocalDate;

public record RecommendedCareTaskDraft(
        String title,
        String description,
        LocalDate scheduledDate,
        Integer sortOrder) {
}
