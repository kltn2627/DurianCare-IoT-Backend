package com.duriancare.cultivation.compliance;

import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.RiskLevel;
import java.util.List;

public record ComplianceDecision(
        int riskScore,
        RiskLevel riskLevel,
        List<BlockingReason> blockingReasons,
        List<ComplianceWarning> warnings,
        boolean requiresLabTest,
        boolean eligibleForHarvest,
        boolean eligibleForExportRelease) {

    public ComplianceDecision {
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
