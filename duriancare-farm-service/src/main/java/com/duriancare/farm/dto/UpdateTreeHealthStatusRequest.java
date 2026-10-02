package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeHealthStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTreeHealthStatusRequest(@NotNull TreeHealthStatus healthStatus) {}
