package com.duriancare.search.dto;

import com.duriancare.search.entity.SearchDocument;
import java.time.Instant;

public record SearchResult(
        String id,
        String type,
        String title,
        String content,
        Instant updatedAt) {

    public static SearchResult from(SearchDocument document) {
        return new SearchResult(
                document.getId(),
                document.getType(),
                document.getTitle(),
                document.getContent(),
                document.getUpdatedAt());
    }
}
