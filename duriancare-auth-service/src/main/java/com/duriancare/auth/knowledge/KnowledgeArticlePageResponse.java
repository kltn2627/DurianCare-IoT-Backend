package com.duriancare.auth.knowledge;

import java.util.List;

public record KnowledgeArticlePageResponse(
        List<KnowledgeArticleResponse> articles,
        long totalElements,
        int totalPages,
        int page,
        int size) {
}

