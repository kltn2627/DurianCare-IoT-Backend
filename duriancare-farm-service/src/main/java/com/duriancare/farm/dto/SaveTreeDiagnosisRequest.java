package com.duriancare.farm.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record SaveTreeDiagnosisRequest(
        @NotBlank String imageUrl,
        @NotBlank String diseaseCode,
        String diseaseName,
        Double confidence,
        Map<String, Object> boundingBox,
        String source) {
}
