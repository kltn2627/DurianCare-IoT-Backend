package com.duriancare.farm.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateTreeCarePlanRequest(
        String diagnosisId,
        String diseaseCode,
        String knowledgeArticleId,
        String treatment,
        @NotNull LocalDate startDate,
        LocalDate followUpDate) {
}
