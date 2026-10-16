package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.domain.TreeHealthStatus;
import com.duriancare.farm.domain.TreeStatus;
import com.duriancare.farm.dto.CreateTreeRequest;
import com.duriancare.farm.dto.GenerateTreesRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.TreeDetailResponse;
import com.duriancare.farm.repository.DurianTreeRepository;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.repository.TreeDiagnosisRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Verifies the backend recovery gate in TreeService.transitionHealthStatus().
 *
 * Gate rule: PATCH /health-status with RECOVERED is only allowed when evaluateRecovery
 * returns RECOVERED or IMPROVED. Any other outcome (WORSENED, STABLE, UNCERTAIN) throws
 * FarmConflictException → HTTP 409.
 */
@ExtendWith(MockitoExtension.class)
class TreeServiceRecoveryGateTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private DurianTreeRepository treeRepository;
    @Mock
    private TreeDiagnosisRecordRepository diagnosisRepository;

    private TreeService service;

    private static final String TREE_ID = "tree-026";
    private static final String FARM_ID = "farm-1";
    private static final String USER_ID = "user-1";
    private static final RequestActor ACTOR = new RequestActor(USER_ID, null, "FARMER");

    private static final DurianTree TREATING_TREE = new DurianTree(
            TREE_ID, FARM_ID, "zone-1", null,
            "DC-T026", null, null, null,
            null, null, 0.3, 0.5,
            TreeHealthStatus.TREATING, TreeStatus.ACTIVE, null,
            Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));

    private static final Farm FARM = new Farm(
            FARM_ID, USER_ID, "Farm", null, null, null,
            null, null, null, null, List.of(),
            Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        service = new TreeService(farmRepository, treeRepository, diagnosisRepository);
    }

    @Test
    void createTreeDefaultsToHealthyAndCreatesNoDiagnosis() {
        when(farmRepository.findByZoneId("zone-1")).thenReturn(Optional.of(FARM));
        when(treeRepository.existsByFarmZoneIdAndTreeCode("zone-1", "DC-T100")).thenReturn(false);
        when(treeRepository.save(any(DurianTree.class))).thenAnswer(invocation -> {
            DurianTree tree = invocation.getArgument(0);
            return new DurianTree(
                    "tree-new", tree.farmId(), tree.farmZoneId(), tree.speciesId(), tree.treeCode(),
                    tree.nickname(), tree.variety(), tree.plantedDate(), tree.latitude(), tree.longitude(),
                    tree.positionX(), tree.positionY(), tree.healthStatus(), tree.status(), tree.notes(),
                    tree.createdAt(), tree.updatedAt());
        });

        TreeDetailResponse created = service.createTree(
                ACTOR,
                "zone-1",
                new CreateTreeRequest(
                        "DC-T100",
                        "Cay moi",
                        "Ri6",
                        "durian",
                        LocalDate.parse("2026-10-14"),
                        BigDecimal.valueOf(10.1),
                        BigDecimal.valueOf(106.1),
                        0.2,
                        0.4,
                        "New planting"));

        assertThat(created.healthStatus()).isEqualTo(TreeHealthStatus.HEALTHY.name());
        ArgumentCaptor<DurianTree> captor = ArgumentCaptor.forClass(DurianTree.class);
        verify(treeRepository).save(captor.capture());
        assertThat(captor.getValue().healthStatus()).isEqualTo(TreeHealthStatus.HEALTHY);
        verify(diagnosisRepository, never()).save(any(TreeDiagnosisRecord.class));
    }

    @Test
    void generateTreesDefaultsEveryGeneratedTreeToHealthy() {
        when(farmRepository.findByZoneId("zone-1")).thenReturn(Optional.of(FARM));
        when(treeRepository.existsByFarmZoneIdAndTreeCode("zone-1", "H01-C01")).thenReturn(false);
        when(treeRepository.existsByFarmZoneIdAndTreeCode("zone-1", "H01-C02")).thenReturn(false);
        when(treeRepository.save(any(DurianTree.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.generateTrees(
                ACTOR,
                "zone-1",
                new GenerateTreesRequest(1, 2, "Ri6", LocalDate.parse("2026-10-14"), "Grid"));

        ArgumentCaptor<DurianTree> captor = ArgumentCaptor.forClass(DurianTree.class);
        verify(treeRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DurianTree::healthStatus)
                .containsOnly(TreeHealthStatus.HEALTHY);
        verify(diagnosisRepository, never()).save(any(TreeDiagnosisRecord.class));
    }

    @Test
    void listTreesForMapReturnsOnlyActiveTrees() {
        when(farmRepository.findByZoneId("zone-1")).thenReturn(Optional.of(FARM));
        when(treeRepository.findByFarmZoneIdAndStatus("zone-1", TreeStatus.ACTIVE))
                .thenReturn(List.of(TREATING_TREE));
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(TREE_ID))
                .thenReturn(Optional.empty());
        when(diagnosisRepository.countByTreeId(TREE_ID)).thenReturn(0L);

        assertThat(service.listTreesForMap(ACTOR, "zone-1"))
                .extracting("id")
                .containsExactly(TREE_ID);

        verify(treeRepository).findByFarmZoneIdAndStatus("zone-1", TreeStatus.ACTIVE);
    }

    @Test
    void authorizedFarmerCanSoftRemoveTreeWithHistory() {
        TreeDiagnosisRecord latest = diagRecord(TREE_ID, "PHOMOPSIS_LEAF_SPOT", 0.8,
                Instant.parse("2026-10-01T10:00:00Z"));
        stubTreeAndFarm();
        when(treeRepository.save(any(DurianTree.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(TREE_ID))
                .thenReturn(Optional.of(latest));
        when(diagnosisRepository.countByTreeId(TREE_ID)).thenReturn(3L);

        TreeDetailResponse removed = service.removeTree(ACTOR, TREE_ID);

        assertThat(removed.status()).isEqualTo(TreeStatus.REMOVED.name());
        assertThat(removed.diagnosisCount()).isEqualTo(3L);
        assertThat(removed.latestDiseaseCode()).isEqualTo("PHOMOPSIS_LEAF_SPOT");
        ArgumentCaptor<DurianTree> captor = ArgumentCaptor.forClass(DurianTree.class);
        verify(treeRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(TreeStatus.REMOVED);
        assertThat(captor.getValue().healthStatus()).isEqualTo(TREATING_TREE.healthStatus());
    }

    @Test
    void repeatedRemoveTreeIsIdempotent() {
        DurianTree removedTree = new DurianTree(
                TREE_ID, FARM_ID, "zone-1", null,
                "DC-T026", null, null, null,
                null, null, 0.3, 0.5,
                TreeHealthStatus.TREATING, TreeStatus.REMOVED, null,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(removedTree));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(FARM));
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(TREE_ID))
                .thenReturn(Optional.empty());
        when(diagnosisRepository.countByTreeId(TREE_ID)).thenReturn(0L);

        TreeDetailResponse response = service.removeTree(ACTOR, TREE_ID);

        assertThat(response.status()).isEqualTo(TreeStatus.REMOVED.name());
        verify(treeRepository, never()).save(any(DurianTree.class));
    }

    @Test
    void unauthorizedFarmerCannotRemoveTree() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(TREATING_TREE));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(FARM));
        RequestActor otherActor = new RequestActor("other-user", null, "FARMER");

        assertThatThrownBy(() -> service.removeTree(otherActor, TREE_ID))
                .isInstanceOf(FarmAccessDeniedException.class);

        verify(treeRepository, never()).save(any(DurianTree.class));
    }

    @Test
    void removedTreeCannotTransitionHealthStatus() {
        DurianTree removedTree = new DurianTree(
                TREE_ID, FARM_ID, "zone-1", null,
                "DC-T026", null, null, null,
                null, null, 0.3, 0.5,
                TreeHealthStatus.TREATING, TreeStatus.REMOVED, null,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(removedTree));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(FARM));

        assertThatThrownBy(() -> service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.TREATING))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("after removal");

        verify(treeRepository, never()).save(any(DurianTree.class));
    }

    // ── Bypass-attack test ────────────────────────────────────────────────────

    /**
     * DC-T026 scenario: TREATING tree with current diagnosis PHOMOPSIS_LEAF_SPOT and
     * previous diagnosis HEALTHY_LEAF.  evaluateRecovery returns WORSENED (different codes).
     * Direct PATCH RECOVERED must be rejected with HTTP 409.
     */
    @Test
    void worsenedEvaluationBlocksRecoveredTransition() {
        TreeDiagnosisRecord current = diagRecord(TREE_ID, "PHOMOPSIS_LEAF_SPOT", 0.9974,
                Instant.parse("2026-10-01T10:00:00Z"));
        TreeDiagnosisRecord previous = diagRecord(TREE_ID, "HEALTHY_LEAF", 0.87,
                Instant.parse("2026-09-20T10:00:00Z"));

        stubTreeAndFarm();
        when(diagnosisRepository.findByTreeIdOrderByDiagnosedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(current, previous)));

        assertThatThrownBy(() -> service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.RECOVERED))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("RECOVERY_NOT_ALLOWED")
                .hasMessageContaining("WORSENED");

        // DB must not be updated
        verify(treeRepository, never()).save(any(DurianTree.class));
    }

    /**
     * UNCERTAIN scenario: only one diagnosis record exists, cannot compare two.
     * PATCH RECOVERED must be rejected.
     */
    @Test
    void uncertainEvaluationBlocksRecoveredTransition() {
        TreeDiagnosisRecord onlyRecord = diagRecord(TREE_ID, "PHOMOPSIS_LEAF_SPOT", 0.85,
                Instant.parse("2026-10-01T10:00:00Z"));

        stubTreeAndFarm();
        when(diagnosisRepository.findByTreeIdOrderByDiagnosedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(onlyRecord)));

        assertThatThrownBy(() -> service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.RECOVERED))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("RECOVERY_NOT_ALLOWED")
                .hasMessageContaining("UNCERTAIN");

        verify(treeRepository, never()).save(any(DurianTree.class));
    }

    // ── Valid-recovery test ───────────────────────────────────────────────────

    /**
     * DC-T010 scenario: TREATING tree with current diagnosis HEALTHY_LEAF.
     * evaluateRecovery returns RECOVERED. PATCH RECOVERED must succeed (200).
     */
    @Test
    void recoveredEvaluationAllowsRecoveredTransition() {
        TreeDiagnosisRecord current = diagRecord(TREE_ID, "HEALTHY_LEAF", 0.93,
                Instant.parse("2026-10-02T10:00:00Z"));
        TreeDiagnosisRecord previous = diagRecord(TREE_ID, "LEAF_BLIGHT", 0.873,
                Instant.parse("2026-09-15T10:00:00Z"));

        stubTreeAndFarm();
        when(diagnosisRepository.findByTreeIdOrderByDiagnosedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(current, previous)));
        when(treeRepository.save(any(DurianTree.class))).thenReturn(TREATING_TREE);
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(TREE_ID))
                .thenReturn(Optional.empty());
        when(diagnosisRepository.countByTreeId(TREE_ID)).thenReturn(2L);

        TreeDetailResponse result = service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.RECOVERED);

        assertThat(result).isNotNull();
        verify(treeRepository).save(any(DurianTree.class));
    }

    /**
     * IMPROVED scenario: same disease code, confidence dropped > 15%.
     * evaluateRecovery returns IMPROVED. PATCH RECOVERED must succeed.
     */
    @Test
    void improvedEvaluationAllowsRecoveredTransition() {
        TreeDiagnosisRecord current = diagRecord(TREE_ID, "LEAF_BLIGHT", 0.60,
                Instant.parse("2026-10-02T10:00:00Z"), TreeHealthStatus.SUSPECTED);
        TreeDiagnosisRecord previous = diagRecord(TREE_ID, "LEAF_BLIGHT", 0.85,
                Instant.parse("2026-09-15T10:00:00Z"), TreeHealthStatus.DISEASED);

        stubTreeAndFarm();
        when(diagnosisRepository.findByTreeIdOrderByDiagnosedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(current, previous)));
        when(treeRepository.save(any(DurianTree.class))).thenReturn(TREATING_TREE);
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(TREE_ID))
                .thenReturn(Optional.empty());
        when(diagnosisRepository.countByTreeId(TREE_ID)).thenReturn(2L);

        TreeDetailResponse result = service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.RECOVERED);

        assertThat(result).isNotNull();
        verify(treeRepository).save(any(DurianTree.class));
    }

    /**
     * STABLE scenario: same disease code, confidence delta ≤ 15%.
     * evaluateRecovery returns STABLE. PATCH RECOVERED must be rejected.
     */
    @Test
    void stableEvaluationBlocksRecoveredTransition() {
        TreeDiagnosisRecord current = diagRecord(TREE_ID, "LEAF_BLIGHT", 0.75,
                Instant.parse("2026-10-02T10:00:00Z"));
        TreeDiagnosisRecord previous = diagRecord(TREE_ID, "LEAF_BLIGHT", 0.80,
                Instant.parse("2026-09-15T10:00:00Z"));

        stubTreeAndFarm();
        when(diagnosisRepository.findByTreeIdOrderByDiagnosedAtDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(current, previous)));

        assertThatThrownBy(() -> service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.RECOVERED))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("RECOVERY_NOT_ALLOWED")
                .hasMessageContaining("STABLE");

        verify(treeRepository, never()).save(any(DurianTree.class));
    }

    /**
     * TREATING transition must remain unrestricted — no evaluateRecovery check.
     */
    @Test
    void treatingTransitionRequiresNoEvaluation() {
        stubTreeAndFarm();
        when(treeRepository.save(any(DurianTree.class))).thenReturn(TREATING_TREE);
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(TREE_ID))
                .thenReturn(Optional.empty());
        when(diagnosisRepository.countByTreeId(TREE_ID)).thenReturn(0L);

        TreeDetailResponse result = service.transitionHealthStatus(ACTOR, TREE_ID, TreeHealthStatus.TREATING);

        assertThat(result).isNotNull();
        // diagnosisRepository.findByTreeIdOrderByDiagnosedAtDesc was NOT called (no gate)
        verify(diagnosisRepository, never()).findByTreeIdOrderByDiagnosedAtDesc(any(), any(Pageable.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubTreeAndFarm() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(TREATING_TREE));
        when(farmRepository.findById(FARM_ID)).thenReturn(Optional.of(FARM));
    }

    private static TreeDiagnosisRecord diagRecord(
            String treeId, String diseaseCode, double confidence, Instant diagnosedAt) {
        return diagRecord(treeId, diseaseCode, confidence, diagnosedAt, null);
    }

    private static TreeDiagnosisRecord diagRecord(
            String treeId,
            String diseaseCode,
            double confidence,
            Instant diagnosedAt,
            TreeHealthStatus impliedHealthStatus) {
        return new TreeDiagnosisRecord(
                null, treeId, FARM_ID, "zone-1",
                "http://img", diseaseCode, null,
                confidence, null, "AI_MODEL", USER_ID,
                impliedHealthStatus, diagnosedAt, diagnosedAt);
    }
}
