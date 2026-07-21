package com.duriancare.cultivation.domain;

import java.util.List;

public record AgriculturalInputSnapshot(
        String code,
        String productName,
        String tradeName,
        String manufacturer,
        InputCategory category,
        BiologicalLevel biologicalLevel,
        Integer preHarvestIntervalDays,
        List<ActiveIngredientSnapshot> activeIngredients,
        List<String> allowedMarketCodes,
        List<String> prohibitedMarketCodes) {

    public AgriculturalInputSnapshot {
        activeIngredients = activeIngredients == null ? List.of() : List.copyOf(activeIngredients);
        allowedMarketCodes = allowedMarketCodes == null ? List.of() : List.copyOf(allowedMarketCodes);
        prohibitedMarketCodes = prohibitedMarketCodes == null ? List.of() : List.copyOf(prohibitedMarketCodes);
    }
}
