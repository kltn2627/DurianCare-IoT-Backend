package com.duriancare.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "activity_executions")
public record ActivityExecution(
        @Id String id,
        @Indexed String cultivationActivityId,
        Instant startedAt,
        Instant completedAt,
        String executedBy,
        String supervisedBy,
        Integer actualTreeCount,
        BigDecimal actualArea,
        String areaUnit,
        Map<String, Object> weatherSnapshot,
        String applicationMethod,
        String equipment,
        String notes,
        String resultObservation,
        List<String> evidenceFiles,
        boolean locked,
        Instant createdAt) {
}
