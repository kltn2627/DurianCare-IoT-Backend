package com.duriancare.cultivation.domain;

import java.math.BigDecimal;

public record ActiveIngredientSnapshot(
        String code,
        String name,
        BigDecimal concentration,
        String concentrationUnit) {
}
