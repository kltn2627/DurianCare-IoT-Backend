package com.duriancare.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "activity_input_usages")
public record ActivityInputUsage(
        @Id String id,
        @Indexed String activityExecutionId,
        @Indexed String agriculturalInputId,
        AgriculturalInputSnapshot agriculturalInputSnapshot,
        String batchNumber,
        LocalDate expiryDate,
        BigDecimal quantityUsed,
        String quantityUnit,
        BigDecimal waterVolume,
        String waterVolumeUnit,
        BigDecimal concentration,
        String concentrationUnit,
        BigDecimal treatedArea,
        String areaUnit,
        List<ActiveIngredientSnapshot> activeIngredientSnapshots,
        LocalDate calculatedSafeHarvestDate,
        Instant createdAt) {

    public ActivityInputUsage {
        activeIngredientSnapshots = activeIngredientSnapshots == null
                ? List.of()
                : List.copyOf(activeIngredientSnapshots);
    }
}
