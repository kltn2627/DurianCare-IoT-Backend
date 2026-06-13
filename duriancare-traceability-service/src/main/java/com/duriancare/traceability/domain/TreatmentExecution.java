package com.duriancare.traceability.domain;

import java.time.Instant;

public record TreatmentExecution(
        String id,
        String performedByUserId,
        ExecutionStatus status,
        Instant performedAt,
        String actualDosage,
        String evidenceImageUrl,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
