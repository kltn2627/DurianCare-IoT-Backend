package com.duriancare.cultivation.compliance;

import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.RiskLevel;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ComplianceEngine {

    private static final Set<BlockingReason> LAB_BLOCKERS = EnumSet.of(
            BlockingReason.LAB_TEST_REQUIRED,
            BlockingReason.PRE_HARVEST_INTERVAL_NOT_MET,
            BlockingReason.LAB_RESULT_FAILED,
            BlockingReason.LAB_RESULT_PENDING);

    public ComplianceDecision assess(List<BlockingReason> blockingReasons, List<ComplianceWarning> warnings) {
        List<BlockingReason> blockers = List.copyOf(blockingReasons == null ? List.of() : blockingReasons);
        List<ComplianceWarning> warningList = List.copyOf(warnings == null ? List.of() : warnings);
        int riskScore = Math.min(100, blockers.size() * 30 + warningList.size() * 8);
        RiskLevel riskLevel = riskLevel(riskScore, blockers);
        boolean requiresLabTest = blockers.stream().anyMatch(LAB_BLOCKERS::contains)
                || riskLevel == RiskLevel.HIGH
                || riskLevel == RiskLevel.CRITICAL;
        boolean eligible = blockers.isEmpty();
        return new ComplianceDecision(
                riskScore,
                riskLevel,
                blockers,
                warningList,
                requiresLabTest,
                eligible,
                eligible && !requiresLabTest);
    }

    private RiskLevel riskLevel(int riskScore, List<BlockingReason> blockingReasons) {
        if (blockingReasons.contains(BlockingReason.PROHIBITED_INPUT_USED)
                || blockingReasons.contains(BlockingReason.LAB_RESULT_FAILED)) {
            return RiskLevel.CRITICAL;
        }
        if (riskScore >= 70) return RiskLevel.HIGH;
        if (riskScore >= 35) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
