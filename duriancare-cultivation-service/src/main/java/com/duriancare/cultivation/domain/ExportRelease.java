package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "export_releases")
public record ExportRelease(
        @Id String id,
        @Indexed(unique = true) String releaseCode,
        @Indexed String harvestBatchId,
        @Indexed String targetMarketCode,
        Map<String, Object> traceabilitySnapshot,
        Map<String, Object> complianceAssessmentSnapshot,
        @Indexed ExportReleaseStatus status,
        String submittedBy,
        Instant submittedAt,
        String reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant releasedAt,
        Instant createdAt) {
}
