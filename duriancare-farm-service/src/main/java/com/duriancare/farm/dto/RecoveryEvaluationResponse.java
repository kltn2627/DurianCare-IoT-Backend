package com.duriancare.farm.dto;

import com.duriancare.farm.domain.RecoveryOutcome;
import java.time.Instant;

public record RecoveryEvaluationResponse(
        String treeId,
        RecoveryOutcome outcome,
        String previousDiseaseCode,
        Double previousConfidence,
        String currentDiseaseCode,
        Double currentConfidence,
        String reason,
        Instant evaluatedAt) {
}
