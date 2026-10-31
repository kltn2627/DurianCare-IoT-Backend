package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.cultivation.api.CultivationCalendarDtos;
import com.duriancare.cultivation.compliance.ComplianceDecision;
import com.duriancare.cultivation.compliance.ComplianceEngine;
import com.duriancare.cultivation.compliance.LabResidueComparator;
import com.duriancare.cultivation.compliance.SafeHarvestDateCalculator;
import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.ActivityExecution;
import com.duriancare.cultivation.domain.ActivityInputUsage;
import com.duriancare.cultivation.domain.ActiveIngredientSnapshot;
import com.duriancare.cultivation.domain.AgriculturalInput;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.CultivationActivity;
import com.duriancare.cultivation.domain.CultivationPlan;
import com.duriancare.cultivation.domain.CultivationSeason;
import com.duriancare.cultivation.domain.ExportRelease;
import com.duriancare.cultivation.domain.ExportReleaseStatus;
import com.duriancare.cultivation.domain.ComplianceAssessment;
import com.duriancare.cultivation.domain.HarvestBatch;
import com.duriancare.cultivation.domain.HarvestBatchStatus;
import com.duriancare.cultivation.domain.InputCategory;
import com.duriancare.cultivation.domain.InputStatus;
import com.duriancare.cultivation.domain.LabSample;
import com.duriancare.cultivation.domain.LabSampleStatus;
import com.duriancare.cultivation.domain.PlanStatus;
import com.duriancare.cultivation.domain.RiskLevel;
import com.duriancare.cultivation.domain.SampleType;
import com.duriancare.cultivation.repository.ActivityExecutionRepository;
import com.duriancare.cultivation.repository.ActivityInputUsageRepository;
import com.duriancare.cultivation.repository.AgriculturalInputRepository;
import com.duriancare.cultivation.repository.AuditLogRepository;
import com.duriancare.cultivation.repository.ComplianceAssessmentRepository;
import com.duriancare.cultivation.repository.CultivationActivityRepository;
import com.duriancare.cultivation.repository.CultivationPlanRepository;
import com.duriancare.cultivation.repository.CultivationSeasonRepository;
import com.duriancare.cultivation.repository.ExportReleaseRepository;
import com.duriancare.cultivation.repository.HarvestBatchRepository;
import com.duriancare.cultivation.repository.LabResidueResultRepository;
import com.duriancare.cultivation.repository.LabSampleRepository;
import com.duriancare.cultivation.repository.ResidueStandardRepository;
import com.duriancare.cultivation.security.CultivationAccessDeniedException;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import com.duriancare.cultivation.security.CultivationActor;
import com.duriancare.cultivation.security.CultivationAuthenticationException;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CultivationCalendarAuthorizationServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"create", "submit", "approve", "release", "recall"})
    void exportReleaseRejectsForgedAuditActor(String operation) {
        var state = switch (operation) {
            case "approve" -> ExportReleaseStatus.UNDER_REVIEW;
            case "release" -> ExportReleaseStatus.APPROVED;
            case "recall" -> ExportReleaseStatus.RELEASED;
            default -> ExportReleaseStatus.DRAFT;
        };
        org.mockito.Mockito.lenient().when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED)));
        org.mockito.Mockito.lenient().when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release("release-1", "batch-1", state)));
        org.mockito.Mockito.lenient().when(exportReleaseRepository.findByHarvestBatchId("batch-1")).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(activityRepository.findAll()).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(complianceEngine.assess(any(), any()))
                .thenReturn(new ComplianceDecision(0, RiskLevel.LOW, List.of(), List.of(), false, true, true));
        org.mockito.Mockito.lenient().when(complianceAssessmentRepository.save(any(ComplianceAssessment.class)))
                .thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.lenient().when(exportReleaseRepository.save(any(ExportRelease.class)))
                .thenAnswer(i -> i.getArgument(0));
        Runnable request = switch (operation) {
            case "create" -> () -> service.createExportRelease(OWNER,
                    new CultivationCalendarDtos.CreateExportReleaseRequest("FIXTURE-REL", "batch-1", "VN", "forged-actor"));
            case "submit" -> () -> service.submitExportRelease(OWNER, "release-1", "forged-actor");
            case "approve" -> () -> service.approveExportRelease(OWNER, "release-1", "forged-actor");
            case "release" -> () -> service.releaseExportRelease(OWNER, "release-1", "forged-actor");
            default -> () -> service.recallExportRelease(OWNER, "release-1", "forged-actor", "fixture-only");
        };
        assertThatThrownBy(request::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authenticated actor");
        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
        verify(complianceAssessmentRepository, never()).save(any(ComplianceAssessment.class));
    }

    private static final CultivationActor OWNER = new CultivationActor("owner-1", "owner@example.com", "FARMER");

    @Test
    void matchingAuthenticatedActorCanCreateReadSubmitApproveReleaseAndRecallFixture() {
        var stored = new java.util.HashMap<String, ExportRelease>();
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED)));
        when(exportReleaseRepository.findByHarvestBatchId("batch-1"))
                .thenAnswer(i -> List.copyOf(stored.values()));
        when(exportReleaseRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(activityRepository.findAll()).thenReturn(List.of());
        when(complianceEngine.assess(any(), any()))
                .thenReturn(new ComplianceDecision(0, RiskLevel.LOW, List.of(), List.of(), false, true, true));
        when(complianceAssessmentRepository.save(any(ComplianceAssessment.class))).thenAnswer(i -> i.getArgument(0));
        when(exportReleaseRepository.save(any(ExportRelease.class))).thenAnswer(i -> {
            ExportRelease value = i.getArgument(0);
            stored.put(value.id(), value);
            return value;
        });
        var created = service.createExportRelease(OWNER, exportRequest("batch-1"));
        assertThat(created.status()).isEqualTo(ExportReleaseStatus.DRAFT);
        assertThat(created.submittedBy()).isEqualTo(OWNER.userId());
        assertThat(service.getExportRelease(OWNER, created.id()).id()).isEqualTo(created.id());
        assertThat(service.submitExportRelease(OWNER, created.id(), OWNER.userId()).status())
                .isEqualTo(ExportReleaseStatus.UNDER_REVIEW);
        assertThat(service.approveExportRelease(OWNER, created.id(), OWNER.userId()).status())
                .isEqualTo(ExportReleaseStatus.APPROVED);
        var released = service.releaseExportRelease(OWNER, created.id(), OWNER.userId());
        assertThat(released.status()).isEqualTo(ExportReleaseStatus.RELEASED);
        assertThat(released.reviewedBy()).isEqualTo(OWNER.userId());
        assertThat(released.releasedAt()).isNotNull();
        assertThatThrownBy(() -> service.createExportRelease(OWNER, exportRequest("batch-1")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
        assertThat(stored).hasSize(1);
        assertThat(service.recallExportRelease(OWNER, created.id(), OWNER.userId(), "fixture-only").status())
                .isEqualTo(ExportReleaseStatus.RECALLED);
    }

    @Mock private CultivationPlanRepository planRepository;
    @Mock private CultivationSeasonRepository seasonRepository;
    @Mock private CultivationActivityRepository activityRepository;
    @Mock private ActivityExecutionRepository executionRepository;
    @Mock private ActivityInputUsageRepository inputUsageRepository;
    @Mock private AgriculturalInputRepository agriculturalInputRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ResidueStandardRepository residueStandardRepository;
    @Mock private LabSampleRepository labSampleRepository;
    @Mock private LabResidueResultRepository labResidueResultRepository;
    @Mock private HarvestBatchRepository harvestBatchRepository;
    @Mock private ExportReleaseRepository exportReleaseRepository;
    @Mock private ComplianceAssessmentRepository complianceAssessmentRepository;
    @Mock private SafeHarvestDateCalculator safeHarvestDateCalculator;
    @Mock private ComplianceEngine complianceEngine;
    @Mock private LabResidueComparator labResidueComparator;
    @Mock private CultivationAccessGuard accessGuard;
    @Mock private com.duriancare.cultivation.repository.LegacyIntegrityMarkerRepository integrityMarkerRepository;

    private CultivationCalendarService service;

    @BeforeEach
    void setUp() {
        service = new CultivationCalendarService(
                planRepository,
                seasonRepository,
                activityRepository,
                executionRepository,
                inputUsageRepository,
                agriculturalInputRepository,
                auditLogRepository,
                residueStandardRepository,
                labSampleRepository,
                labResidueResultRepository,
                harvestBatchRepository,
                exportReleaseRepository,
                complianceAssessmentRepository,
                safeHarvestDateCalculator,
                complianceEngine,
                labResidueComparator,
                accessGuard,
                integrityMarkerRepository);
        org.mockito.Mockito.lenient()
                .when(activityRepository.save(any(CultivationActivity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(planRepository.findById("plan-1")).thenAnswer(invocation -> {
            CultivationActivity activity = activityRepository.findById("activity-1").orElse(null);
            return Optional.of(plan("plan-1", activity == null ? "farm-1" : activity.farmId(),
                    activity == null ? "zone-1" : activity.plotId()));
        });
        org.mockito.Mockito.lenient().when(seasonRepository.findById("season-1")).thenAnswer(invocation -> {
            HarvestBatch batch = harvestBatchRepository.findById("batch-1").orElse(null);
            CultivationActivity activity = activityRepository.findById("activity-1").orElse(null);
            String farmId = batch != null ? batch.farmId() : activity != null ? activity.farmId() : "farm-1";
            String plotId = batch != null ? batch.plotId() : activity != null ? activity.plotId() : "zone-1";
            return Optional.of(season(farmId, plotId));
        });
        org.mockito.Mockito.lenient().when(seasonRepository.existsById("season-1")).thenReturn(true);
    }

    @Test
    void directPlanIdLoadsResourceThenAuthorizesResolvedScope() {
        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan("plan-1", "farm-1", "zone-1")));

        service.getPlan(OWNER, "plan-1");

        verify(accessGuard).requireView(OWNER, "CultivationPlan", "plan-1", "farm-1", "zone-1");
    }

    @Test
    void createPlanRejectsMissingSeason() {
        var request = createPlanRequest("farm-1", "zone-1");
        when(seasonRepository.findById("season-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPlan(OWNER, request))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(planRepository, never()).save(any(CultivationPlan.class));
    }

    @Test
    void createPlanRejectsSeasonFromAnotherZone() {
        var request = createPlanRequest("farm-1", "zone-1");
        when(seasonRepository.findById("season-1")).thenReturn(Optional.of(season("farm-1", "zone-2")));

        assertThatThrownBy(() -> service.createPlan(OWNER, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("farm and zone");
        verify(planRepository, never()).save(any(CultivationPlan.class));
    }

    @Test
    void createPlanRejectsSeasonFromAnotherFarm() {
        var request = createPlanRequest("farm-1", "zone-1");
        when(seasonRepository.findById("season-1")).thenReturn(Optional.of(season("farm-2", "zone-1")));

        assertThatThrownBy(() -> service.createPlan(OWNER, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("farm and zone");
        verify(planRepository, never()).save(any(CultivationPlan.class));
    }

    @Test
    void createPlanAcceptsSeasonInMatchingFarmAndZone() {
        var request = createPlanRequest("farm-1", "zone-1");
        when(seasonRepository.findById("season-1")).thenReturn(Optional.of(season("farm-1", "zone-1")));
        when(planRepository.save(any(CultivationPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CultivationPlan saved = service.createPlan(OWNER, request);

        assertThat(saved.cultivationSeasonId()).isEqualTo("season-1");
        assertThat(saved.farmId()).isEqualTo("farm-1");
        assertThat(saved.plotId()).isEqualTo("zone-1");
    }

    private CultivationCalendarDtos.CreatePlanRequest createPlanRequest(String farmId, String plotId) {
        return new CultivationCalendarDtos.CreatePlanRequest(
                farmId, plotId, "season-1", null, "Care plan", LocalDate.parse("2026-08-01"),
                null, List.of("VN"), OWNER.userId());
    }

    private CultivationSeason season(String farmId, String plotId) {
        return new CultivationSeason(
                "season-1", farmId, plotId, "Season 2026", "DURIAN", "Ri6",
                LocalDate.parse("2026-08-01"), null, OWNER.userId(), Instant.now(), Instant.now());
    }

    @Test
    void directActivityIdOutsideScopeIsDeniedBeforeMutation() {
        CultivationActivity activity = activity("activity-1", "farm-2", "zone-9");
        when(activityRepository.findById("activity-1")).thenReturn(Optional.of(activity));
        org.mockito.Mockito.doThrow(new CultivationAccessDeniedException("denied"))
                .when(accessGuard)
                .requireUpdate(OWNER, "CultivationActivity", "activity-1", "farm-2", "zone-9");

        assertThatThrownBy(() -> service.startActivity(OWNER, "activity-1"))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    @Test
    void pendingApprovalActivityCanBeApprovedByAuthenticatedActor() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        CultivationActivity approved = service.approveActivity(OWNER, "activity-1", OWNER.userId());

        assertThat(approved.status()).isEqualTo(ActivityStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(OWNER.userId());
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.rejectionReason()).isNull();
        verify(accessGuard).requireUpdate(OWNER, "CultivationActivity", "activity-1", "farm-1", "zone-1");
    }

    @Test
    void pendingApprovalActivityCanBeRejectedWithReasonByAuthenticatedActor() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        CultivationActivity rejected = service.rejectActivity(OWNER, "activity-1", OWNER.userId(), "Needs review");

        assertThat(rejected.status()).isEqualTo(ActivityStatus.CANCELLED);
        assertThat(rejected.approvedBy()).isEqualTo(OWNER.userId());
        assertThat(rejected.rejectionReason()).isEqualTo("Needs review");
        verify(accessGuard).requireUpdate(OWNER, "CultivationActivity", "activity-1", "farm-1", "zone-1");
    }

    @Test
    void approveTwiceIsRejectedAfterFirstDecision() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.APPROVED, true)));

        assertThatThrownBy(() -> service.approveActivity(OWNER, "activity-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void rejectTwiceIsRejectedAfterFirstDecision() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.CANCELLED, true)));

        assertThatThrownBy(() -> service.rejectActivity(OWNER, "activity-1", OWNER.userId(), "Again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void approveAfterRejectIsRejected() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.CANCELLED, true)));

        assertThatThrownBy(() -> service.approveActivity(OWNER, "activity-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void rejectAfterApproveIsRejected() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.APPROVED, true)));

        assertThatThrownBy(() -> service.rejectActivity(OWNER, "activity-1", OWNER.userId(), "Too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void approveNonPendingStateIsRejected() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.SCHEDULED, true)));

        assertThatThrownBy(() -> service.approveActivity(OWNER, "activity-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void rejectNonPendingStateIsRejected() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.COMPLETED, true)));

        assertThatThrownBy(() -> service.rejectActivity(OWNER, "activity-1", OWNER.userId(), "Invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending approval");
    }

    @Test
    void pendingApprovalActivityCannotStartBeforeApproval() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        assertThatThrownBy(() -> service.startActivity(OWNER, "activity-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved before execution");
        verify(activityRepository, never()).save(any(CultivationActivity.class));
    }

    @Test
    void pendingApprovalActivityCannotCompleteBeforeApproval() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completeRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved before execution");
        verify(executionRepository, never()).save(any());
        verify(inputUsageRepository, never()).save(any());
        verify(activityRepository, never()).save(any(CultivationActivity.class));
    }

    @Test
    void pendingApprovalActivityCannotSkipBeforeDecision() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        assertThatThrownBy(() -> service.skipActivity(OWNER, "activity-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved before execution");
        verify(activityRepository, never()).save(any(CultivationActivity.class));
    }

    @Test
    void pendingApprovalActivityCannotCancelWithoutRejectReason() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        assertThatThrownBy(() -> service.cancelActivity(OWNER, "activity-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved before execution");
        verify(activityRepository, never()).save(any(CultivationActivity.class));
    }

    @Test
    void approvalNotRequiredActivityCannotBeApproved() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, false)));

        assertThatThrownBy(() -> service.approveActivity(OWNER, "activity-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not require approval");
    }

    @Test
    void approvalNotRequiredActivityCannotBeRejected() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, false)));

        assertThatThrownBy(() -> service.rejectActivity(OWNER, "activity-1", OWNER.userId(), "No approval needed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not require approval");
    }

    @Test
    void blankRejectReasonIsRejectedByService() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        assertThatThrownBy(() -> service.rejectActivity(OWNER, "activity-1", OWNER.userId(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void bodyUserIdCannotSpoofAuthenticatedApprovalActor() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", "farm-1", "zone-1", ActivityStatus.PENDING_APPROVAL, true)));

        assertThatThrownBy(() -> service.approveActivity(OWNER, "activity-1", "other-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match authenticated actor");
    }

    @Test
    void wrongFarmApprovalIsDeniedByAccessGuard() {
        approvalDeniedByGuard("farm-2", "zone-1", "denied wrong farm");
    }

    @Test
    void wrongAreaApprovalIsDeniedByAccessGuard() {
        approvalDeniedByGuard("farm-1", "zone-9", "denied wrong area");
    }

    @Test
    void revokedAuthorizationApprovalIsDeniedByAccessGuard() {
        approvalDeniedByGuard("farm-1", "zone-1", "authorization revoked");
    }

    @Test
    void expiredAuthorizationApprovalIsDeniedByAccessGuard() {
        approvalDeniedByGuard("farm-1", "zone-1", "authorization expired");
    }

    @Test
    void createActivityCannotMoveAcrossPlanScope() {
        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan("plan-1", "farm-1", "zone-1")));

        assertThatThrownBy(() -> service.createActivity(OWNER, new CultivationCalendarDtos.CreateActivityRequest(
                "plan-1",
                "season-1",
                "farm-1",
                "zone-2",
                List.of(),
                ActivityType.FERTILIZATION,
                "Task",
                null,
                Instant.now(),
                null,
                null,
                null,
                List.of(),
                false,
                List.of(),
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Activity scope must match");
    }

    @Test
    void anonymousPlanReadReturnsAuthenticationErrorFromGuard() {
        CultivationActor anonymous = new CultivationActor("", "", "");
        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan("plan-1", "farm-1", "zone-1")));
        org.mockito.Mockito.doThrow(new CultivationAuthenticationException("auth required"))
                .when(accessGuard)
                .requireView(anonymous, "CultivationPlan", "plan-1", "farm-1", "zone-1");

        assertThatThrownBy(() -> service.getPlan(anonymous, "plan-1"))
                .isInstanceOf(CultivationAuthenticationException.class);
    }

    @Test
    void harvestBatchIdLoadsBatchThenAuthorizesResolvedScope() {
        when(harvestBatchRepository.findById("batch-1")).thenReturn(Optional.of(batch("batch-1", "farm-2", "zone-9")));
        org.mockito.Mockito.doThrow(new CultivationAccessDeniedException("denied"))
                .when(accessGuard)
                .requireView(OWNER, "HarvestBatch", "batch-1", "farm-2", "zone-9");

        assertThatThrownBy(() -> service.getHarvestBatch(OWNER, "batch-1"))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    @Test
    void labSampleIdResolvesHarvestBatchScopeBeforeAllowingAccess() {
        when(labSampleRepository.findById("sample-1")).thenReturn(Optional.of(sample("sample-1", "batch-1")));
        when(harvestBatchRepository.findById("batch-1")).thenReturn(Optional.of(batch("batch-1", "farm-2", "zone-9")));
        when(accessGuard.canView(OWNER, "farm-2", "zone-9")).thenReturn(false);

        assertThatThrownBy(() -> service.getLabSample(OWNER, "sample-1"))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    @Test
    void exportReleaseIdResolvesHarvestBatchScopeBeforeAllowingAccess() {
        when(exportReleaseRepository.findById("release-1")).thenReturn(Optional.of(release("release-1", "batch-1")));
        when(harvestBatchRepository.findById("batch-1")).thenReturn(Optional.of(batch("batch-1", "farm-2", "zone-9")));
        org.mockito.Mockito.doThrow(new CultivationAccessDeniedException("denied"))
                .when(accessGuard)
                .requireView(OWNER, "HarvestBatch", "batch-1", "farm-2", "zone-9");

        assertThatThrownBy(() -> service.getExportRelease(OWNER, "release-1"))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    @Test
    void createExportReleaseRejectsBlockedHarvestBatchBeforePersistence() {
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.BLOCKED)));

        assertThatThrownBy(() -> service.createExportRelease(OWNER, exportRequest("batch-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not eligible");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
        verify(complianceAssessmentRepository, never()).save(any(ComplianceAssessment.class));
    }

    @Test
    void createExportReleaseUsesCanonicalBatchIdAndResolvedScope() {
        HarvestBatch batch = batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED);
        when(harvestBatchRepository.findById("batch-1")).thenReturn(Optional.of(batch));
        when(exportReleaseRepository.findByHarvestBatchId("batch-1")).thenReturn(List.of());
        when(activityRepository.findAll()).thenReturn(List.of());
        when(complianceEngine.assess(any(), any()))
                .thenReturn(new ComplianceDecision(0, RiskLevel.LOW, List.of(), List.of(), false, true, true));
        when(complianceAssessmentRepository.save(any(ComplianceAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(exportReleaseRepository.save(any(ExportRelease.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExportRelease release = service.createExportRelease(OWNER, exportRequest("batch-1"));

        assertThat(release.harvestBatchId()).isEqualTo("batch-1");
        assertThat(release.traceabilitySnapshot()).containsKey("harvestBatch");
        verify(accessGuard).requireView(OWNER, "HarvestBatch", "batch-1", "farm-1", "zone-1");
        verify(accessGuard).requireUpdate(OWNER, "ExportRelease", null, "farm-1", "zone-1");
    }

    @Test
    void duplicateActiveExportReleaseForSameHarvestBatchIsRejected() {
        HarvestBatch batch = batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED);
        when(harvestBatchRepository.findById("batch-1")).thenReturn(Optional.of(batch));
        when(exportReleaseRepository.findByHarvestBatchId("batch-1"))
                .thenReturn(List.of(release("release-1", "batch-1", ExportReleaseStatus.DRAFT)));

        assertThatThrownBy(() -> service.createExportRelease(OWNER, exportRequest("batch-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
        verify(complianceAssessmentRepository, never()).save(any(ComplianceAssessment.class));
    }

    @Test
    void complianceAssessmentRejectsHarvestBatchFromDifferentSeason() {
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-2", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED)));

        assertThatThrownBy(() -> service.assessCompliance(
                "season-1",
                new CultivationCalendarDtos.AssessComplianceRequest("batch-1", "VN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");

        verify(complianceAssessmentRepository, never()).save(any(ComplianceAssessment.class));
    }

    @Test
    void submitExportReleaseRejectsIneligibleResolvedHarvestBatch() {
        when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release("release-1", "batch-1", ExportReleaseStatus.DRAFT)));
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.CANCELLED)));

        assertThatThrownBy(() -> service.submitExportRelease(OWNER, "release-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not eligible");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void submitExportReleaseRejectsNonDraftDossier() {
        when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release("release-1", "batch-1", ExportReleaseStatus.APPROVED)));
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED)));

        assertThatThrownBy(() -> service.submitExportRelease(OWNER, "release-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only draft");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void approveExportReleaseRejectsInvalidTransition() {
        when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release("release-1", "batch-1", ExportReleaseStatus.DRAFT)));
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED)));

        assertThatThrownBy(() -> service.approveExportRelease(OWNER, "release-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("under review");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void releaseExportReleaseRejectsIneligibleResolvedHarvestBatch() {
        when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release("release-1", "batch-1", ExportReleaseStatus.APPROVED)));
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.BLOCKED)));

        assertThatThrownBy(() -> service.releaseExportRelease(OWNER, "release-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not eligible");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void releaseExportReleaseRejectsNonApprovedDossier() {
        when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release("release-1", "batch-1", ExportReleaseStatus.UNDER_REVIEW)));
        when(harvestBatchRepository.findById("batch-1"))
                .thenReturn(Optional.of(batch("batch-1", "season-1", "farm-1", "zone-1", HarvestBatchStatus.HARVESTED)));

        assertThatThrownBy(() -> service.releaseExportRelease(OWNER, "release-1", OWNER.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only approved");

        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void seasonEndpointResolvesSeasonScopesBeforeSafeHarvestCalculation() {
        when(seasonRepository.findById("season-1")).thenReturn(Optional.of(season("farm-2", "zone-9")));
        org.mockito.Mockito.doThrow(new CultivationAccessDeniedException("denied"))
                .when(accessGuard)
                .requireView(OWNER, "CultivationSeason", "season-1", "farm-2", "zone-9");

        assertThatThrownBy(() -> service.safeHarvestDate(OWNER, "season-1"))
                .isInstanceOf(CultivationAccessDeniedException.class);
    }

    private CultivationPlan plan(String id, String farmId, String plotId) {
        return new CultivationPlan(
                id,
                farmId,
                plotId,
                "season-1",
                null,
                "Plan",
                LocalDate.now(),
                null,
                List.of("VN"),
                PlanStatus.ACTIVE,
                "owner-1",
                Instant.now(),
                Instant.now());
    }

    @Test
    void chemicalCompletionPersistsExecutionUsageAndSnapshots() {
        prepareCompletion(ActivityType.CHEMICAL_TREATMENT, input("input-1", InputStatus.VERIFIED, BiologicalLevel.CHEMICAL));
        when(safeHarvestDateCalculator.safeHarvestDate(LocalDate.of(2026, 8, 12), 7))
                .thenReturn(LocalDate.of(2026, 8, 19));

        var result = service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "500")), OWNER.userId()));

        CultivationActivity completed = (CultivationActivity) result.activity();
        ActivityExecution execution = (ActivityExecution) result.execution();
        assertThat(completed.status()).isEqualTo(ActivityStatus.COMPLETED);
        assertThat(execution.cultivationActivityId()).isEqualTo("activity-1");
        assertThat(execution.executedBy()).isEqualTo(OWNER.userId());
        assertThat(result.inputUsages()).hasSize(1);
        ActivityInputUsage saved = (ActivityInputUsage) result.inputUsages().getFirst();
        assertThat(saved.activityExecutionId()).isEqualTo(execution.id());
        assertThat(saved.agriculturalInputId()).isEqualTo("input-1");
        assertThat(saved.quantityUsed()).isEqualByComparingTo("500");
        assertThat(saved.agriculturalInputSnapshot().productName()).isEqualTo("Thuốc A");
        assertThat(saved.activeIngredientSnapshots()).extracting(ActiveIngredientSnapshot::code).containsExactly("AI");
        assertThat(saved.agriculturalInputSnapshot().preHarvestIntervalDays()).isEqualTo(7);
        assertThat(saved.calculatedSafeHarvestDate()).isEqualTo(LocalDate.of(2026, 8, 19));

        when(activityRepository.findAll()).thenReturn(List.of(completed));
        when(executionRepository.findByCultivationActivityId(completed.id())).thenReturn(List.of(execution));
        when(inputUsageRepository.findByActivityExecutionId(execution.id())).thenReturn(List.of(saved));
        when(safeHarvestDateCalculator.earliestSafeHarvestDate(any())).thenCallRealMethod();
        assertThat(service.safeHarvestDate("season-1")).contains(LocalDate.of(2026, 8, 19));
    }

    @Test
    void chemicalCompletionWithoutUsageIsRejectedBeforeWrites() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.SCHEDULED)));

        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(), OWNER.userId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("At least one");
        verify(executionRepository, never()).save(any());
    }

    @Test
    void fertilizationCompletionWithUsageSucceeds() {
        prepareCompletion(ActivityType.FERTILIZATION, input("input-1", InputStatus.VERIFIED, BiologicalLevel.CHEMICAL));
        assertThat(service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "2")), OWNER.userId()))
                .inputUsages()).hasSize(1);
    }

    @Test
    void missingProductIsRejectedBeforeWrites() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.SCHEDULED)));

        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("missing", "1")), OWNER.userId())))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Agricultural input");
        verify(executionRepository, never()).save(any());
    }

    @Test
    void prohibitedProductIsRejectedBeforeWrites() {
        prepareInvalidProduct(input("input-1", InputStatus.PROHIBITED, BiologicalLevel.PROHIBITED));
        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "1")), OWNER.userId())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(executionRepository, never()).save(any());
    }

    @Test
    void unverifiedProductIsRejectedBeforeWrites() {
        prepareInvalidProduct(input("input-1", InputStatus.DRAFT, BiologicalLevel.CHEMICAL));
        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "1")), OWNER.userId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("verified");
        verify(executionRepository, never()).save(any());
    }

    @Test
    void invalidQuantityIsRejectedBeforeWrites() {
        prepareInvalidProduct(input("input-1", InputStatus.VERIFIED, BiologicalLevel.CHEMICAL));
        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "0")), OWNER.userId())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Quantity");
        verify(executionRepository, never()).save(any());
    }

    @Test
    void invalidSecondUsageDoesNotSaveFirstUsageOrExecution() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.SCHEDULED)));
        when(agriculturalInputRepository.findById("input-1"))
                .thenReturn(Optional.of(input("input-1", InputStatus.VERIFIED, BiologicalLevel.CHEMICAL)));
        when(agriculturalInputRepository.findById("input-2"))
                .thenReturn(Optional.of(input("input-2", InputStatus.DRAFT, BiologicalLevel.CHEMICAL)));

        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(
                List.of(usage("input-1", "500"), usage("input-2", "10")), OWNER.userId())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(executionRepository, never()).save(any());
        verify(inputUsageRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void completedActivityCannotBeCompletedTwice() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.COMPLETED)));
        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "1")), OWNER.userId())))
                .isInstanceOf(IllegalStateException.class);
        verify(executionRepository, never()).save(any());
    }

    @Test
    void careHistoryReturnsActivityExecutionAndUsage() {
        CultivationActivity activity = completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.COMPLETED);
        ActivityExecution execution = new ActivityExecution("execution-1", activity.id(), Instant.now(), Instant.now(),
                OWNER.userId(), null, null, null, null, Map.of(), null, null, null, null, List.of(), true, Instant.now());
        ActivityInputUsage usage = new ActivityInputUsage("usage-1", execution.id(), "input-1", null,
                null, null, BigDecimal.ONE, "ml", null, null, null, null, null, null, List.of(), null, Instant.now());
        when(activityRepository.findAll()).thenReturn(List.of(activity));
        when(accessGuard.canView(OWNER, activity.farmId(), activity.plotId())).thenReturn(true);
        when(executionRepository.findByCultivationActivityId(activity.id())).thenReturn(List.of(execution));
        when(inputUsageRepository.findByActivityExecutionId(execution.id())).thenReturn(List.of(usage));

        var history = service.careHistory(OWNER, "season-1");
        assertThat(history.activities()).isEqualTo(List.of(activity));
        assertThat(history.executions()).isEqualTo(List.of(execution));
        assertThat(history.inputUsages()).isEqualTo(List.of(usage));
    }

    @Test
    void clientCannotClaimAnotherExecutedByUser() {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.SCHEDULED)));
        assertThatThrownBy(() -> service.completeActivity(OWNER, "activity-1", completionRequest(List.of(usage("input-1", "1")), "someone-else")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("authenticated actor");
        verify(executionRepository, never()).save(any());
    }

    private void prepareCompletion(ActivityType type, AgriculturalInput input) {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(type, ActivityStatus.SCHEDULED)));
        when(agriculturalInputRepository.findById(input.id())).thenReturn(Optional.of(input));
        when(executionRepository.save(any(ActivityExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inputUsageRepository.save(any(ActivityInputUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void prepareInvalidProduct(AgriculturalInput input) {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(completionActivity(ActivityType.CHEMICAL_TREATMENT, ActivityStatus.SCHEDULED)));
        when(agriculturalInputRepository.findById(input.id())).thenReturn(Optional.of(input));
    }

    private CultivationCalendarDtos.ActivityInputUsageRequest usage(String inputId, String quantity) {
        return new CultivationCalendarDtos.ActivityInputUsageRequest(inputId, "LOT-1", null,
                new BigDecimal(quantity), "ml", null, null, null, null, null, null);
    }

    private CultivationCalendarDtos.CompleteActivityRequest completionRequest(
            List<CultivationCalendarDtos.ActivityInputUsageRequest> usages, String executedBy) {
        Instant started = Instant.parse("2026-08-12T07:30:00Z");
        return new CultivationCalendarDtos.CompleteActivityRequest(started, started.plusSeconds(3600), executedBy,
                null, null, null, null, Map.of(), null, null, "Đã thực hiện", null, List.of(), usages);
    }

    private CultivationActivity completionActivity(ActivityType type, ActivityStatus status) {
        return new CultivationActivity("activity-1", "plan-1", "season-1", "farm-1", "zone-1",
                List.of(), type, "Phun thuốc", null, Instant.now(), null, null, "NORMAL", status,
                List.of(), false, null, null, null, null, Instant.now(), Instant.now());
    }

    private AgriculturalInput input(String id, InputStatus status, BiologicalLevel level) {
        return new AgriculturalInput(id, "CODE-" + id, "Thuốc A", null, "Nhà sản xuất", "REG-1",
                InputCategory.CHEMICAL_PESTICIDE, level, null, "ml",
                List.of(new ActiveIngredientSnapshot("AI", "Hoạt chất", BigDecimal.ONE, "g/L")),
                List.of(), "500 ml/ha", null, 7, null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), status, null, null, Instant.now(), Instant.now());
    }

    private CultivationActivity activity(String id, String farmId, String plotId) {
        return activity(id, farmId, plotId, ActivityStatus.SCHEDULED, false);
    }

    private CultivationActivity activity(String id, String farmId, String plotId, ActivityStatus status, boolean approvalRequired) {
        return new CultivationActivity(
                id,
                "plan-1",
                "season-1",
                farmId,
                plotId,
                List.of(),
                ActivityType.FERTILIZATION,
                "Task",
                null,
                Instant.now(),
                null,
                null,
                "NORMAL",
                status,
                List.of(),
                approvalRequired,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    private void approvalDeniedByGuard(String farmId, String plotId, String message) {
        when(activityRepository.findById("activity-1"))
                .thenReturn(Optional.of(activity("activity-1", farmId, plotId, ActivityStatus.PENDING_APPROVAL, true)));
        org.mockito.Mockito.doThrow(new CultivationAccessDeniedException(message))
                .when(accessGuard)
                .requireUpdate(OWNER, "CultivationActivity", "activity-1", farmId, plotId);

        assertThatThrownBy(() -> service.approveActivity(OWNER, "activity-1", OWNER.userId()))
                .isInstanceOf(CultivationAccessDeniedException.class)
                .hasMessageContaining(message);
    }

    private CultivationCalendarDtos.CompleteActivityRequest completeRequest() {
        Instant startedAt = Instant.parse("2026-08-12T07:30:00Z");
        return new CultivationCalendarDtos.CompleteActivityRequest(
                startedAt,
                startedAt.plusSeconds(3600),
                OWNER.userId(),
                null,
                0,
                BigDecimal.ZERO,
                null,
                Map.of(),
                null,
                null,
                null,
                null,
                List.of(),
                List.of());
    }

    private HarvestBatch batch(String id, String farmId, String plotId) {
        return batch(id, "season-1", farmId, plotId, HarvestBatchStatus.HARVESTED);
    }

    private HarvestBatch batch(String id, String cultivationSeasonId, String farmId, String plotId, HarvestBatchStatus status) {
        return new HarvestBatch(
                id,
                "BATCH-" + id,
                cultivationSeasonId,
                farmId,
                plotId,
                Instant.now(),
                BigDecimal.TEN,
                "kg",
                "VN",
                null,
                RiskLevel.LOW,
                status,
                "owner-1",
                Instant.now());
    }

    private CultivationCalendarDtos.CreateExportReleaseRequest exportRequest(String harvestBatchId) {
        return new CultivationCalendarDtos.CreateExportReleaseRequest(
                "REL-" + harvestBatchId,
                harvestBatchId,
                "VN",
                OWNER.userId());
    }

    private LabSample sample(String id, String harvestBatchId) {
        return new LabSample(
                id,
                "season-1",
                harvestBatchId,
                "SAMPLE-" + id,
                SampleType.FRUIT,
                Instant.now(),
                "owner-1",
                null,
                "Lab",
                null,
                LabSampleStatus.COLLECTED,
                List.of(),
                Instant.now());
    }

    private ExportRelease release(String id, String harvestBatchId) {
        return release(id, harvestBatchId, ExportReleaseStatus.DRAFT);
    }

    private ExportRelease release(String id, String harvestBatchId, ExportReleaseStatus status) {
        return new ExportRelease(
                id,
                "REL-" + id,
                harvestBatchId,
                "VN",
                Map.of(),
                Map.of(),
                status,
                "owner-1",
                null,
                null,
                null,
                null,
                null,
                Instant.now());
    }
}
