package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.farm.domain.RecoveryOutcome;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.domain.TreeHealthStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TreeDiagnosisServiceTest {

    // ── Phase G: RECOVERED_BY_FARMER must map to HEALTHY ──────────────────────

    @Test
    void recoveredByFarmerMapsToHealthy() {
        assertThat(TreeDiagnosisService.inferHealthStatus("RECOVERED_BY_FARMER"))
                .isEqualTo(TreeHealthStatus.HEALTHY);
    }

    @Test
    void recoveredByFarmerCaseInsensitive() {
        assertThat(TreeDiagnosisService.inferHealthStatus("recovered_by_farmer"))
                .isEqualTo(TreeHealthStatus.HEALTHY);
    }

    // ── Phase F: healthy codes map to HEALTHY ─────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"HEALTHY_LEAF", "healthy_leaf", "HEALTHY", "healthy"})
    void healthyCodesMapsToHealthy(String code) {
        assertThat(TreeDiagnosisService.inferHealthStatus(code))
                .isEqualTo(TreeHealthStatus.HEALTHY);
    }

    // ── Disease codes map to DISEASED ─────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "LEAF_BLIGHT", "ALGAL_LEAF_SPOT", "PHOMOPSIS_LEAF_SPOT",
        "ALLOCARIDARA_ATTACK", "LOW_CONFIDENCE", "INVALID_IMAGE"
    })
    void diseasedCodesMapsToDisease(String code) {
        assertThat(TreeDiagnosisService.inferHealthStatus(code))
                .isEqualTo(TreeHealthStatus.DISEASED);
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Test
    void nullCodeMapsSuspected() {
        assertThat(TreeDiagnosisService.inferHealthStatus(null))
                .isEqualTo(TreeHealthStatus.SUSPECTED);
    }

    @Test
    void blankCodeMapsSuspected() {
        assertThat(TreeDiagnosisService.inferHealthStatus("  "))
                .isEqualTo(TreeHealthStatus.SUSPECTED);
    }

    @Test
    void dashesAndSpacesNormalized() {
        assertThat(TreeDiagnosisService.inferHealthStatus("HEALTHY-LEAF"))
                .isEqualTo(TreeHealthStatus.HEALTHY);
        assertThat(TreeDiagnosisService.inferHealthStatus("RECOVERED BY FARMER"))
                .isEqualTo(TreeHealthStatus.HEALTHY);
    }

    // ── Recovery outcome: never treat classifier confidence as severity ──────

    @Test
    void healthyFollowUpEvaluatesRecovered() {
        assertThat(TreeDiagnosisService.evaluateOutcome(
                diag("LEAF_BLIGHT", 0.87, TreeHealthStatus.DISEASED),
                diag("HEALTHY_LEAF", 0.62, TreeHealthStatus.HEALTHY)))
                .isEqualTo(RecoveryOutcome.RECOVERED);
    }

    @Test
    void diseasedToSuspectedEvaluatesImprovedWithoutConfidenceSeverity() {
        assertThat(TreeDiagnosisService.evaluateOutcome(
                diag("PHOMOPSIS_LEAF_SPOT", 0.87, TreeHealthStatus.DISEASED),
                diag("PHOMOPSIS_LEAF_SPOT", 0.91, TreeHealthStatus.SUSPECTED)))
                .isEqualTo(RecoveryOutcome.IMPROVED);
    }

    @Test
    void sameDiseaseStaysStableEvenWhenConfidenceChanges() {
        assertThat(TreeDiagnosisService.evaluateOutcome(
                diag("PHOMOPSIS_LEAF_SPOT", 0.87, TreeHealthStatus.DISEASED),
                diag("PHOMOPSIS_LEAF_SPOT", 0.99, TreeHealthStatus.DISEASED)))
                .isEqualTo(RecoveryOutcome.STABLE);
    }

    @Test
    void healthyToDiseaseEvaluatesWorsened() {
        assertThat(TreeDiagnosisService.evaluateOutcome(
                diag("HEALTHY_LEAF", 0.93, TreeHealthStatus.HEALTHY),
                diag("PHOMOPSIS_LEAF_SPOT", 0.76, TreeHealthStatus.DISEASED)))
                .isEqualTo(RecoveryOutcome.WORSENED);
    }

    @Test
    void differentNonHealthyDiseasesEvaluateUncertain() {
        assertThat(TreeDiagnosisService.evaluateOutcome(
                diag("LEAF_BLIGHT", 0.87, TreeHealthStatus.DISEASED),
                diag("PHOMOPSIS_LEAF_SPOT", 0.91, TreeHealthStatus.DISEASED)))
                .isEqualTo(RecoveryOutcome.UNCERTAIN);
    }

    private static TreeDiagnosisRecord diag(
            String diseaseCode, double confidence, TreeHealthStatus impliedHealthStatus) {
        return new TreeDiagnosisRecord(
                null, "tree-1", "farm-1", "zone-1", "image",
                diseaseCode, null, confidence, null, "AI_MODEL",
                "user-1", impliedHealthStatus, Instant.now(), Instant.now());
    }
}
