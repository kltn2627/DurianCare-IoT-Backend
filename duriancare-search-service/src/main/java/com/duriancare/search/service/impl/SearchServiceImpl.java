package com.duriancare.search.service.impl;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import com.duriancare.search.repository.SearchDocumentRepository;
import com.duriancare.search.service.SearchService;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<SearchDocument> search(String query, String type, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return Page.empty(pageable);
        }

        String normalizedType = normalizeType(type);
        if (normalizedType == null) {
            return repository.findByTitleContainingOrContentContaining(
                    normalizedQuery,
                    normalizedQuery,
                    pageable);
        }
        return repository.findByTypeAndTitleContainingOrTypeAndContentContaining(
                normalizedType,
                normalizedQuery,
                normalizedType,
                normalizedQuery,
                pageable);
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String normalizedType = type.trim();
        if (normalizedType.isEmpty()) {
            return null;
        }
        return normalizedType.toUpperCase(Locale.ROOT);
    }
}
