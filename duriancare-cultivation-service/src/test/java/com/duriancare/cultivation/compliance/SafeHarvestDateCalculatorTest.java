package com.duriancare.cultivation.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.cultivation.domain.ActivityInputUsage;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafeHarvestDateCalculatorTest {

    private final SafeHarvestDateCalculator calculator = new SafeHarvestDateCalculator();

    @Test
    void calculatesSafeHarvestDateFromCompletionDateAndPhi() {
        LocalDate completedDate = LocalDate.of(2026, 7, 1);

        LocalDate result = calculator.safeHarvestDate(completedDate, 14);

        assertThat(result).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void returnsLatestSafeDateAcrossInputUsages() {
        ActivityInputUsage biological = usage(LocalDate.of(2026, 7, 3));
        ActivityInputUsage chemical = usage(LocalDate.of(2026, 7, 18));

        assertThat(calculator.earliestSafeHarvestDate(List.of(biological, chemical)))
                .contains(LocalDate.of(2026, 7, 18));
    }

    private ActivityInputUsage usage(LocalDate safeHarvestDate) {
        return new ActivityInputUsage(
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
                null,
                safeHarvestDate,
                null);
    }
}
