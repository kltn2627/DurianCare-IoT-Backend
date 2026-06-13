package com.duriancare.search.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record SearchIndexRequest(
        @NotBlank String id,
        @NotBlank String type,
        @NotBlank String title,
        String content,
        Map<String, Object> metadata) {
}
