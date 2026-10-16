package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "compliance_assessments")
public record ComplianceAssessment(
        @Id String id,
        @Indexed String cultivationSeasonId,
        @Indexed String harvestBatchId,
        @Indexed String targetMarketCode,
        int riskScore,
        RiskLevel riskLevel,
        List<BlockingReason> blockingReasons,
        List<ComplianceWarning> warnings,
        LocalDate earliestSafeHarvestDate,
        boolean requiresLabTest,
        boolean eligibleForHarvest,
        boolean eligibleForExportRelease,
        Map<String, Object> assessmentDetails,
        Instant assessedAt,
        String rulesVersion) {
}
