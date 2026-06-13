package com.duriancare.traceability.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TreatmentStep(
        String id,
        int dayNumber,
        LocalDate scheduledDate,
        TreatmentActionType actionType,
        String title,
        String instructions,
        String productName,
        String dosage,
        Integer safetyIntervalDays,
        int sortOrder,
        List<TreatmentExecution> executions,
        Instant createdAt,
        Instant updatedAt) {
}
