package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeCarePlanStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCarePlanStatusRequest(@NotNull TreeCarePlanStatus status) {
}
