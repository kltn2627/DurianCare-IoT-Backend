package com.duriancare.search.controller;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import com.duriancare.search.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchDocument> search(@RequestParam("q") String query) {
        return searchService.search(query);
    }

    @PostMapping("/internal/index")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SearchDocument index(@Valid @RequestBody SearchIndexRequest request) {
        return searchService.index(request);
    }
}
