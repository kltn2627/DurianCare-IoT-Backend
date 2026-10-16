package com.duriancare.auth.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record KnowledgeArticleRequest(
        @NotBlank @Size(max = 220) String title,
        @NotBlank @Size(max = 120) String category,
        @Size(max = 150) String author,
        @NotBlank @Size(max = 600) String excerpt,
        @NotBlank String content,
        KnowledgeArticleStatus status,
        boolean featured,
        List<@Size(max = 40) String> tags) {
}

