package com.duriancare.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.entity.SearchDocument;
import com.duriancare.search.service.SearchService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import(SearchApiExceptionHandler.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void searchReturnsPagedResponseWithMappedItems() throws Exception {
        SearchDocument first = document("1", "ARTICLE", "Durian article", "Durian content");
        SearchDocument second = document("2", "ARTICLE", "Another article", "More content");
        when(searchService.search(
                eq("durian"),
                eq("ARTICLE"),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(first, second),
                        PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "title")),
                        12));

        mockMvc.perform(get("/api/search")
                        .param("q", "durian")
                        .param("type", "article")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortBy", "title")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("durian"))
                .andExpect(jsonPath("$.type").value("ARTICLE"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.numberOfElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.results[0].id").value("1"))
                .andExpect(jsonPath("$.results[0].title").value("Durian article"))
                .andExpect(jsonPath("$.results[1].id").value("2"));
    }

    @Test
    void missingQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void blankQueryIsRejected() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidSortFieldIsRejected() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "durian")
                        .param("sortBy", "name"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSortDirectionIsRejected() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "durian")
                        .param("sortDirection", "up"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalIndexEndpointStillAcceptsDocumentPayload() throws Exception {
        when(searchService.index(any(SearchIndexRequest.class)))
                .thenAnswer(invocation -> {
                    SearchIndexRequest request = invocation.getArgument(0, SearchIndexRequest.class);
                    SearchDocument document = new SearchDocument();
                    document.setId(request.id());
                    document.setType(request.type());
                    document.setTitle(request.title());
                    document.setContent(request.content());
                    document.setMetadata(request.metadata());
                    document.setUpdatedAt(Instant.parse("2026-07-07T00:00:00Z"));
                    return document;
                });

        mockMvc.perform(post("/api/search/internal/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "doc-1",
                                  "type": "ARTICLE",
                                  "title": "Durian care",
                                  "content": "Guide",
                                  "metadata": {"source": "manual"}
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.type").value("ARTICLE"));

        verify(searchService).index(any(SearchIndexRequest.class));
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
