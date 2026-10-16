package com.duriancare.cultivation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** One evidence-backed dispatch per harvest lot; old lots are never implicitly verified. */
@Document(collection = "harvest_dispatches")
public record HarvestDispatch(
        @Id String id,
        String exportReleaseId,
        String farmId,
        String plotId,
        String cultivationSeasonId,
        Evidence evidence,
        String recordedBy,
        Instant recordedAt,
        String confirmedBy,
        Instant confirmedAt,
        @Version Long version,
        Delivery delivery) {

    public HarvestDispatch(String id, String exportReleaseId, String farmId, String plotId, String cultivationSeasonId,
            Evidence evidence, String recordedBy, Instant recordedAt, String confirmedBy, Instant confirmedAt, Long version) {
        this(id, exportReleaseId, farmId, plotId, cultivationSeasonId, evidence, recordedBy, recordedAt,
                confirmedBy, confirmedAt, version, null);
    }

    public record Delivery(String sourceRef, Instant deliveredAt, String recordedBy, Instant recordedAt) { }

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    public String status() {
        return confirmedAt == null ? "DRAFT" : delivery == null ? "DISPATCHED" : "DELIVERED";
    }

    public record Evidence(
            String harvestSourceRef,
            String gradingSourceRef,
            String packingSourceRef,
            String dispatchSourceRef,
            BigDecimal harvestedQuantity,
            BigDecimal qualifiedQuantity,
            BigDecimal rejectedQuantity,
            BigDecimal packedQuantity,
            BigDecimal shippedQuantity,
            String quantityUnit,
            Instant packedAt,
            Instant shippedAt,
            String destination) {
    }
}
