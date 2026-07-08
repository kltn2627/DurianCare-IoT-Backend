package com.duriancare.search.dto;

import com.duriancare.search.entity.SearchDocument;
import java.util.List;
import org.springframework.data.domain.Page;

public record SearchResponse(
        String query,
        String type,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int numberOfElements,
        boolean hasNext,
        boolean hasPrevious,
        String sortBy,
        String sortDirection,
        List<SearchResult> results) {

    public static SearchResponse from(
            String query,
            String type,
            String sortBy,
            String sortDirection,
            Page<SearchDocument> searchPage) {
        return new SearchResponse(
                query,
                type,
                searchPage.getNumber(),
                searchPage.getSize(),
                searchPage.getTotalElements(),
                searchPage.getTotalPages(),
                searchPage.getNumberOfElements(),
                searchPage.hasNext(),
                searchPage.hasPrevious(),
                sortBy,
                sortDirection,
                searchPage.getContent().stream()
                        .map(SearchResult::from)
                        .toList());
    }
}
