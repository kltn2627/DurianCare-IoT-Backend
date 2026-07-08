package com.duriancare.search.service;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchService {

    SearchDocument index(SearchIndexRequest request);

    Page<SearchDocument> search(String query, String type, Pageable pageable);

    default List<SearchDocument> search(String query) {
        return search(query, null, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent();
    }
}
