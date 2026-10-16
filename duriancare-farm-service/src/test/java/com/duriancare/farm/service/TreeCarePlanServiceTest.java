package com.duriancare.farm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.TreeCareRecommendation;
import com.duriancare.farm.domain.TreeCareRecommendationStatus;
import com.duriancare.farm.domain.TreeCarePlanStatus;
import com.duriancare.farm.dto.AcceptCareRecommendationRequest;
import com.duriancare.farm.dto.CareRecommendationRequest;
import com.duriancare.farm.domain.TreeCarePlan;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.domain.TreeHealthStatus;
import com.duriancare.farm.domain.TreeStatus;
import com.duriancare.farm.dto.CreateTreeCarePlanRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.event.publisher.FarmNotificationEventPublisher;
import com.duriancare.farm.repository.TreeCarePlanRepository;
import com.duriancare.farm.repository.TreeCareRecommendationRepository;
import com.duriancare.farm.repository.TreeCareTaskRepository;
import com.duriancare.farm.repository.TreeDiagnosisRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreeCarePlanServiceTest {

    @Mock
    private TreeService treeService;
    @Mock
    private TreeCarePlanRepository carePlanRepository;
    @Mock
    private TreeCareRecommendationRepository recommendationRepository;
    @Mock
    private TreeCareTaskRepository taskRepository;
    @Mock
    private TreeDiagnosisRecordRepository diagnosisRepository;
    @Mock
    private AiCareRecommendationClient aiRecommendationClient;
    @Mock
    private FarmNotificationEventPublisher eventPublisher;

    private TreeCarePlanService service;

    private static final RequestActor ACTOR = new RequestActor("farmer-1", null, "FARMER");

    @BeforeEach
    void setUp() {
        service = new TreeCarePlanService(
                treeService, carePlanRepository, recommendationRepository, taskRepository,
                diagnosisRepository, aiRecommendationClient, eventPublisher);
    }

    @Test
    void followUpNotificationContainsTreeCarePlanAndRoutingMetadata() {
        DurianTree tree = tree("tree-003", "farm-1", "zone-a", "T003");
        TreeCarePlan saved = new TreeCarePlan(
                "plan-1", tree.id(), tree.farmId(), "diag-1",
                "PHOMOPSIS_LEAF_SPOT", "kb-1", "Theo hướng dẫn KB",
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-08"),
                com.duriancare.farm.domain.TreeCarePlanStatus.PLANNED,
                "MANUAL", null, null, java.util.List.of(), null,
                ACTOR.userId(), Instant.now(), Instant.now());

        stubActiveTree(tree);
        when(diagnosisRepository.findById("diag-1")).thenReturn(java.util.Optional.of(
                diagnosis("diag-1", tree, "PHOMOPSIS_LEAF_SPOT")));
        when(carePlanRepository.save(any(TreeCarePlan.class))).thenReturn(saved);

        service.createCarePlan(ACTOR, tree.id(), new CreateTreeCarePlanRequest(
                "diag-1", "PHOMOPSIS_LEAF_SPOT", "kb-1",
                "Theo hướng dẫn KB", saved.startDate(), saved.followUpDate()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(
                eq("CARE_FOLLOWUP"),
                eq("CARE_FOLLOWUP:plan-1:2026-10-08"),
                eq(ACTOR.userId()),
                eq("Lịch tái khám cây T003"),
                any(String.class),
                eq("CARE"),
                metadataCaptor.capture());

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata)
                .containsEntry("treeId", "tree-003")
                .containsEntry("treeCode", "T003")
                .containsEntry("farmId", "farm-1")
                .containsEntry("zoneId", "zone-a")
                .containsEntry("carePlanId", "plan-1")
                .containsEntry("followUpDate", "2026-10-08");
        assertThat(metadata.get("targetUrl"))
                .isEqualTo("/dashboard/client/farms/farm-1/zones/zone-a?treeId=tree-003");
    }

    @Test
    void createRecommendationReturnsExistingDraftForSameDiagnosisAndSource() {
        DurianTree tree = tree("tree-010", "farm-1", "zone-a", "T010");
        TreeDiagnosisRecord diagnosis = diagnosis("diag-10", tree, "PHOMOPSIS_LEAF_SPOT");
        TreeCareRecommendation existing = recommendation("rec-10", tree, diagnosis, TreeCareRecommendationStatus.DRAFT, null);
        stubActiveTree(tree);
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(tree.id())).thenReturn(Optional.of(diagnosis));
        when(recommendationRepository.findByDiagnosisIdAndSource("diag-10", "AI_RECOMMENDED"))
                .thenReturn(Optional.of(existing));

        var response = service.createRecommendation(ACTOR, tree.id(), new CareRecommendationRequest(null));

        assertThat(response.id()).isEqualTo("rec-10");
        verify(recommendationRepository, never()).save(any(TreeCareRecommendation.class));
    }

    @Test
    void createRecommendationUsesConservativeFallbackWhenAiUnavailable() {
        DurianTree tree = tree("tree-011", "farm-1", "zone-a", "T011");
        TreeDiagnosisRecord diagnosis = diagnosis("diag-11", tree, "UNKNOWN_DISEASE");
        stubActiveTree(tree);
        when(diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(tree.id())).thenReturn(Optional.of(diagnosis));
        when(recommendationRepository.findByDiagnosisIdAndSource("diag-11", "AI_RECOMMENDED"))
                .thenReturn(Optional.empty());
        when(aiRecommendationClient.fetchRecommendation("UNKNOWN_DISEASE")).thenReturn(Optional.empty());
        when(recommendationRepository.save(any(TreeCareRecommendation.class))).thenAnswer(invocation -> {
            TreeCareRecommendation recommendation = invocation.getArgument(0);
            return new TreeCareRecommendation(
                    "rec-11", recommendation.treeId(), recommendation.farmId(), recommendation.farmZoneId(),
                    recommendation.treeCode(), recommendation.diagnosisId(), recommendation.diseaseCode(),
                    recommendation.source(), recommendation.status(), recommendation.recommendationSnapshot(),
                    recommendation.tasks(), recommendation.followUpDate(), recommendation.acceptedCarePlanId(),
                    recommendation.createdByUserId(), recommendation.createdAt(), recommendation.updatedAt());
        });

        var response = service.createRecommendation(ACTOR, tree.id(), new CareRecommendationRequest(null));

        assertThat(response.recommendationSnapshot()).containsEntry("source", "CONSERVATIVE_MONITORING");
        assertThat(response.tasks()).isNotEmpty();
    }

    @Test
    void acceptRecommendationIsIdempotentAndDoesNotCreateDuplicateTasks() {
        DurianTree tree = tree("tree-012", "farm-1", "zone-a", "T012");
        TreeDiagnosisRecord diagnosis = diagnosis("diag-12", tree, "PHOMOPSIS_LEAF_SPOT");
        TreeCareRecommendation recommendation = recommendation("rec-12", tree, diagnosis, TreeCareRecommendationStatus.DRAFT, null);
        TreeCarePlan existing = new TreeCarePlan(
                "plan-12", tree.id(), tree.farmId(), diagnosis.id(), diagnosis.diseaseCode(),
                null, "existing", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-08"),
                com.duriancare.farm.domain.TreeCarePlanStatus.PLANNED,
                "AI_RECOMMENDED", recommendation.id(), recommendation.recommendationSnapshot(),
                List.of("task-1"), Instant.now(), ACTOR.userId(), Instant.now(), Instant.now());
        stubActiveTree(tree);
        when(recommendationRepository.findById("rec-12")).thenReturn(Optional.of(recommendation));
        when(carePlanRepository.findByRecommendationId("rec-12")).thenReturn(Optional.of(existing));

        var response = service.acceptRecommendation(ACTOR, tree.id(), "rec-12", new AcceptCareRecommendationRequest(
                null, null, null, null));

        assertThat(response.id()).isEqualTo("plan-12");
        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
        verify(taskRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publish(
                eq("CARE_FOLLOWUP"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createCarePlanDerivesDiseaseCodeFromDiagnosisWhenClientOmitsDiseaseCode() {
        DurianTree tree = tree("tree-008", "farm-1", "zone-a", "T008");
        stubActiveTree(tree);
        when(diagnosisRepository.findById("diag-8")).thenReturn(java.util.Optional.of(
                diagnosis("diag-8", tree, "Algal_Leaf_Spot")));
        when(carePlanRepository.save(any(TreeCarePlan.class))).thenAnswer(invocation -> {
            TreeCarePlan plan = invocation.getArgument(0);
            return new TreeCarePlan(
                    "plan-8", plan.treeId(), plan.farmId(), plan.diagnosisId(), plan.diseaseCode(),
                    plan.knowledgeArticleId(), plan.treatment(), plan.startDate(), plan.followUpDate(),
                    plan.status(), plan.source(), plan.recommendationId(), plan.recommendationSnapshot(),
                    plan.taskIds(), plan.acceptedAt(), plan.createdByUserId(), plan.createdAt(), plan.updatedAt());
        });

        service.createCarePlan(ACTOR, tree.id(), new CreateTreeCarePlanRequest(
                "diag-8", null, "kb-1", "Theo dõi tán lá",
                LocalDate.parse("2026-10-01"), null));

        ArgumentCaptor<TreeCarePlan> planCaptor = ArgumentCaptor.forClass(TreeCarePlan.class);
        verify(carePlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().diseaseCode()).isEqualTo("Algal_Leaf_Spot");
        assertThat(planCaptor.getValue().diagnosisId()).isEqualTo("diag-8");
    }

    @Test
    void createCarePlanRejectsConflictingDiseaseCodeWhenDiagnosisIdIsProvided() {
        DurianTree tree = tree("tree-008", "farm-1", "zone-a", "T008");
        stubActiveTree(tree);
        when(diagnosisRepository.findById("diag-8")).thenReturn(java.util.Optional.of(
                diagnosis("diag-8", tree, "Algal_Leaf_Spot")));

        assertThatThrownBy(() -> service.createCarePlan(ACTOR, tree.id(), new CreateTreeCarePlanRequest(
                "diag-8", "Phomopsis_Leaf_Spot", "kb-1", "Theo dõi tán lá",
                LocalDate.parse("2026-10-01"), null)))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("Disease code must match");

        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
    }

    @Test
    void createCarePlanRejectsDiagnosisFromAnotherTree() {
        DurianTree tree = tree("tree-008", "farm-1", "zone-a", "T008");
        DurianTree otherTree = tree("tree-999", "farm-1", "zone-a", "T999");
        stubActiveTree(tree);
        when(diagnosisRepository.findById("diag-999")).thenReturn(java.util.Optional.of(
                diagnosis("diag-999", otherTree, "Algal_Leaf_Spot")));

        assertThatThrownBy(() -> service.createCarePlan(ACTOR, tree.id(), new CreateTreeCarePlanRequest(
                "diag-999", "Algal_Leaf_Spot", "kb-1", "Theo dõi tán lá",
                LocalDate.parse("2026-10-01"), null)))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("Diagnosis does not belong");

        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
    }

    @Test
    void createCarePlanReturnsExistingManualPlanForRepeatedDiagnosisRequest() {
        DurianTree tree = tree("tree-013", "farm-1", "zone-a", "T013");
        TreeCarePlan existing = carePlan(
                "plan-13", tree, "diag-13", "PHOMOPSIS_LEAF_SPOT", TreeCarePlanStatus.PLANNED);
        stubActiveTree(tree);
        when(diagnosisRepository.findById("diag-13")).thenReturn(Optional.of(
                diagnosis("diag-13", tree, "PHOMOPSIS_LEAF_SPOT")));
        when(carePlanRepository.findByDiagnosisIdAndSource("diag-13", "MANUAL"))
                .thenReturn(Optional.of(existing));

        var response = service.createCarePlan(ACTOR, tree.id(), new CreateTreeCarePlanRequest(
                "diag-13", "PHOMOPSIS_LEAF_SPOT", "kb-1", "Theo dõi",
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-08")));

        assertThat(response.id()).isEqualTo("plan-13");
        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
        verify(eventPublisher, never()).publish(
                eq("CARE_FOLLOWUP"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        DurianTree tree = tree("tree-014", "farm-1", "zone-a", "T014");
        TreeCarePlan plan = carePlan(
                "plan-14", tree, "diag-14", "PHOMOPSIS_LEAF_SPOT", TreeCarePlanStatus.PLANNED);
        when(carePlanRepository.findById("plan-14")).thenReturn(Optional.of(plan));
        stubActiveTree(tree);

        assertThatThrownBy(() -> service.updateStatus(ACTOR, "plan-14", TreeCarePlanStatus.COMPLETED))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("Invalid care plan status transition");

        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
    }

    @Test
    void updateStatusPersistsAllowedTransitionAndReturnsSavedStatus() {
        DurianTree tree = tree("tree-015", "farm-1", "zone-a", "T015");
        TreeCarePlan plan = carePlan(
                "plan-15", tree, "diag-15", "PHOMOPSIS_LEAF_SPOT", TreeCarePlanStatus.PLANNED);
        when(carePlanRepository.findById("plan-15")).thenReturn(Optional.of(plan));
        stubActiveTree(tree);
        when(carePlanRepository.save(any(TreeCarePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateStatus(ACTOR, "plan-15", TreeCarePlanStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        ArgumentCaptor<TreeCarePlan> planCaptor = ArgumentCaptor.forClass(TreeCarePlan.class);
        verify(carePlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().status()).isEqualTo(TreeCarePlanStatus.IN_PROGRESS);
    }

    @Test
    void updateStatusIsIdempotentForSameStatus() {
        DurianTree tree = tree("tree-016", "farm-1", "zone-a", "T016");
        TreeCarePlan plan = carePlan(
                "plan-16", tree, "diag-16", "PHOMOPSIS_LEAF_SPOT", TreeCarePlanStatus.COMPLETED);
        when(carePlanRepository.findById("plan-16")).thenReturn(Optional.of(plan));
        stubActiveTree(tree);

        var response = service.updateStatus(ACTOR, "plan-16", TreeCarePlanStatus.COMPLETED);

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
    }

    @Test
    void acceptRecommendationDeletesPlanAndDoesNotNotifyWhenTaskPersistenceFails() {
        DurianTree tree = tree("tree-017", "farm-1", "zone-a", "T017");
        TreeDiagnosisRecord diagnosis = diagnosis("diag-17", tree, "PHOMOPSIS_LEAF_SPOT");
        TreeCareRecommendation recommendation = recommendation(
                "rec-17", tree, diagnosis, TreeCareRecommendationStatus.DRAFT, null);
        TreeCarePlan savedPlan = carePlan(
                "plan-17", tree, diagnosis.id(), diagnosis.diseaseCode(), TreeCarePlanStatus.PLANNED);
        stubActiveTree(tree);
        when(recommendationRepository.findById("rec-17")).thenReturn(Optional.of(recommendation));
        when(carePlanRepository.findByRecommendationId("rec-17")).thenReturn(Optional.empty());
        when(carePlanRepository.save(any(TreeCarePlan.class))).thenReturn(savedPlan);
        when(taskRepository.existsByCarePlanId("plan-17")).thenReturn(false);
        when(taskRepository.saveAll(any())).thenThrow(new RuntimeException("task persistence failed"));

        assertThatThrownBy(() -> service.acceptRecommendation(ACTOR, tree.id(), "rec-17",
                new AcceptCareRecommendationRequest(null, null, null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("task persistence failed");

        verify(carePlanRepository).deleteById("plan-17");
        verify(recommendationRepository, never()).save(any(TreeCareRecommendation.class));
        verify(eventPublisher, never()).publish(
                eq("CARE_FOLLOWUP"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void removedTreeCannotCreateRecommendationOrCareWork() {
        DurianTree tree = tree("tree-018", "farm-1", "zone-a", "T018");
        when(treeService.requireActiveTreeForMutation(eq(ACTOR), eq(tree.id()), any(String.class)))
                .thenThrow(new FarmConflictException("Care recommendation cannot be created after tree removal"));

        assertThatThrownBy(() -> service.createRecommendation(
                ACTOR, tree.id(), new CareRecommendationRequest(null)))
                .isInstanceOf(FarmConflictException.class)
                .hasMessageContaining("after tree removal");

        verify(recommendationRepository, never()).save(any(TreeCareRecommendation.class));
        verify(carePlanRepository, never()).save(any(TreeCarePlan.class));
        verify(taskRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publish(
                eq("CARE_FOLLOWUP"), any(), any(), any(), any(), any(), any());
    }

    private void stubActiveTree(DurianTree tree) {
        when(treeService.requireActiveTreeForMutation(eq(ACTOR), eq(tree.id()), any(String.class)))
                .thenReturn(tree);
    }

    private DurianTree tree(String id, String farmId, String zoneId, String treeCode) {
        return new DurianTree(
                id, farmId, zoneId, null,
                treeCode, null, null, null, null, null, 0.1, 0.2,
                TreeHealthStatus.TREATING, TreeStatus.ACTIVE, null,
                Instant.now(), Instant.now());
    }

    private TreeDiagnosisRecord diagnosis(String id, DurianTree tree, String diseaseCode) {
        return new TreeDiagnosisRecord(
                id, tree.id(), tree.farmId(), tree.farmZoneId(), "https://example.test/image.jpg",
                diseaseCode, diseaseCode, 0.87, Map.of(), "MOBILE", ACTOR.userId(),
                TreeHealthStatus.DISEASED, Instant.now(), Instant.now());
    }

    private TreeCareRecommendation recommendation(
            String id,
            DurianTree tree,
            TreeDiagnosisRecord diagnosis,
            TreeCareRecommendationStatus status,
            String acceptedCarePlanId) {
        return new TreeCareRecommendation(
                id, tree.id(), tree.farmId(), tree.farmZoneId(), tree.treeCode(), diagnosis.id(),
                diagnosis.diseaseCode(), "AI_RECOMMENDED", status,
                Map.of(
                        "source", "AI_SERVICE_STRUCTURED_KB",
                        "summary", "Summary",
                        "careActions", List.of("Theo dõi lá bệnh"),
                        "monitoringActions", List.of("Tái kiểm tra")),
                List.of(new com.duriancare.farm.domain.RecommendedCareTaskDraft(
                        "Theo dõi lá bệnh", "Theo dõi lá bệnh", LocalDate.parse("2026-10-08"), 1)),
                LocalDate.parse("2026-10-08"), acceptedCarePlanId,
                ACTOR.userId(), Instant.now(), Instant.now());
    }

    private TreeCarePlan carePlan(
            String id,
            DurianTree tree,
            String diagnosisId,
            String diseaseCode,
            TreeCarePlanStatus status) {
        return new TreeCarePlan(
                id, tree.id(), tree.farmId(), diagnosisId, diseaseCode,
                null, "Theo dõi", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-08"),
                status, "MANUAL", null, null, List.of(), null,
                ACTOR.userId(), Instant.now(), Instant.now());
    }
}
