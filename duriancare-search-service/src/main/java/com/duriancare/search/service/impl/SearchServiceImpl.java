package com.duriancare.search.service.impl;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import com.duriancare.search.repository.SearchDocumentRepository;
import com.duriancare.search.service.SearchService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private final SearchDocumentRepository repository;

    public SearchServiceImpl(SearchDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public SearchDocument index(SearchIndexRequest request) {
        SearchDocument document = new SearchDocument();
        document.setId(request.id());
        document.setType(request.type());
        document.setTitle(request.title());
        document.setContent(request.content());
        document.setMetadata(request.metadata());
        document.setUpdatedAt(Instant.now());
        return repository.save(document);
    }

    @Override
    public List<SearchDocument> search(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }
        return repository.findTop50ByTitleContainingOrContentContaining(
                normalizedQuery,
                normalizedQuery);
    }
}
