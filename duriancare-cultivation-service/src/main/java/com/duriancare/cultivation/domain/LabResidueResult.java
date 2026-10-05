package com.duriancare.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lab_residue_results")
public record LabResidueResult(
        @Id String id,
        @Indexed String labSampleId,
        @Indexed String activeIngredientCode,
        String activeIngredientName,
        BigDecimal measuredValue,
        String unit,
        BigDecimal detectionLimit,
        BigDecimal quantificationLimit,
        BigDecimal applicableMrl,
        String applicableMrlSource,
        @Indexed LabResultStatus resultStatus,
        String verifiedBy,
        Instant verifiedAt,
        Instant createdAt,
        String residueStandardId,
        String mrlUnit,
        LocalDate standardEffectiveFrom,
        LocalDate standardEffectiveTo,
        SourceType standardSourceType,
        String evaluationPolicyVersion) {

    public LabResidueResult(String id, String labSampleId, String activeIngredientCode, String activeIngredientName,
            BigDecimal measuredValue, String unit, BigDecimal detectionLimit, BigDecimal quantificationLimit,
            BigDecimal applicableMrl, String applicableMrlSource, LabResultStatus resultStatus,
            String verifiedBy, Instant verifiedAt, Instant createdAt) {
        this(id, labSampleId, activeIngredientCode, activeIngredientName, measuredValue, unit,
                detectionLimit, quantificationLimit, applicableMrl, applicableMrlSource, resultStatus,
                verifiedBy, verifiedAt, createdAt, null, null, null, null, null, null);
    }
}
