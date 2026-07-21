package com.duriancare.cultivation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotBehaviorTest {

    @Test
    void agriculturalInputSnapshotKeepsStableCopiesOfLists() {
        List<ActiveIngredientSnapshot> activeIngredients = new ArrayList<>();
        activeIngredients.add(new ActiveIngredientSnapshot("AI-001", "Azadirachtin", new BigDecimal("0.3"), "%"));
        List<String> allowedMarkets = new ArrayList<>(List.of("VN"));

        AgriculturalInputSnapshot snapshot = new AgriculturalInputSnapshot(
                "BIO-001",
                "Neem extract",
                "Neem A",
                "DurianCare",
                InputCategory.BOTANICAL_PRODUCT,
                BiologicalLevel.BIOLOGICAL,
                0,
                activeIngredients,
                allowedMarkets,
                List.of());

        activeIngredients.add(new ActiveIngredientSnapshot("AI-002", "Changed", BigDecimal.ONE, "%"));
        allowedMarkets.add("EU");

        assertThat(snapshot.activeIngredients()).hasSize(1);
        assertThat(snapshot.allowedMarketCodes()).containsExactly("VN");
        assertThatThrownBy(() -> snapshot.allowedMarketCodes().add("JP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void activityInputUsageKeepsStableActiveIngredientSnapshot() {
        List<ActiveIngredientSnapshot> activeIngredients = new ArrayList<>();
        activeIngredients.add(new ActiveIngredientSnapshot("AI-001", "Copper", new BigDecimal("50"), "%"));

        ActivityInputUsage usage = new ActivityInputUsage(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                activeIngredients,
                null,
                null);

        activeIngredients.clear();

        assertThat(usage.activeIngredientSnapshots()).hasSize(1);
        assertThatThrownBy(() -> usage.activeIngredientSnapshots().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
