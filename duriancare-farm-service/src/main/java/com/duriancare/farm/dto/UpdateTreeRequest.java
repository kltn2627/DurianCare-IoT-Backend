package com.duriancare.farm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTreeRequest(
        String nickname,
        String variety,
        String speciesId,
        LocalDate plantedDate,
        BigDecimal latitude,
        BigDecimal longitude,
        Double positionX,
        Double positionY,
        String notes) {
}
