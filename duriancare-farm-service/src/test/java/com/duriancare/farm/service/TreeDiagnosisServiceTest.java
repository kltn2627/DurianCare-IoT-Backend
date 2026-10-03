package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.duriancare.farm.domain.TreeHealthStatus;
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
}
