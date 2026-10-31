package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.cultivation.api.CultivationCalendarDtos;
import com.duriancare.cultivation.compliance.ComplianceEngine;
import com.duriancare.cultivation.compliance.LabResidueComparator;
import com.duriancare.cultivation.compliance.SafeHarvestDateCalculator;
import com.duriancare.cultivation.domain.ActiveIngredientSnapshot;
import com.duriancare.cultivation.domain.ActivityExecution;
import com.duriancare.cultivation.domain.ActivityInputUsage;
import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.AgriculturalInputSnapshot;
import com.duriancare.cultivation.domain.AuditLog;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceAssessment;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.CultivationActivity;
import com.duriancare.cultivation.domain.CultivationSeason;
import com.duriancare.cultivation.domain.ExportRelease;
import com.duriancare.cultivation.domain.ExportReleaseStatus;
import com.duriancare.cultivation.domain.HarvestBatch;
import com.duriancare.cultivation.domain.HarvestBatchStatus;
import com.duriancare.cultivation.domain.InputCategory;
import com.duriancare.cultivation.domain.LabResidueResult;
import com.duriancare.cultivation.domain.LabResultStatus;
import com.duriancare.cultivation.domain.LabSample;
import com.duriancare.cultivation.domain.LabSampleStatus;
import com.duriancare.cultivation.domain.ResidueStandard;
import com.duriancare.cultivation.domain.RiskLevel;
import com.duriancare.cultivation.domain.SampleType;
import com.duriancare.cultivation.domain.SourceType;
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
import com.duriancare.cultivation.security.CultivationAccessGuard;
import com.duriancare.cultivation.security.CultivationAccessDeniedException;
import com.duriancare.cultivation.security.CultivationActor;
import java.math.BigDecimal;
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
class CultivationPhase2ServiceTest {
    private static final CultivationActor EXPERT = new CultivationActor("expert-1", "expert@example.com", "EXPERT");
    private static final CultivationActor FARMER = new CultivationActor("farmer-1", "farmer@example.com", "FARMER");
    private static final Instant SAMPLED_AT = Instant.parse("2026-08-18T02:00:00Z");
    private static final Instant HARVESTED_AT = Instant.parse("2026-08-25T01:00:00Z");

    @Mock CultivationPlanRepository planRepository;
    @Mock CultivationSeasonRepository seasonRepository;
    @Mock CultivationActivityRepository activityRepository;
    @Mock ActivityExecutionRepository executionRepository;
    @Mock ActivityInputUsageRepository inputUsageRepository;
    @Mock AgriculturalInputRepository agriculturalInputRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ResidueStandardRepository residueStandardRepository;
    @Mock LabSampleRepository labSampleRepository;
    @Mock LabResidueResultRepository labResidueResultRepository;
    @Mock HarvestBatchRepository harvestBatchRepository;
    @Mock ExportReleaseRepository exportReleaseRepository;
    @Mock ComplianceAssessmentRepository complianceAssessmentRepository;
    @Mock CultivationAccessGuard accessGuard;
    @Mock com.duriancare.cultivation.repository.LegacyIntegrityMarkerRepository integrityMarkerRepository;
    CultivationCalendarService service;

