package com.duriancare.farm.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Optional, persisted agronomy profile. A supplied profile replaces the previous profile as a whole. */
public record FarmZoneAgronomy(
        @Pattern(regexp = "DURIAN|OTHER") String cropType,
        @Size(max = 120) String variety,
        @PastOrPresent LocalDate plantingDate,
        @PositiveOrZero Integer initialTreeCount,
        @PositiveOrZero Integer currentTreeCount,
        @Pattern(regexp = "SEEDLING|VEGETATIVE|FLOWERING|FRUITING|PRODUCTIVE|RENOVATION") String growthStage,
        @Size(max = 240) String seedSource,
        @Size(max = 120) String plantingDistance,
        @Size(max = 240) String soilType,
        @Size(max = 240) String waterSource,
        @Pattern(regexp = "DRIP|SPRINKLER|FLOOD|MANUAL|OTHER") String irrigationMethod,
        @Size(max = 1000) String drainageStatus,
        @Size(max = 1000) String locationDescription,
        @Size(max = 20) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String> imageUrls,
        @Size(max = 5000) String notes,
        @Size(max = 100) List<@NotNull @Valid HarvestHistory> harvestHistories) {

    @JsonIgnore
    @AssertTrue(message = "Current tree count cannot exceed initial tree count")
    public boolean isTreeCountValid() {
        return initialTreeCount == null || currentTreeCount == null || currentTreeCount <= initialTreeCount;
    }

    @JsonIgnore
    @AssertTrue(message = "Harvest history must not precede planting or be in the future")
    public boolean isHarvestTimelineValid() {
        if (harvestHistories == null) return true;
        LocalDate today = LocalDate.now();
        return harvestHistories.stream().allMatch(history -> history == null
                || ((history.harvestYear() == null || (history.harvestYear() <= today.getYear()
                    && (plantingDate == null || history.harvestYear() >= plantingDate.getYear())))
                && (history.harvestedAt() == null || (history.harvestedAt().isBefore(today.plusDays(1))
                    && (plantingDate == null || !history.harvestedAt().isBefore(plantingDate))
                    && (history.harvestYear() == null || history.harvestedAt().getYear() == history.harvestYear())))));
    }

    public record HarvestHistory(
            @Size(max = 120) String id,
            @NotBlank @Size(max = 160) String seasonName,
            @NotNull @Min(1900) Integer harvestYear,
            @PastOrPresent LocalDate harvestedAt,
            @PositiveOrZero BigDecimal yieldValue,
            @Pattern(regexp = "kg|tấn") String yieldUnit,
            @Size(max = 1000) String qualityNote,
            @Size(max = 2000) String notes) {
    }
}
