package com.duriancare.search.event.listener;

import com.duriancare.search.dto.SearchIndexRequest;
import com.duriancare.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexEventListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SearchIndexEventListener.class);

    private final ObjectMapper objectMapper;
    private final SearchService searchService;

    public SearchIndexEventListener(
            ObjectMapper objectMapper,
            SearchService searchService) {
        this.objectMapper = objectMapper;
        this.searchService = searchService;
    }

    @KafkaListener(
            topics = "${duriancare.search.index-topic:search.document.upsert}",
            groupId = "${spring.application.name}")
    public void onDocumentUpsert(String payload) {
        try {
            searchService.index(
                    objectMapper.readValue(payload, SearchIndexRequest.class));
        } catch (Exception exception) {
            LOGGER.error("Unable to index Kafka search event", exception);
        }
    }
}
