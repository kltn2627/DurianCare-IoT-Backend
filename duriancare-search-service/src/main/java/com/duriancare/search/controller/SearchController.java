package com.duriancare.search.controller;

import com.duriancare.search.dto.SearchResponse;
import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import com.duriancare.search.service.SearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@Tag(name = "Search", description = "Search resources exposed through the API gateway")
@RequestMapping("/api/search")
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search indexed resources", description = "Supports keyword search with optional type filtering, pagination, and sorting.")
    public SearchResponse search(
            @RequestParam("q")
            @NotBlank
            @Size(max = 200)
            String query,
            @RequestParam(value = "type", required = false)
            @Size(max = 50)
            String type,
            @RequestParam(value = "page", defaultValue = "0")
            @Min(0)
            int page,
            @RequestParam(value = "size", defaultValue = "20")
            @Min(1)
            @Max(MAX_PAGE_SIZE)
            int size,
            @RequestParam(value = "sortBy", defaultValue = "updatedAt")
            @Pattern(regexp = "^(updatedAt|title)$")
            String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "desc")
            @Pattern(regexp = "^(asc|desc)$")
            String sortDirection) {
        String normalizedType = normalizeType(type);
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return SearchResponse.from(
                query.trim(),
                normalizedType,
                sortBy,
                sortDirection.toLowerCase(),
                searchService.search(query, normalizedType, pageable));
    }

    @PostMapping("/internal/index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Index a document internally", description = "Internal endpoint used by backend services to publish search documents.")
    public SearchDocument index(@Valid @RequestBody SearchIndexRequest request) {
        return searchService.index(request);
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String normalizedType = type.trim();
        return normalizedType.isEmpty() ? null : normalizedType.toUpperCase();
    }
}
