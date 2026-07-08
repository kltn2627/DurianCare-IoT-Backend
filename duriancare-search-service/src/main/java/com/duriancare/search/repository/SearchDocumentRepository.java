package com.duriancare.search.repository;

import com.duriancare.search.entity.SearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SearchDocumentRepository
        extends ElasticsearchRepository<SearchDocument, String> {

    Page<SearchDocument> findByTitleContainingOrContentContaining(
            String title,
            String content,
            Pageable pageable);

    Page<SearchDocument> findByTypeAndTitleContainingOrTypeAndContentContaining(
            String typeForTitle,
            String title,
            String typeForContent,
            String content,
            Pageable pageable);
}
