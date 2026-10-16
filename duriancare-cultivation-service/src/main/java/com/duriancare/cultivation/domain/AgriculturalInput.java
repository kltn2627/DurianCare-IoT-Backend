package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "agricultural_inputs")
public record AgriculturalInput(
        @Id String id,
        @Indexed(unique = true) String code,
        @Indexed String productName,
        String tradeName,
        String manufacturer,
        String registrationNumber,
        InputCategory category,
        BiologicalLevel biologicalLevel,
        String formulation,
        String unit,
        List<ActiveIngredientSnapshot> activeIngredients,
        List<String> beneficialOrganisms,
        String recommendedDose,
        String maximumDose,
        Integer preHarvestIntervalDays,
        Integer reEntryIntervalHours,
        List<String> targetPests,
        List<String> applicableCrops,
        List<String> allowedMarketCodes,
        List<String> prohibitedMarketCodes,
        String labelDocumentUrl,
        List<String> certificateDocumentUrls,
        @Indexed InputStatus status,
        String verifiedBy,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt) {
}
