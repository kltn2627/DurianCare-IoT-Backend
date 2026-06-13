package com.duriancare.search.service;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import java.util.List;

public interface SearchService {

    SearchDocument index(SearchIndexRequest request);

    List<SearchDocument> search(String query);
}