    @BeforeEach
    void setUp() {
        service = new CultivationCalendarService(planRepository, seasonRepository, activityRepository,
                executionRepository, inputUsageRepository, agriculturalInputRepository, auditLogRepository,
                residueStandardRepository, labSampleRepository, labResidueResultRepository,
                harvestBatchRepository, exportReleaseRepository, complianceAssessmentRepository,
                new SafeHarvestDateCalculator(), new ComplianceEngine(), new LabResidueComparator(), accessGuard,
                integrityMarkerRepository);
        lenient().when(seasonRepository.findById("season-1")).thenReturn(Optional.of(season("farm-1", "zone-1")));
        lenient().when(seasonRepository.existsById("season-1")).thenReturn(true);
        lenient().when(planRepository.findById("plan-1")).thenReturn(Optional.of(new com.duriancare.cultivation.domain.CultivationPlan(
                "plan-1", "farm-1", "zone-1", "season-1", null, "Plan", LocalDate.parse("2026-01-01"),
                null, List.of("VN"), com.duriancare.cultivation.domain.PlanStatus.ACTIVE,
                "farmer-1", Instant.now(), Instant.now())));
        lenient().when(harvestBatchRepository.findById("batch-1")).thenReturn(Optional.of(batch()));
        lenient().when(accessGuard.canView(any(), any(), any())).thenReturn(true);
        lenient().when(labResidueResultRepository.save(any(LabResidueResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(labSampleRepository.save(any(LabSample.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(exportReleaseRepository.save(any(ExportRelease.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(complianceAssessmentRepository.save(any(ComplianceAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void clientPassAndLargeMrlCannotOverrideServerFailureOrAuditActor() {
        labSample(LabSampleStatus.SENT);
        standards(standard(true, true, null));
        LabResidueResult result = service.createLabResult(EXPERT,
                request(new BigDecimal("2"), "mg/kg", new BigDecimal("999999"), LabResultStatus.PASS));
        assertThat(result.resultStatus()).isEqualTo(LabResultStatus.FAIL);
        assertThat(result.applicableMrl()).isEqualByComparingTo("1");
        assertThat(result.residueStandardId()).isEqualTo("standard-1");
        assertThat(result.verifiedBy()).isEqualTo(EXPERT.userId());
        assertThat(result.evaluationPolicyVersion()).isEqualTo("SERVER_MRL_V1");
        verify(accessGuard).requireUpdate(EXPERT, "LabResidueResult", null, "farm-1", "zone-1");
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().actorId()).isEqualTo(EXPERT.userId());
    }

    @Test
    void noStandardIsNotPass() {
        labSample(LabSampleStatus.SENT);
        LabResidueResult result = service.createLabResult(EXPERT,
                request(BigDecimal.ZERO, "mg/kg", new BigDecimal("999999"), LabResultStatus.PASS));
        assertThat(result.resultStatus()).isEqualTo(LabResultStatus.NO_STANDARD_FOUND);
        assertThat(result.applicableMrl()).isNull();
    }

    @Test
    void inactiveStandardIsIgnored() {
        assertMissingStandardFor(standard(false, true, null));
    }

    @Test
    void unverifiedStandardIsIgnored() {
        assertMissingStandardFor(standard(true, false, null));
    }

    @Test
    void expiredStandardIsIgnoredAtSampleDate() {
        assertMissingStandardFor(standard(true, true, LocalDate.parse("2026-08-17")));
    }

    @Test
    void laterEffectiveStandardIsIgnoredAtSampleDate() {
        ResidueStandard later = new ResidueStandard("later", "VN", "DURIAN", "Durian", "AI", "Ingredient",
                BigDecimal.ONE, "mg/kg", LocalDate.parse("2026-08-19"), null, SourceType.VIETNAM,
                "catalog", null, true, true, Instant.now(), Instant.now());
        assertMissingStandardFor(later);
    }

    @Test
    void latestEffectiveVerifiedStandardWins() {
        labSample(LabSampleStatus.SENT);
        ResidueStandard older = standard(true, true, null);
        ResidueStandard newer = new ResidueStandard("newer", "VN", "DURIAN", "Durian", "AI",
                "Ingredient", new BigDecimal("0.25"), "mg/kg", LocalDate.parse("2026-08-01"), null,
                SourceType.VIETNAM, "new catalog", null, true, true, Instant.now(), Instant.now());
        standards(older, newer);
        LabResidueResult result = service.createLabResult(EXPERT,
                request(new BigDecimal("0.5"), "mg/kg", null, null));
        assertThat(result.residueStandardId()).isEqualTo("newer");
        assertThat(result.resultStatus()).isEqualTo(LabResultStatus.FAIL);
    }

    @Test
    void sameDateStandardsAreAmbiguousRatherThanChoosingArbitrarily() {
        labSample(LabSampleStatus.SENT);
        ResidueStandard first = standard(true, true, null);
        ResidueStandard second = new ResidueStandard("another", "VN", "DURIAN", "Durian", "AI",
                "Ingredient", new BigDecimal("3"), "mg/kg", first.effectiveFrom(), null,
                SourceType.VIETNAM, "other catalog", null, true, true, Instant.now(), Instant.now());
        standards(first, second);
        assertThat(service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null)).resultStatus())
                .isEqualTo(LabResultStatus.NO_STANDARD_FOUND);
    }

    @Test
    void incompatibleUnitDoesNotPass() {
        labSample(LabSampleStatus.SENT);
        standards(standard(true, true, null));
        assertThat(service.createLabResult(EXPERT,
                request(new BigDecimal("500"), "ug/kg", null, null)).resultStatus())
                .isEqualTo(LabResultStatus.PENDING_REVIEW);
    }

    @Test
    void unsupportedUnitDoesNotPassEvenWhenStringsMatch() {
        labSample(LabSampleStatus.SENT);
        ResidueStandard unknownUnit = new ResidueStandard("standard-1", "VN", "DURIAN", "Durian", "AI",
                "Ingredient", BigDecimal.ONE, "unknown", LocalDate.parse("2026-01-01"), null,
                SourceType.VIETNAM, "catalog", null, true, true, Instant.now(), Instant.now());
        standards(unknownUnit);
        assertThat(service.createLabResult(EXPERT, request(BigDecimal.ZERO, "unknown", null, null)).resultStatus())
                .isEqualTo(LabResultStatus.PENDING_REVIEW);
    }

    @Test
    void duplicateIngredientInOneSampleIsRejected() {
        labSample(LabSampleStatus.SENT);
        when(labResidueResultRepository.findByLabSampleId("sample-1")).thenReturn(List.of(result("SERVER_MRL_V1", BigDecimal.ZERO)));
        assertThatThrownBy(() -> service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
        verify(labResidueResultRepository, never()).save(any(LabResidueResult.class));
    }

    @Test
    void collectedSampleCannotReceiveResult() {
        labSample(LabSampleStatus.COLLECTED);
        assertThatThrownBy(() -> service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sent samples");
    }

    @Test
    void completePanelUpdatesSampleStatus() {
        labSample(LabSampleStatus.SENT);
        standards(standard(true, true, null));
        service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null));
        ArgumentCaptor<LabSample> sample = ArgumentCaptor.forClass(LabSample.class);
        verify(labSampleRepository).save(sample.capture());
        assertThat(sample.getValue().status()).isEqualTo(LabSampleStatus.RESULT_RECORDED);
    }

    @Test
    void sampleCannotClaimResultRecordedAtCreation() {
        var request = new CultivationCalendarDtos.CreateLabSampleRequest("season-1", "batch-1", "SAMPLE-NEW",
                SampleType.FRUIT, SAMPLED_AT, "person-at-lab", null, "Lab", null,
                LabSampleStatus.RESULT_RECORDED, List.of(), List.of("AI"));
        LabSample saved = service.createLabSample(FARMER, request);
        assertThat(saved.status()).isEqualTo(LabSampleStatus.COLLECTED);
        assertThat(saved.expectedActiveIngredientCodes()).containsExactly("AI");
    }

    @Test
    void futureSampleDateCannotSelectFutureStandard() {
        var request = new CultivationCalendarDtos.CreateLabSampleRequest("season-1", "batch-1", "SAMPLE-FUTURE",
                SampleType.FRUIT, Instant.parse("2100-01-01T00:00:00Z"), "person-at-lab", null, "Lab", null,
                LabSampleStatus.COLLECTED, List.of(), List.of("AI"));
        assertThatThrownBy(() -> service.createLabSample(FARMER, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
    }

    @Test
    void nonFruitSampleCannotProvideHarvestResidueResult() {
        LabSample leaf = new LabSample("sample-1", "season-1", "batch-1", "SAMPLE-LEAF", SampleType.LEAF,
                SAMPLED_AT, "farmer-1", null, "Lab", null, LabSampleStatus.SENT,
                List.of(), Instant.now(), List.of("AI"));
        when(labSampleRepository.findById("sample-1")).thenReturn(Optional.of(leaf));
        assertThatThrownBy(() -> service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fruit");
    }

    @Test
    void incompleteMultiIngredientPanelStaysPending() {
        LabSample multi = new LabSample("sample-1", "season-1", "batch-1", "SAMPLE-1", SampleType.FRUIT,
                SAMPLED_AT, "farmer-1", null, "Lab", null, LabSampleStatus.SENT,
                List.of(), Instant.now(), List.of("AI", "OTHER"));
        when(labSampleRepository.findById("sample-1")).thenReturn(Optional.of(multi));
        standards(standard(true, true, null));
        service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null));
        verify(labSampleRepository, never()).save(any(LabSample.class));
    }

    @Test
    void lodAboveMrlRequiresReview() {
        labSample(LabSampleStatus.SENT);
        standards(standard(true, true, null));
        var request = new CultivationCalendarDtos.CreateLabResultRequest("sample-1", "AI", "Ingredient",
                null, "mg/kg", new BigDecimal("2"), new BigDecimal("3"), null, null, null, null);
        assertThat(service.createLabResult(EXPERT, request).resultStatus())
                .isEqualTo(LabResultStatus.PENDING_REVIEW);
    }

    @Test
    void batchFarmMismatchIsRejected() {
        var request = harvestRequest("farm-2", "zone-1");
        assertThatThrownBy(() -> service.createHarvestBatch(FARMER, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must match");
        verify(harvestBatchRepository, never()).save(any(HarvestBatch.class));
    }

    @Test
    void batchZoneMismatchIsRejected() {
        var request = harvestRequest("farm-1", "zone-2");
        assertThatThrownBy(() -> service.createHarvestBatch(FARMER, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must match");
    }

    @Test
    void batchCreatorComesFromAuthenticatedActor() {
        when(harvestBatchRepository.save(any(HarvestBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        HarvestBatch saved = service.createHarvestBatch(FARMER, harvestRequest("farm-1", "zone-1"));
        assertThat(saved.createdBy()).isEqualTo(FARMER.userId());
    }

    @Test
    void marketCannotBeChangedToAvoidBatchStandard() {
        var request = new CultivationCalendarDtos.AssessComplianceRequest("batch-1", "EU");
        assertThatThrownBy(() -> service.assessCompliance("season-1", request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Target market");
    }

    @Test
    void ordinaryUserCannotCreateVerifiedResidueStandard() {
        var request = new CultivationCalendarDtos.CreateResidueStandardRequest("VN", "DURIAN", "Durian",
                "AI", "Ingredient", BigDecimal.ONE, "mg/kg", LocalDate.parse("2026-01-01"), null,
                SourceType.VIETNAM, "catalog", true, true);
        assertThatThrownBy(() -> service.createResidueStandard(FARMER, request))
                .isInstanceOf(CultivationAccessDeniedException.class);
        verify(residueStandardRepository, never()).save(any(ResidueStandard.class));
    }

    @Test
    void blockedReleaseCannotSubmit() {
        exportRelease(ExportReleaseStatus.BLOCKED);
        assertThatThrownBy(() -> service.submitExportRelease(FARMER, "release-1", "spoofed"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DRAFT");
        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void draftReleaseCannotApprove() {
        exportRelease(ExportReleaseStatus.DRAFT);
        assertThatThrownBy(() -> service.approveExportRelease(EXPERT, "release-1", "spoofed"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void approvalReassessesAndRejectsLatestLabFailure() {
        exportRelease(ExportReleaseStatus.UNDER_REVIEW);
        failedLab();
        assertThatThrownBy(() -> service.approveExportRelease(EXPERT, "release-1", "spoofed"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LAB_RESULT_FAILED");
        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void releaseReassessesAndRejectsNewLabFailure() {
        exportRelease(ExportReleaseStatus.APPROVED);
        failedLab();
        assertThatThrownBy(() -> service.releaseExportRelease(EXPERT, "release-1", "spoofed"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LAB_RESULT_FAILED");
        verify(exportReleaseRepository, never()).save(any(ExportRelease.class));
    }

    @Test
    void releaseReassessesAndRejectsNewPhiViolation() {
        exportRelease(ExportReleaseStatus.APPROVED);
        var activity = activity();
        when(activityRepository.findAll()).thenReturn(List.of(activity));
        when(executionRepository.findByCultivationActivityId(activity.id())).thenReturn(List.of(execution()));
        when(inputUsageRepository.findByActivityExecutionId("execution-1"))
                .thenReturn(List.of(usage(LocalDate.parse("2026-08-26"))));
        standards(standard(true, true, null));
        assertThatThrownBy(() -> service.releaseExportRelease(EXPERT, "release-1", "spoofed"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRE_HARVEST_INTERVAL_NOT_MET");
    }

    @Test
    void missingChemicalPhiBlocksCompliance() {
        var activity = activity();
        when(activityRepository.findAll()).thenReturn(List.of(activity));
        when(executionRepository.findByCultivationActivityId(activity.id())).thenReturn(List.of(execution()));
        ActivityInputUsage original = usage(LocalDate.parse("2026-08-20"));
        AgriculturalInputSnapshot noPhi = new AgriculturalInputSnapshot("INPUT", "Product", null, null,
                InputCategory.CHEMICAL_PESTICIDE, BiologicalLevel.CHEMICAL, null,
                original.activeIngredientSnapshots(), List.of("VN"), List.of());
        ActivityInputUsage incomplete = new ActivityInputUsage(original.id(), original.activityExecutionId(),
                original.agriculturalInputId(), noPhi, original.batchNumber(), original.expiryDate(),
                original.quantityUsed(), original.quantityUnit(), original.waterVolume(), original.waterVolumeUnit(),
                original.concentration(), original.concentrationUnit(), original.treatedArea(), original.areaUnit(),
                original.activeIngredientSnapshots(), original.calculatedSafeHarvestDate(), original.createdAt());
        when(inputUsageRepository.findByActivityExecutionId("execution-1")).thenReturn(List.of(incomplete));
        standards(standard(true, true, null));
        ComplianceAssessment assessment = service.assessCompliance("season-1",
                new CultivationCalendarDtos.AssessComplianceRequest("batch-1", "VN"));
        assertThat(assessment.blockingReasons()).contains(BlockingReason.MISSING_PRE_HARVEST_INTERVAL);
    }

    @Test
    void releaseWithCurrentValidDataRebuildsSnapshotAndUsesAuthenticatedReviewer() {
        exportRelease(ExportReleaseStatus.APPROVED);
        passingLab();
        ExportRelease released = service.releaseExportRelease(EXPERT, "release-1", "spoofed");
        assertThat(released.status()).isEqualTo(ExportReleaseStatus.RELEASED);
        assertThat(released.reviewedBy()).isEqualTo(EXPERT.userId());
        assertThat(released.traceabilitySnapshot()).containsKeys("labSamples", "labResidueResults", "resolvedLabEvidence");
        assertThat(released.traceabilitySnapshot()).doesNotContainKey("stale");
        assertThat(released.complianceAssessmentSnapshot()).containsKey("assessedAt");
        assertThat(released.traceabilitySnapshot().get("labAssessmentStatus")).isEqualTo("ASSESSED");
    }

    @Test
    void noLabSampleIsExplicitlyNotAssessed() {
        ComplianceAssessment assessment = service.assessCompliance("season-1",
                new CultivationCalendarDtos.AssessComplianceRequest("batch-1", "VN"));
        assertThat(assessment.warnings()).contains(ComplianceWarning.LAB_NOT_ASSESSED);
        assertThat(assessment.eligibleForExportRelease()).isTrue();
    }

    @Test
    void legacyLabEvidenceIsBlocked() {
        when(labSampleRepository.findByCultivationSeasonId("season-1"))
                .thenReturn(List.of(sample(LabSampleStatus.RESULT_RECORDED)));
        when(labResidueResultRepository.findByLabSampleId("sample-1"))
                .thenReturn(List.of(result(null, BigDecimal.ZERO)));
        ComplianceAssessment assessment = service.assessCompliance("season-1",
                new CultivationCalendarDtos.AssessComplianceRequest("batch-1", "VN"));
        assertThat(assessment.blockingReasons()).contains(BlockingReason.LAB_EVIDENCE_UNVERIFIED);
        assertThat(assessment.eligibleForExportRelease()).isFalse();
    }

    @Test
    void legacySampleWithoutSamplingTimeStillCreatesBlockedReleaseSnapshot() {
        LabSample legacy = new LabSample("sample-1", "season-1", "batch-1", "SAMPLE-LEGACY",
                SampleType.FRUIT, null, "farmer-1", null, "Lab", null,
                LabSampleStatus.RESULT_RECORDED, List.of(), Instant.now(), List.of("AI"));
        when(labSampleRepository.findByCultivationSeasonId("season-1")).thenReturn(List.of(legacy));
        when(labResidueResultRepository.findByLabSampleId("sample-1"))
                .thenReturn(List.of(result(null, BigDecimal.ZERO)));

        ExportRelease release = service.createExportRelease(FARMER,
                new CultivationCalendarDtos.CreateExportReleaseRequest("REL-LEGACY", "batch-1", "VN", "spoofed"));

        assertThat(release.status()).isEqualTo(ExportReleaseStatus.BLOCKED);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) release.traceabilitySnapshot()
                .get("resolvedLabEvidence");
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).get("sampledOn")).isNull();
        assertThat(evidence.get(0).get("liveResultStatus")).isEqualTo(LabResultStatus.PENDING_REVIEW);
    }

    @Test
    void resultWithoutCurrentVerifiedStandardBlocksExport() {
        when(labSampleRepository.findByCultivationSeasonId("season-1"))
                .thenReturn(List.of(sample(LabSampleStatus.RESULT_RECORDED)));
        when(labResidueResultRepository.findByLabSampleId("sample-1"))
                .thenReturn(List.of(result("SERVER_MRL_V1", BigDecimal.ZERO)));
        ComplianceAssessment assessment = service.assessCompliance("season-1",
                new CultivationCalendarDtos.AssessComplianceRequest("batch-1", "VN"));
        assertThat(assessment.blockingReasons()).contains(BlockingReason.NO_APPLICABLE_MRL);
    }

    @Test
    void recalledReleaseResponseShowsCurrentStatusAndReason() {
        var release = exportRelease(ExportReleaseStatus.RELEASED);
        when(exportReleaseRepository.findById("release-1"))
                .thenReturn(Optional.of(release), Optional.of(release));
        ExportRelease recalled = service.recallExportRelease(EXPERT, "release-1", "spoofed", "Lab alert");
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        when(auditLogRepository.findByResourceTypeAndResourceId("ExportRelease", "release-1"))
                .thenReturn(List.of(audit.getValue()));
        when(exportReleaseRepository.findById("release-1")).thenReturn(Optional.of(recalled));
        var response = service.traceability(EXPERT, "release-1");
        assertThat(response.status()).isEqualTo(ExportReleaseStatus.RECALLED);
        assertThat(response.recallReason()).isEqualTo("Lab alert");
        assertThat(response.recalledAt()).isNotNull();
    }

    private void assertMissingStandardFor(ResidueStandard candidate) {
        labSample(LabSampleStatus.SENT);
        standards(candidate);
        assertThat(service.createLabResult(EXPERT, request(BigDecimal.ZERO, "mg/kg", null, null)).resultStatus())
                .isEqualTo(LabResultStatus.NO_STANDARD_FOUND);
    }

    private void labSample(LabSampleStatus status) {
        when(labSampleRepository.findById("sample-1")).thenReturn(Optional.of(sample(status)));
    }

    private void standards(ResidueStandard... standards) {
        when(residueStandardRepository
                .findByMarketCodeAndCommodityCodeAndActiveIngredientCodeAndActiveTrueAndVerifiedTrueOrderByEffectiveFromDesc(
                        "VN", "DURIAN", "AI")).thenReturn(List.of(standards));
    }

    private void failedLab() {
        when(labSampleRepository.findByCultivationSeasonId("season-1"))
                .thenReturn(List.of(sample(LabSampleStatus.RESULT_RECORDED)));
        when(labResidueResultRepository.findByLabSampleId("sample-1"))
                .thenReturn(List.of(result("SERVER_MRL_V1", new BigDecimal("2"))));
        standards(standard(true, true, null));
    }

    private void passingLab() {
        when(labSampleRepository.findByCultivationSeasonId("season-1"))
                .thenReturn(List.of(sample(LabSampleStatus.RESULT_RECORDED)));
        when(labResidueResultRepository.findByLabSampleId("sample-1"))
                .thenReturn(List.of(result("SERVER_MRL_V1", new BigDecimal("0.5"))));
        standards(standard(true, true, null));
    }

    private CultivationSeason season(String farmId, String plotId) {
        return new CultivationSeason("season-1", farmId, plotId, "Season", "DURIAN", "Ri6",
                LocalDate.parse("2026-01-01"), null, "farmer-1", Instant.now(), Instant.now());
    }

    private HarvestBatch batch() {
        return new HarvestBatch("batch-1", "BATCH-1", "season-1", "farm-1", "zone-1",
                HARVESTED_AT, BigDecimal.TEN, "kg", "VN", null, RiskLevel.LOW,
                HarvestBatchStatus.HARVESTED, "farmer-1", Instant.now());
    }

    private LabSample sample(LabSampleStatus status) {
        return new LabSample("sample-1", "season-1", "batch-1", "SAMPLE-1", SampleType.FRUIT,
                SAMPLED_AT, "farmer-1", null, "Lab", null, status, List.of(), Instant.now(), List.of("AI"));
    }

    private ResidueStandard standard(boolean active, boolean verified, LocalDate effectiveTo) {
        return new ResidueStandard("standard-1", "VN", "DURIAN", "Durian", "AI", "Ingredient",
                BigDecimal.ONE, "mg/kg", LocalDate.parse("2026-01-01"), effectiveTo,
                SourceType.VIETNAM, "catalog", null, verified, active, Instant.now(), Instant.now());
    }

    private LabResidueResult result(String policyVersion, BigDecimal measuredValue) {
        return new LabResidueResult("result-1", "sample-1", "AI", "Ingredient", measuredValue,
                "mg/kg", null, null, BigDecimal.ONE, "catalog", LabResultStatus.PASS,
                "spoofed", Instant.now(), Instant.now(), "standard-1", "mg/kg",
                LocalDate.parse("2026-01-01"), null, SourceType.VIETNAM, policyVersion);
    }

    private CultivationCalendarDtos.CreateLabResultRequest request(BigDecimal measuredValue, String unit,
            BigDecimal clientMrl, LabResultStatus clientStatus) {
        return new CultivationCalendarDtos.CreateLabResultRequest("sample-1", "AI", "Ingredient",
                measuredValue, unit, null, null, clientMrl, "client source", clientStatus, "spoofed");
    }

    private CultivationCalendarDtos.CreateHarvestBatchRequest harvestRequest(String farmId, String plotId) {
        return new CultivationCalendarDtos.CreateHarvestBatchRequest("BATCH-NEW", "season-1",
                farmId, plotId, HARVESTED_AT, BigDecimal.ONE, "kg", "VN", "spoofed", false, null);
    }

    private ExportRelease exportRelease(ExportReleaseStatus status) {
        var release = new ExportRelease("release-1", "REL-1", "batch-1", "VN", Map.of("stale", true),
                Map.of(), status, "farmer-1", Instant.now(), null, null, null, null, Instant.now());
        when(exportReleaseRepository.findById("release-1")).thenReturn(Optional.of(release));
        return release;
    }

    private CultivationActivity activity() {
        return new CultivationActivity("activity-1", "plan-1", "season-1", "farm-1", "zone-1",
                List.of(), ActivityType.CHEMICAL_TREATMENT, "Spray", null, HARVESTED_AT,
                null, null, "NORMAL", ActivityStatus.COMPLETED, List.of(), false, null, null,
                null, null, Instant.now(), Instant.now());
    }

    private ActivityExecution execution() {
        return new ActivityExecution("execution-1", "activity-1", HARVESTED_AT, HARVESTED_AT,
                "farmer-1", null, null, null, null, Map.of(), null, null,
                null, null, List.of(), true, Instant.now());
    }

    private ActivityInputUsage usage(LocalDate safeDate) {
        var ingredient = new ActiveIngredientSnapshot("AI", "Ingredient", BigDecimal.ONE, "g/L");
        var input = new AgriculturalInputSnapshot("INPUT", "Product", null, null,
                InputCategory.CHEMICAL_PESTICIDE, BiologicalLevel.CHEMICAL, 7,
                List.of(ingredient), List.of("VN"), List.of());
        return new ActivityInputUsage("usage-1", "execution-1", "input-1", input,
                "lot-1", null, BigDecimal.ONE, "ml", null, null, null, null,
                null, null, List.of(ingredient), safeDate, Instant.now());
    }
}
