package com.duriancare.search.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import com.duriancare.search.repository.SearchDocumentRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private SearchDocumentRepository repository;

    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceImpl(repository);
    }

    @Test
    void searchUsesPaginationAndSortingWithoutTypeFilter() {
        SearchDocument first = document("1", "ARTICLE", "Durian article", "Durian care guide");
        SearchDocument second = document("2", "ARTICLE", "Leaf article", "Leaf blight");
        PageRequest pageRequest = PageRequest.of(2, 15, Sort.by(Sort.Direction.ASC, "title"));
        when(repository.findByTitleContainingOrContentContaining(
                eq("durian"),
                eq("durian"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageRequest, 32));

        Page<SearchDocument> result = service.search(" durian ", null, pageRequest);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(15);
        assertThat(result.getTotalElements()).isEqualTo(32);
        assertThat(result.getContent()).containsExactly(first, second);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByTitleContainingOrContentContaining(
                eq("durian"),
                eq("durian"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void searchAppliesTypeFilterWhenProvided() {
        SearchDocument document = document("1", "DISEASE", "Leaf blight", "Fungal disease");
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"));
        when(repository.findByTypeAndTitleContainingOrTypeAndContentContaining(
                eq("DISEASE"),
                eq("leaf"),
                eq("DISEASE"),
                eq("leaf"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document), pageRequest, 1));

        Page<SearchDocument> result = service.search(" leaf ", " disease ", pageRequest);

        assertThat(result.getContent()).containsExactly(document);
        verify(repository).findByTypeAndTitleContainingOrTypeAndContentContaining(
                eq("DISEASE"),
                eq("leaf"),
                eq("DISEASE"),
                eq("leaf"),
                any(Pageable.class));
    }

    @Test
    void legacySearchReturnsFirstPageContentForBackwardCompatibility() {
        SearchDocument document = document("1", "ARTICLE", "Durian article", "Guide");
        when(repository.findByTitleContainingOrContentContaining(
                eq("durian"),
                eq("durian"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 50), 1));

        assertThat(service.search("durian")).containsExactly(document);
    }

    @Test
    void indexUpdatesTimestampAndPersistsDocument() {
        SearchIndexRequest request = new SearchIndexRequest(
                "doc-1",
                "ARTICLE",
                "Durian farming",
                "Content",
                java.util.Map.of("source", "manual"));
        when(repository.save(any(SearchDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SearchDocument.class));

        SearchDocument result = service.index(request);

        assertThat(result.getId()).isEqualTo("doc-1");
        assertThat(result.getType()).isEqualTo("ARTICLE");
        assertThat(result.getTitle()).isEqualTo("Durian farming");
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(repository).save(any(SearchDocument.class));
    }

    private SearchDocument document(
            String id,
            String type,
            String title,
            String content) {
        SearchDocument document = new SearchDocument();
        document.setId(id);
        document.setType(type);
        document.setTitle(title);
        document.setContent(content);
        document.setUpdatedAt(Instant.parse("2026-07-07T00:00:00Z"));
        return document;
    }
}
