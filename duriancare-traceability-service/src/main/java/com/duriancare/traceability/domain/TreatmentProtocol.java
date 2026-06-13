package com.duriancare.traceability.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "treatment_protocols")
@CompoundIndex(
        name = "zone_status_idx",
        def = "{'farmZoneId': 1, 'status': 1}")
public record TreatmentProtocol(
        @Id String id,
        String farmId,
        String farmZoneId,
        String cropSeasonId,
        String diseaseRecordId,
        String createdByEngineerId,
        String approvedByFarmerId,
        String name,
        String objective,
        ProtocolStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Instant approvedAt,
        List<TreatmentStep> steps,
        Instant createdAt,
        Instant updatedAt) {
}
