package com.duriancare.cultivation.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceEngineTest {

    private final ComplianceEngine engine = new ComplianceEngine();

    @Test
    void blocksExportForProhibitedProduct() {
        ComplianceDecision decision = engine.assess(
                List.of(BlockingReason.PROHIBITED_INPUT_USED),
                List.of());

        assertThat(decision.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(decision.eligibleForHarvest()).isFalse();
        assertThat(decision.eligibleForExportRelease()).isFalse();
    }

    @Test
    void requiresLabTestWhenPhiIsNotMet() {
        ComplianceDecision decision = engine.assess(
                List.of(BlockingReason.PRE_HARVEST_INTERVAL_NOT_MET),
                List.of(ComplianceWarning.NEAR_PRE_HARVEST_INTERVAL));

        assertThat(decision.requiresLabTest()).isTrue();
        assertThat(decision.eligibleForHarvest()).isFalse();
    }

    @Test
    void allowsBiologicalOnlyFlowWithoutBlockers() {
        ComplianceDecision decision = engine.assess(
                List.of(),
                List.of(ComplianceWarning.MISSING_WEATHER_INFORMATION));

        assertThat(decision.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(decision.eligibleForHarvest()).isTrue();
        assertThat(decision.eligibleForExportRelease()).isTrue();
    }
}
