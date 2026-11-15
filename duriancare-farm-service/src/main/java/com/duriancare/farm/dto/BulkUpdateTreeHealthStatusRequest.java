package com.duriancare.farm.dto;

import com.duriancare.farm.domain.TreeHealthStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Change the health status of several trees; each tree goes through the single-tree rules. */
public record BulkUpdateTreeHealthStatusRequest(
        @NotEmpty @Size(max = 200) List<@NotBlank String> treeIds,
        @NotNull TreeHealthStatus healthStatus) {
}
