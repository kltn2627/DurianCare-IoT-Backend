package com.duriancare.farm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GenerateTreesRequest(
        @NotNull @Min(1) @Max(500) Integer rows,
        @NotNull @Min(1) @Max(500) Integer treesPerRow,
        String variety,
        LocalDate plantedDate,
        String notes) {
}
