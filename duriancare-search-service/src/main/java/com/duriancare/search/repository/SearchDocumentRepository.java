package com.duriancare.search.repository;

import com.duriancare.search.entity.SearchDocument;
import java.util.List;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SearchDocumentRepository
        extends ElasticsearchRepository<SearchDocument, String> {

    List<SearchDocument> findTop50ByTitleContainingOrContentContaining(
            String title,
            String content);
}
