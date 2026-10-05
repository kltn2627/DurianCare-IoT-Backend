package com.duriancare.cultivation.service;

import com.duriancare.cultivation.api.CultivationCalendarDtos;
import com.duriancare.cultivation.compliance.ComplianceDecision;
import com.duriancare.cultivation.compliance.ComplianceEngine;
import com.duriancare.cultivation.compliance.LabResidueComparator;
import com.duriancare.cultivation.compliance.SafeHarvestDateCalculator;
import com.duriancare.cultivation.domain.ActivityExecution;
import com.duriancare.cultivation.domain.ActivityInputUsage;
import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.AgriculturalInput;
import com.duriancare.cultivation.domain.AgriculturalInputSnapshot;
import com.duriancare.cultivation.domain.AuditAction;
import com.duriancare.cultivation.domain.AuditLog;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceAssessment;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.CultivationActivity;
import com.duriancare.cultivation.domain.CultivationPlan;
import com.duriancare.cultivation.domain.CultivationSeason;
import com.duriancare.cultivation.domain.ExportRelease;
import com.duriancare.cultivation.domain.ExportReleaseStatus;
import com.duriancare.cultivation.domain.HarvestBatch;
import com.duriancare.cultivation.domain.HarvestBatchStatus;
import com.duriancare.cultivation.domain.InputStatus;
import com.duriancare.cultivation.domain.LabResidueResult;
import com.duriancare.cultivation.domain.LabResultStatus;
import com.duriancare.cultivation.domain.LabSample;
import com.duriancare.cultivation.domain.LabSampleStatus;
import com.duriancare.cultivation.domain.PlanStatus;
import com.duriancare.cultivation.domain.ResidueStandard;
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
import com.duriancare.cultivation.repository.LegacyIntegrityMarkerRepository;
import com.duriancare.cultivation.repository.ResidueStandardRepository;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import com.duriancare.cultivation.security.CultivationAccessDeniedException;
import com.duriancare.cultivation.security.CultivationActor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CultivationCalendarService {

    private static final String RULES_VERSION = "cultivation-calendar-v2";
    private static final String LAB_EVALUATION_VERSION = "SERVER_MRL_V1";

    private final CultivationPlanRepository planRepository;
    private final CultivationSeasonRepository seasonRepository;
    private final CultivationActivityRepository activityRepository;
    private final ActivityExecutionRepository executionRepository;
    private final ActivityInputUsageRepository inputUsageRepository;
    private final AgriculturalInputRepository agriculturalInputRepository;
    private final AuditLogRepository auditLogRepository;
    private final ResidueStandardRepository residueStandardRepository;
    private final LabSampleRepository labSampleRepository;
    private final LabResidueResultRepository labResidueResultRepository;
    private final HarvestBatchRepository harvestBatchRepository;
    private final ExportReleaseRepository exportReleaseRepository;
    private final ComplianceAssessmentRepository complianceAssessmentRepository;
    private final SafeHarvestDateCalculator safeHarvestDateCalculator;
    private final ComplianceEngine complianceEngine;
    private final LabResidueComparator labResidueComparator;
    private final CultivationAccessGuard accessGuard;
    private final LegacyIntegrityMarkerRepository integrityMarkerRepository;

    public CultivationCalendarService(
            CultivationPlanRepository planRepository,
            CultivationSeasonRepository seasonRepository,
            CultivationActivityRepository activityRepository,
            ActivityExecutionRepository executionRepository,
            ActivityInputUsageRepository inputUsageRepository,
            AgriculturalInputRepository agriculturalInputRepository,
            AuditLogRepository auditLogRepository,
            ResidueStandardRepository residueStandardRepository,
            LabSampleRepository labSampleRepository,
            LabResidueResultRepository labResidueResultRepository,
            HarvestBatchRepository harvestBatchRepository,
            ExportReleaseRepository exportReleaseRepository,
            ComplianceAssessmentRepository complianceAssessmentRepository,
            SafeHarvestDateCalculator safeHarvestDateCalculator,
            ComplianceEngine complianceEngine,
            LabResidueComparator labResidueComparator,
            CultivationAccessGuard accessGuard,
            LegacyIntegrityMarkerRepository integrityMarkerRepository) {
        this.planRepository = planRepository;
        this.seasonRepository = seasonRepository;
        this.activityRepository = activityRepository;
        this.executionRepository = executionRepository;
        this.inputUsageRepository = inputUsageRepository;
        this.agriculturalInputRepository = agriculturalInputRepository;
        this.auditLogRepository = auditLogRepository;
        this.residueStandardRepository = residueStandardRepository;
        this.labSampleRepository = labSampleRepository;
        this.labResidueResultRepository = labResidueResultRepository;
        this.harvestBatchRepository = harvestBatchRepository;
        this.exportReleaseRepository = exportReleaseRepository;
        this.complianceAssessmentRepository = complianceAssessmentRepository;
        this.safeHarvestDateCalculator = safeHarvestDateCalculator;
        this.complianceEngine = complianceEngine;
        this.labResidueComparator = labResidueComparator;
        this.accessGuard = accessGuard;
        this.integrityMarkerRepository = integrityMarkerRepository;
    }

    public CultivationPlan createPlan(CultivationActor actor, CultivationCalendarDtos.CreatePlanRequest request) {
        accessGuard.requireCreate(actor, "CultivationPlan", null, request.farmId(), request.plotId());
        return createPlan(request);
    }

    public CultivationPlan getPlan(CultivationActor actor, String id) {
        CultivationPlan plan = getPlan(id);
        accessGuard.requireView(actor, "CultivationPlan", plan.id(), plan.farmId(), plan.plotId());
        return plan;
    }

    public List<CultivationPlan> listPlans(CultivationActor actor, String farmId, String plotId, String cultivationSeasonId) {
        return listPlans(farmId, plotId, cultivationSeasonId).stream()
                .filter(plan -> accessGuard.canView(actor, plan.farmId(), plan.plotId()))
                .toList();
    }

    public List<CultivationActivity> getPlanCalendar(CultivationActor actor, String id) {
        CultivationPlan plan = getPlan(actor, id);
        return activityRepository.findByCultivationPlanId(plan.id()).stream()
                .filter(this::hasValidActivityAncestry)
                .filter(activity -> accessGuard.canView(actor, activity.farmId(), activity.plotId()))
                .sorted(Comparator.comparing(CultivationActivity::scheduledStartAt))
                .toList();
    }

    public CultivationActivity createActivity(CultivationActor actor, CultivationCalendarDtos.CreateActivityRequest request) {
        CultivationPlan plan = getPlan(actor, request.cultivationPlanId());
        requireSamePlanScope(plan, request);
        accessGuard.requireCreate(actor, "CultivationActivity", null, request.farmId(), request.plotId());
        return createActivity(request);
    }

    public List<CultivationActivity> listActivities(
            CultivationActor actor,
            String cultivationSeasonId,
            ActivityType activityType,
            ActivityStatus status) {
        return listActivities(cultivationSeasonId, activityType, status).stream()
                .filter(activity -> accessGuard.canView(actor, activity.farmId(), activity.plotId()))
                .toList();
    }

    public CultivationActivity getActivity(CultivationActor actor, String id) {
        CultivationActivity activity = getActivity(id);
        accessGuard.requireView(actor, "CultivationActivity", activity.id(), activity.farmId(), activity.plotId());
        return activity;
    }

    public CultivationActivity updateActivity(
            CultivationActor actor,
            String id,
            CultivationCalendarDtos.UpdateActivityRequest request) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        return updateActivity(id, request);
    }

    public CultivationActivity approveActivity(CultivationActor actor, String id, String userId) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        requireSameActor(actor, userId);
        return approveActivity(id, actor.userId());
    }

    public CultivationActivity rejectActivity(CultivationActor actor, String id, String userId, String reason) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        requireSameActor(actor, userId);
        return rejectActivity(id, actor.userId(), reason);
    }

    public CultivationActivity startActivity(CultivationActor actor, String id) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        return startActivity(id);
    }

    public CultivationActivity skipActivity(CultivationActor actor, String id) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        return skipActivity(id);
    }

    public CultivationActivity cancelActivity(CultivationActor actor, String id) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        return cancelActivity(id);
    }

    public CultivationCalendarDtos.CompleteActivityResponse completeActivity(
            CultivationActor actor,
            String id,
            CultivationCalendarDtos.CompleteActivityRequest request) {
        CultivationActivity current = getActivity(id);
        accessGuard.requireUpdate(actor, "CultivationActivity", current.id(), current.farmId(), current.plotId());
        if (!requireText(actor.userId(), "Authenticated user is required")
                .equals(requireText(request.executedBy(), "Executed by is required"))) {
            throw new IllegalArgumentException("Executed by must match authenticated actor");
        }
        return completeActivity(id, request);
    }

    public CultivationCalendarDtos.CareHistoryResponse careHistory(CultivationActor actor, String cultivationSeasonId) {
        List<CultivationActivity> activities = listActivities(actor, cultivationSeasonId, null, null);
        List<ActivityExecution> executions = activities.stream()
                .flatMap(activity -> executionRepository.findByCultivationActivityId(activity.id()).stream())
                .toList();
        List<ActivityInputUsage> usages = executions.stream()
                .flatMap(execution -> inputUsageRepository.findByActivityExecutionId(execution.id()).stream())
                .toList();
        return new CultivationCalendarDtos.CareHistoryResponse(activities, executions, usages);
    }

    public List<CultivationActivity> chemicalHistory(CultivationActor actor, String cultivationSeasonId) {
        return chemicalHistory(cultivationSeasonId).stream()
                .filter(activity -> accessGuard.canView(actor, activity.farmId(), activity.plotId()))
                .toList();
    }

    public java.util.Optional<LocalDate> safeHarvestDate(CultivationActor actor, String cultivationSeasonId) {
        requireCanViewSeason(actor, cultivationSeasonId);
        return safeHarvestDate(cultivationSeasonId);
    }

    public ComplianceAssessment assessCompliance(
            CultivationActor actor,
            String cultivationSeasonId,
            CultivationCalendarDtos.AssessComplianceRequest request) {
        HarvestBatch harvestBatch = request.harvestBatchId() == null ? null : getHarvestBatch(actor, request.harvestBatchId());
        if (harvestBatch == null) {
            requireCanViewSeason(actor, cultivationSeasonId);
        } else if (!harvestBatch.cultivationSeasonId().equals(cultivationSeasonId)) {
            throw new IllegalArgumentException("Harvest batch does not belong to the requested cultivation season");
        }
        return assessCompliance(cultivationSeasonId, request, actor.userId());
    }

    public LabSample createLabSample(CultivationActor actor, CultivationCalendarDtos.CreateLabSampleRequest request) {
        if (StringUtils.hasText(request.harvestBatchId())) {
            HarvestBatch batch = getHarvestBatch(actor, request.harvestBatchId());
            if (!batch.cultivationSeasonId().equals(request.cultivationSeasonId())) {
                throw new IllegalArgumentException("Harvest batch does not belong to the requested cultivation season");
            }
            accessGuard.requireUpdate(actor, "LabSample", null, batch.farmId(), batch.plotId());
        } else {
            CultivationSeason season = requireSeason(request.cultivationSeasonId());
            accessGuard.requireUpdate(actor, "LabSample", null, season.farmId(), season.plotId());
        }
        return createLabSample(request, actor.userId());
    }

    public LabSample sendLabSample(CultivationActor actor, String id) {
        LabSample sample = getLabSample(actor, id);
        if (!StringUtils.hasText(sample.harvestBatchId())) {
            throw new IllegalStateException("Residue sample must be linked to a harvest batch");
        }
        HarvestBatch batch = getHarvestBatch(actor, sample.harvestBatchId());
        accessGuard.requireUpdate(actor, "LabSample", id, batch.farmId(), batch.plotId());
        if (sample.status() != LabSampleStatus.COLLECTED) {
            throw new IllegalStateException("Only collected samples can be sent");
        }
        return labSampleRepository.save(withLabSampleStatus(sample, LabSampleStatus.SENT));
    }

    public List<LabSample> listLabSamples(CultivationActor actor, String cultivationSeasonId, String harvestBatchId) {
        return listLabSamples(cultivationSeasonId, harvestBatchId).stream()
                .filter(sample -> canViewLabSample(actor, sample))
                .toList();
    }

    public LabSample getLabSample(CultivationActor actor, String id) {
        LabSample sample = getLabSample(id);
        requireCanViewLabSample(actor, sample);
        return sample;
    }

    public List<LabResidueResult> listLabResults(CultivationActor actor, String sampleId) {
        LabSample sample = getLabSample(actor, sampleId);
        return labResidueResultRepository.findByLabSampleId(sample.id());
    }

    public LabResidueResult createLabResult(CultivationActor actor, CultivationCalendarDtos.CreateLabResultRequest request) {
        LabSample sample = getLabSample(actor, request.labSampleId());
        if (sample.sampleType() != SampleType.FRUIT) {
            throw new IllegalArgumentException("Only fruit samples can support harvest residue results");
        }
        if (!StringUtils.hasText(sample.harvestBatchId())) {
            throw new IllegalStateException("Residue result requires a batch-linked sample");
        }
        HarvestBatch batch = getHarvestBatch(actor, sample.harvestBatchId());
        if (!batch.cultivationSeasonId().equals(sample.cultivationSeasonId())) {
            throw new IllegalArgumentException("Lab sample season must match its harvest batch");
        }
        accessGuard.requireUpdate(actor, "LabResidueResult", null, batch.farmId(), batch.plotId());
        requireTechnicalReviewer(actor);
        return createLabResult(request, sample, batch, actor.userId());
    }

    public HarvestBatch createHarvestBatch(CultivationActor actor, CultivationCalendarDtos.CreateHarvestBatchRequest request) {
        CultivationSeason season = requireSeason(request.cultivationSeasonId());
        requireBatchScope(season, request.farmId(), request.plotId());
        accessGuard.requireCreate(actor, "HarvestBatch", null, season.farmId(), season.plotId());
        return createHarvestBatch(request, actor.userId());
    }

    public HarvestBatch getHarvestBatch(CultivationActor actor, String id) {
        HarvestBatch batch = getHarvestBatch(id);
        accessGuard.requireView(actor, "HarvestBatch", batch.id(), batch.farmId(), batch.plotId());
        return batch;
    }

    public List<HarvestBatch> listHarvestBatches(
            CultivationActor actor,
            String cultivationSeasonId,
            String farmId,
            String plotId) {
        return listHarvestBatches(cultivationSeasonId, farmId, plotId).stream()
                .filter(batch -> accessGuard.canView(actor, batch.farmId(), batch.plotId()))
                .toList();
    }

    public ComplianceAssessment assessExportRelease(
            CultivationActor actor,
            CultivationCalendarDtos.AssessComplianceRequest request) {
        HarvestBatch batch = getHarvestBatch(actor, requireText(request.harvestBatchId(), "Harvest batch is required"));
        return assessCompliance(actor, batch.cultivationSeasonId(), request);
    }

    public ExportRelease createExportRelease(CultivationActor actor, CultivationCalendarDtos.CreateExportReleaseRequest request) {
        HarvestBatch batch = getHarvestBatch(actor, request.harvestBatchId());
        accessGuard.requireUpdate(actor, "ExportRelease", null, batch.farmId(), batch.plotId());
        return createExportRelease(request, actor.userId());
    }

    public ExportRelease submitExportRelease(CultivationActor actor, String id, String userId) {
        ExportRelease release = getExportRelease(actor, id);
        HarvestBatch batch = getHarvestBatch(actor, release.harvestBatchId());
        accessGuard.requireUpdate(actor, "ExportRelease", id, batch.farmId(), batch.plotId());
        return submitExportRelease(id, actor.userId());
    }

    public ExportRelease approveExportRelease(CultivationActor actor, String id, String reviewerId) {
        ExportRelease release = getExportRelease(actor, id);
        HarvestBatch batch = getHarvestBatch(actor, release.harvestBatchId());
        accessGuard.requireUpdate(actor, "ExportRelease", id, batch.farmId(), batch.plotId());
        requireTechnicalReviewer(actor);
        return approveExportRelease(id, actor.userId());
    }

    public ExportRelease releaseExportRelease(CultivationActor actor, String id, String reviewerId) {
        ExportRelease release = getExportRelease(actor, id);
        HarvestBatch batch = getHarvestBatch(actor, release.harvestBatchId());
        accessGuard.requireUpdate(actor, "ExportRelease", id, batch.farmId(), batch.plotId());
        requireTechnicalReviewer(actor);
        return releaseExportRelease(id, actor.userId());
    }

    public ExportRelease recallExportRelease(CultivationActor actor, String id, String reviewerId, String reason) {
        ExportRelease release = getExportRelease(actor, id);
        HarvestBatch batch = getHarvestBatch(actor, release.harvestBatchId());
        accessGuard.requireUpdate(actor, "ExportRelease", id, batch.farmId(), batch.plotId());
        return recallExportRelease(id, actor.userId(), reason);
    }

    public ExportRelease getExportRelease(CultivationActor actor, String id) {
        ExportRelease release = getExportRelease(id);
        HarvestBatch batch = getHarvestBatch(actor, release.harvestBatchId());
        accessGuard.requireView(actor, "ExportRelease", release.id(), batch.farmId(), batch.plotId());
        return release;
    }

    public List<ExportRelease> listExportReleases(
            CultivationActor actor,
            String harvestBatchId,
            String targetMarketCode,
            ExportReleaseStatus status) {
        return listExportReleases(harvestBatchId, targetMarketCode, status).stream()
                .filter(release -> canViewExportRelease(actor, release))
                .toList();
    }

    public CultivationCalendarDtos.TraceabilityResponse traceability(CultivationActor actor, String id) {
        ExportRelease release = getExportRelease(actor, id);
        return traceabilityResponse(release);
    }

    public CultivationPlan createPlan(CultivationCalendarDtos.CreatePlanRequest request) {
        CultivationSeason season = seasonRepository.findById(request.cultivationSeasonId().trim())
                .orElseThrow(() -> new NoSuchElementException("Cultivation season was not found"));
        if (!Objects.equals(season.farmId(), request.farmId().trim())
                || !Objects.equals(season.plotId(), request.plotId().trim())) {
            throw new IllegalArgumentException("Cultivation season must belong to the plan farm and zone");
        }
        if (request.expectedHarvestDate() != null && request.expectedHarvestDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Expected harvest date must not be before start date");
        }
        Instant now = Instant.now();
        CultivationPlan saved = planRepository.save(new CultivationPlan(
                newId(),
                request.farmId().trim(),
                request.plotId().trim(),
                request.cultivationSeasonId().trim(),
                trimToNull(request.templateId()),
                request.name().trim(),
                request.startDate(),
                request.expectedHarvestDate(),
                copy(request.targetMarketCodes()),
                PlanStatus.ACTIVE,
                request.createdBy().trim(),
                now,
                now));
        audit(AuditAction.CULTIVATION_PLAN_CREATED, "CultivationPlan", saved.id(), saved.createdBy(), null,
                Map.of("cultivationSeasonId", saved.cultivationSeasonId()));
        return saved;
    }

    public CultivationPlan getPlan(String id) {
        CultivationPlan plan = planRepository.findById(id).orElseThrow(() -> notFound("Cultivation plan", id));
        if (!hasValidPlanAncestry(plan)) throw notFound("Cultivation plan", id);
        return plan;
    }

    public List<CultivationPlan> listPlans(String farmId, String plotId, String cultivationSeasonId) {
        return planRepository.findAll().stream()
                .filter(this::hasValidPlanAncestry)
                .filter(plan -> farmId == null || farmId.equals(plan.farmId()))
                .filter(plan -> plotId == null || plotId.equals(plan.plotId()))
                .filter(plan -> cultivationSeasonId == null || cultivationSeasonId.equals(plan.cultivationSeasonId()))
                .sorted(Comparator.comparing(CultivationPlan::startDate).reversed())
                .toList();
    }

    public List<CultivationActivity> getPlanCalendar(String id) {
        getPlan(id);
        return activityRepository.findByCultivationPlanId(id).stream()
                .filter(this::hasValidActivityAncestry)
                .sorted(Comparator.comparing(CultivationActivity::scheduledStartAt))
                .toList();
    }

    public CultivationActivity createActivity(CultivationCalendarDtos.CreateActivityRequest request) {
        CultivationPlan plan = getPlan(request.cultivationPlanId());
        requireSamePlanScope(plan, request);
        if (request.scheduledEndAt() != null && request.scheduledEndAt().isBefore(request.scheduledStartAt())) {
            throw new IllegalArgumentException("Scheduled end time must not be before start time");
        }
        ActivityStatus status = request.approvalRequired() ? ActivityStatus.PENDING_APPROVAL : ActivityStatus.SCHEDULED;
        if (request.activityType() == ActivityType.CHEMICAL_TREATMENT) {
            List<AgriculturalInput> inputs = loadInputs(request.agriculturalInputIds());
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("Chemical treatment requires at least one agricultural input");
            }
            for (AgriculturalInput input : inputs) {
                validateInputCanBePlanned(input, request);
            }
            if (inputs.stream().anyMatch(this::requiresChemicalApproval)) {
                requireText(request.targetPestOrDisease(), "Target pest or disease is required for chemical treatment");
                requireText(request.biologicalControlReason(), "Biological/IPM reason is required for chemical treatment");
                requireText(request.approvalUserId(), "Approval user is required for chemical treatment");
                status = ActivityStatus.PENDING_APPROVAL;
            }
        }
        Instant now = Instant.now();
        CultivationActivity saved = activityRepository.save(new CultivationActivity(
                newId(),
                request.cultivationPlanId().trim(),
                request.cultivationSeasonId().trim(),
                request.farmId().trim(),
                request.plotId().trim(),
                copy(request.treeIds()),
                request.activityType(),
                request.title().trim(),
                trimToNull(request.description()),
                request.scheduledStartAt(),
                request.scheduledEndAt(),
                trimToNull(request.recurrenceRule()),
                defaultString(request.priority(), "NORMAL"),
                status,
                copy(request.assignedUserIds()),
                request.approvalRequired() || status == ActivityStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                null,
                now,
                now));
        audit(AuditAction.CULTIVATION_ACTIVITY_CREATED, "CultivationActivity", saved.id(), null, null,
                Map.of("activityType", saved.activityType().name(), "status", saved.status().name()));
        return saved;
    }

    public List<CultivationActivity> listActivities(String cultivationSeasonId, ActivityType activityType, ActivityStatus status) {
        return activityRepository.findAll().stream()
                .filter(this::hasValidActivityAncestry)
                .filter(activity -> cultivationSeasonId == null || cultivationSeasonId.equals(activity.cultivationSeasonId()))
                .filter(activity -> activityType == null || activityType == activity.activityType())
                .filter(activity -> status == null || status == activity.status())
                .sorted(Comparator.comparing(CultivationActivity::scheduledStartAt))
                .toList();
    }

    public CultivationActivity getActivity(String id) {
        CultivationActivity activity = activityRepository.findById(id).orElseThrow(() -> notFound("Cultivation activity", id));
        if (!hasValidActivityAncestry(activity)) throw notFound("Cultivation activity", id);
        return activity;
    }

    public CultivationActivity updateActivity(String id, CultivationCalendarDtos.UpdateActivityRequest request) {
        CultivationActivity current = getActivity(id);
        if (current.status() == ActivityStatus.CANCELLED || current.status() == ActivityStatus.COMPLETED) {
            throw new IllegalStateException("Completed or cancelled activity cannot be updated");
        }
        Instant start = request.scheduledStartAt() == null ? current.scheduledStartAt() : request.scheduledStartAt();
        Instant end = request.scheduledEndAt() == null ? current.scheduledEndAt() : request.scheduledEndAt();
        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("Scheduled end time must not be before start time");
        }
        CultivationActivity saved = activityRepository.save(new CultivationActivity(
                current.id(), current.cultivationPlanId(), current.cultivationSeasonId(), current.farmId(), current.plotId(),
                current.treeIds(), current.activityType(),
                defaultString(request.title(), current.title()),
                request.description() == null ? current.description() : request.description(),
                start, end, current.recurrenceRule(),
                defaultString(request.priority(), current.priority()),
                current.status(),
                request.assignedUserIds() == null ? current.assignedUserIds() : copy(request.assignedUserIds()),
                current.approvalRequired(), current.approvedBy(), current.approvedAt(), current.rejectionReason(),
                current.version(), current.createdAt(), Instant.now()));
        audit(AuditAction.CULTIVATION_ACTIVITY_UPDATED, "CultivationActivity", saved.id(), null, null,
                Map.of("status", saved.status().name()));
        return saved;
    }

    public CultivationActivity approveActivity(String id, String userId) {
        CultivationActivity current = getActivity(id);
        requirePendingApproval(current);
        String reviewerId = requireText(userId, "Approval user is required");
        CultivationActivity approved = withStatus(current, ActivityStatus.APPROVED, reviewerId, Instant.now(), null);
        audit(AuditAction.CULTIVATION_ACTIVITY_APPROVED, "CultivationActivity", approved.id(), reviewerId, null, Map.of());
        return approved;
    }

    public CultivationActivity rejectActivity(String id, String userId, String reason) {
        CultivationActivity current = getActivity(id);
        requirePendingApproval(current);
        String reviewerId = requireText(userId, "Rejection user is required");
        String rejectionReason = requireText(reason, "Rejection reason is required");
        CultivationActivity rejected = withStatus(current, ActivityStatus.CANCELLED, reviewerId, Instant.now(), rejectionReason);
        audit(AuditAction.CULTIVATION_ACTIVITY_REJECTED, "CultivationActivity", rejected.id(), reviewerId, rejectionReason, Map.of());
        return rejected;
    }

    public CultivationActivity startActivity(String id) {
        CultivationActivity current = getActivity(id);
        requireNotPendingApproval(current);
        if (current.status() == ActivityStatus.CANCELLED || current.status() == ActivityStatus.COMPLETED) {
            throw new IllegalStateException("Cancelled or completed activity cannot be started");
        }
        return withStatus(current, ActivityStatus.IN_PROGRESS, current.approvedBy(), current.approvedAt(), current.rejectionReason());
    }

    public CultivationActivity skipActivity(String id) {
        CultivationActivity skipped = terminalStatus(id, ActivityStatus.SKIPPED);
        audit(AuditAction.CULTIVATION_ACTIVITY_SKIPPED, "CultivationActivity", skipped.id(), null, null, Map.of());
        return skipped;
    }

    public CultivationActivity cancelActivity(String id) {
        CultivationActivity cancelled = terminalStatus(id, ActivityStatus.CANCELLED);
        audit(AuditAction.CULTIVATION_ACTIVITY_CANCELLED, "CultivationActivity", cancelled.id(), null, null, Map.of());
        return cancelled;
    }

    public CultivationCalendarDtos.CompleteActivityResponse completeActivity(
            String id,
            CultivationCalendarDtos.CompleteActivityRequest request) {
        CultivationActivity activity = getActivity(id);
        requireNotPendingApproval(activity);
        if (activity.status() == ActivityStatus.CANCELLED || activity.status() == ActivityStatus.COMPLETED) {
            throw new IllegalStateException("Cancelled or completed activity cannot be completed");
        }
        if (request.completedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("Completion time must not be before start time");
        }
        List<CultivationCalendarDtos.ActivityInputUsageRequest> usageRequests = copy(request.inputUsages());
        if (switch (activity.activityType()) {
            case FERTILIZATION, FERTIGATION, BIOLOGICAL_TREATMENT, CHEMICAL_TREATMENT -> true;
            default -> false;
        } && usageRequests.isEmpty()) {
            throw new IllegalArgumentException("At least one agricultural input usage is required for this activity type");
        }
        LocalDate completedDate = LocalDate.ofInstant(request.completedAt(), ZoneOffset.UTC);
        List<AgriculturalInput> validatedInputs = new ArrayList<>();
        List<AgriculturalInputSnapshot> snapshots = new ArrayList<>();
        List<LocalDate> safeHarvestDates = new ArrayList<>();
        for (CultivationCalendarDtos.ActivityInputUsageRequest usageRequest : usageRequests) {
            if (usageRequest == null) {
                throw new IllegalArgumentException("Agricultural input usage is required");
            }
            String inputId = requireText(usageRequest.agriculturalInputId(), "Agricultural input is required");
            AgriculturalInput input = agriculturalInputRepository.findById(inputId)
                    .orElseThrow(() -> notFound("Agricultural input", inputId));
            validateInputCanBeUsed(input, usageRequest);
            validatedInputs.add(input);
            snapshots.add(CultivationCalendarDtos.snapshotOf(input));
            safeHarvestDates.add(safeHarvestDateCalculator.safeHarvestDate(completedDate, input.preHarvestIntervalDays()));
        }
        ActivityExecution execution = executionRepository.save(new ActivityExecution(
                newId(), id, request.startedAt(), request.completedAt(), request.executedBy().trim(),
                trimToNull(request.supervisedBy()), request.actualTreeCount(), request.actualArea(), request.areaUnit(),
                request.weatherSnapshot(), request.applicationMethod(), request.equipment(), request.notes(),
                request.resultObservation(), copy(request.evidenceFiles()), true, Instant.now()));
        List<ActivityInputUsage> usages = new ArrayList<>();
        for (int index = 0; index < usageRequests.size(); index++) {
            CultivationCalendarDtos.ActivityInputUsageRequest usageRequest = usageRequests.get(index);
            AgriculturalInput input = validatedInputs.get(index);
            usages.add(inputUsageRepository.save(new ActivityInputUsage(
                    newId(), execution.id(), input.id(), snapshots.get(index),
                    trimToNull(usageRequest.batchNumber()), usageRequest.expiryDate(), usageRequest.quantityUsed(),
                    usageRequest.quantityUnit(), usageRequest.waterVolume(), usageRequest.waterVolumeUnit(),
                    usageRequest.concentration(), usageRequest.concentrationUnit(), usageRequest.treatedArea(),
                    usageRequest.areaUnit(), input.activeIngredients(), safeHarvestDates.get(index), Instant.now())));
        }
        CultivationActivity completed = withStatus(activity, ActivityStatus.COMPLETED, activity.approvedBy(), activity.approvedAt(), null);
        audit(AuditAction.CULTIVATION_ACTIVITY_COMPLETED, "CultivationActivity", completed.id(), request.executedBy(), null,
                Map.of("executionId", execution.id(), "inputUsageCount", usages.size()));
        return new CultivationCalendarDtos.CompleteActivityResponse(
                completed,
                execution,
                usages,
                safeHarvestDate(activity.cultivationSeasonId()).orElse(null));
    }

    public CultivationCalendarDtos.CareHistoryResponse careHistory(String cultivationSeasonId) {
        List<CultivationActivity> activities = listActivities(cultivationSeasonId, null, null);
        List<ActivityExecution> executions = activities.stream()
                .flatMap(activity -> executionRepository.findByCultivationActivityId(activity.id()).stream())
                .toList();
        List<ActivityInputUsage> usages = executions.stream()
                .flatMap(execution -> inputUsageRepository.findByActivityExecutionId(execution.id()).stream())
                .toList();
        return new CultivationCalendarDtos.CareHistoryResponse(activities, executions, usages);
    }

    public List<CultivationActivity> chemicalHistory(String cultivationSeasonId) {
        return activityRepository.findByCultivationSeasonIdAndActivityType(cultivationSeasonId, ActivityType.CHEMICAL_TREATMENT)
                .stream().filter(this::hasValidActivityAncestry).toList();
    }

    public java.util.Optional<LocalDate> safeHarvestDate(String cultivationSeasonId) {
        return safeHarvestDateCalculator.earliestSafeHarvestDate(inputUsagesForSeason(cultivationSeasonId));
    }

    public AgriculturalInput createAgriculturalInput(CultivationCalendarDtos.CreateAgriculturalInputRequest request) {
        Instant now = Instant.now();
        AgriculturalInput saved = agriculturalInputRepository.save(new AgriculturalInput(
                newId(), request.code().trim(), request.productName().trim(), request.tradeName(), request.manufacturer(),
                request.registrationNumber(), request.category(), request.biologicalLevel(), request.formulation(),
                request.unit(), copy(request.activeIngredients()), copy(request.beneficialOrganisms()),
                request.recommendedDose(), request.maximumDose(), request.preHarvestIntervalDays(),
                request.reEntryIntervalHours(), copy(request.targetPests()), copy(request.applicableCrops()),
                copy(request.allowedMarketCodes()), copy(request.prohibitedMarketCodes()), request.labelDocumentUrl(),
                copy(request.certificateDocumentUrls()), request.status() == null ? InputStatus.DRAFT : request.status(),
                request.verifiedBy(), request.verifiedBy() == null ? null : now, now, now));
        audit(AuditAction.AGRICULTURAL_INPUT_CREATED, "AgriculturalInput", saved.id(), saved.verifiedBy(), null,
                Map.of("code", saved.code(), "biologicalLevel", saved.biologicalLevel().name()));
        return saved;
    }

    public List<AgriculturalInput> listAgriculturalInputs(BiologicalLevel biologicalLevel, InputStatus status) {
        return agriculturalInputRepository.findAll().stream()
                .filter(input -> biologicalLevel == null || biologicalLevel == input.biologicalLevel())
                .filter(input -> status == null || status == input.status())
                .sorted(Comparator.comparing(AgriculturalInput::productName))
                .toList();
    }

    public AgriculturalInput getAgriculturalInput(String id) {
        return agriculturalInputRepository.findById(id).orElseThrow(() -> notFound("Agricultural input", id));
    }

    public ResidueStandard createResidueStandard(CultivationActor actor, CultivationCalendarDtos.CreateResidueStandardRequest request) {
        requireStandardAdministrator(actor);
        return createResidueStandard(request, actor.userId());
    }

    public List<ResidueStandard> importResidueStandards(CultivationActor actor,
            List<CultivationCalendarDtos.CreateResidueStandardRequest> requests) {
        requireStandardAdministrator(actor);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream().map(request -> createResidueStandard(request, actor.userId())).toList();
    }

    private ResidueStandard createResidueStandard(CultivationCalendarDtos.CreateResidueStandardRequest request,
            String actorId) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new IllegalArgumentException("Effective-to date must not be before effective-from date");
        }
        Instant now = Instant.now();
        ResidueStandard saved = residueStandardRepository.save(new ResidueStandard(
                newId(), normalizeCode(request.marketCode()), normalizeCode(request.commodityCode()), request.commodityName(),
                normalizeCode(request.activeIngredientCode()), request.activeIngredientName(), request.mrlValue(), request.unit().trim(),
                request.effectiveFrom(), request.effectiveTo(), request.sourceType(), request.sourceReference(), null,
                request.verified(), request.active(), now, now));
        audit(AuditAction.RESIDUE_STANDARD_CREATED, "ResidueStandard", saved.id(), actorId, null,
                Map.of("marketCode", saved.marketCode(), "activeIngredientCode", saved.activeIngredientCode()));
        return saved;
    }

    public List<ResidueStandard> listResidueStandards(String marketCode, String commodityCode, String activeIngredientCode) {
        return residueStandardRepository.findAll().stream()
                .filter(standard -> marketCode == null || marketCode.equalsIgnoreCase(standard.marketCode()))
                .filter(standard -> commodityCode == null || commodityCode.equalsIgnoreCase(standard.commodityCode()))
                .filter(standard -> activeIngredientCode == null || activeIngredientCode.equalsIgnoreCase(standard.activeIngredientCode()))
                .toList();
    }

    private LabSample createLabSample(CultivationCalendarDtos.CreateLabSampleRequest request, String actorId) {
        CultivationSeason season = requireSeason(request.cultivationSeasonId());
        if (StringUtils.hasText(request.harvestBatchId())) {
            HarvestBatch batch = getHarvestBatch(request.harvestBatchId());
            if (!Objects.equals(batch.cultivationSeasonId(), season.id())) {
                throw new IllegalArgumentException("Lab sample season must match its harvest batch");
            }
        }
        if (request.sampledAt() == null || request.sampledAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Sample collection time cannot be in the future");
        }
        List<String> expectedCodes = java.util.stream.Stream.concat(
                copy(request.expectedActiveIngredientCodes()).stream(),
                inputUsagesForSeason(request.cultivationSeasonId()).stream()
                        .flatMap(usage -> usage.activeIngredientSnapshots().stream())
                        .map(ingredient -> ingredient.code()))
                .map(CultivationCalendarService::normalizeCode).distinct().toList();
        LabSample saved = labSampleRepository.save(new LabSample(
                newId(), request.cultivationSeasonId(), trimToNull(request.harvestBatchId()), request.sampleCode(),
                request.sampleType(), request.sampledAt(), request.sampledBy(), request.samplingLocation(),
                request.laboratoryName(), request.laboratoryAccreditation(),
                LabSampleStatus.COLLECTED,
                copy(request.attachments()), Instant.now(), expectedCodes));
        audit(AuditAction.LAB_SAMPLE_CREATED, "LabSample", saved.id(), actorId, null,
                Map.of("sampleCode", saved.sampleCode()));
        return saved;
    }

    public List<LabSample> listLabSamples(String cultivationSeasonId, String harvestBatchId) {
        return labSampleRepository.findAll().stream()
                .filter(this::hasValidLabSampleAncestry)
                .filter(sample -> cultivationSeasonId == null || cultivationSeasonId.equals(sample.cultivationSeasonId()))
                .filter(sample -> harvestBatchId == null || harvestBatchId.equals(sample.harvestBatchId()))
                .sorted(Comparator.comparing(LabSample::sampledAt).reversed())
                .toList();
    }

    public LabSample getLabSample(String id) {
        LabSample sample = labSampleRepository.findById(id).orElseThrow(() -> notFound("Lab sample", id));
        if (!hasValidLabSampleAncestry(sample)) throw notFound("Lab sample", id);
        return sample;
    }

    private LabResidueResult createLabResult(CultivationCalendarDtos.CreateLabResultRequest request,
            LabSample sample, HarvestBatch batch, String actorId) {
        if (sample.status() != LabSampleStatus.SENT) {
            throw new IllegalStateException("Only sent samples can receive residue results");
        }
        String ingredientCode = normalizeCode(request.activeIngredientCode());
        if (!sample.expectedActiveIngredientCodes().contains(ingredientCode)) {
            throw new IllegalArgumentException("Ingredient is not in the sample's expected panel");
        }
        if (labResidueResultRepository.findByLabSampleId(sample.id()).stream()
                .anyMatch(result -> ingredientCode.equalsIgnoreCase(result.activeIngredientCode()))) {
            throw new IllegalStateException("A result for this ingredient already exists in the sample");
        }
        CultivationSeason season = requireSeason(sample.cultivationSeasonId());
        requireBatchScope(season, batch.farmId(), batch.plotId());
        String market = normalizeCode(batch.expectedDestinationMarket());
        String commodity = normalizeCode(season.crop());
        LocalDate applicableOn = LocalDate.ofInstant(sample.sampledAt(), ZoneOffset.UTC);
        Optional<ResidueStandard> resolved = resolveStandard(market, commodity, ingredientCode, applicableOn);
        LabResultStatus status = evaluateLabMeasurement(request.measuredValue(), request.unit(),
                request.detectionLimit(), request.quantificationLimit(), resolved.orElse(null));
        Instant now = Instant.now();
        ResidueStandard standard = resolved.orElse(null);
        LabResidueResult saved = labResidueResultRepository.save(new LabResidueResult(
                newId(), sample.id(), ingredientCode, request.activeIngredientName(),
                request.measuredValue(), request.unit(), request.detectionLimit(), request.quantificationLimit(),
                standard == null ? null : standard.mrlValue(), standard == null ? null : standard.sourceReference(),
                status, actorId, now, now, standard == null ? null : standard.id(),
                standard == null ? null : standard.unit(), standard == null ? null : standard.effectiveFrom(),
                standard == null ? null : standard.effectiveTo(), standard == null ? null : standard.sourceType(),
                LAB_EVALUATION_VERSION));
        Set<String> recordedCodes = labResidueResultRepository.findByLabSampleId(sample.id()).stream()
                .map(result -> normalizeCode(result.activeIngredientCode())).collect(Collectors.toSet());
        recordedCodes.add(ingredientCode);
        if (!sample.expectedActiveIngredientCodes().isEmpty()
                && recordedCodes.containsAll(sample.expectedActiveIngredientCodes())) {
            labSampleRepository.save(withLabSampleStatus(sample, LabSampleStatus.RESULT_RECORDED));
        }
        audit(AuditAction.LAB_RESULT_RECORDED, "LabResidueResult", saved.id(), actorId, null,
                Map.of("labSampleId", saved.labSampleId(), "resultStatus", saved.resultStatus().name()));
        return saved;
    }

    private HarvestBatch createHarvestBatch(CultivationCalendarDtos.CreateHarvestBatchRequest request, String actorId) {
        CultivationSeason season = requireSeason(request.cultivationSeasonId());
        requireBatchScope(season, request.farmId(), request.plotId());
        LocalDate harvestedDate = LocalDate.ofInstant(request.harvestedAt(), ZoneOffset.UTC);
        LocalDate earliestSafeHarvestDate = safeHarvestDate(request.cultivationSeasonId()).orElse(null);
        RiskLevel riskLevel = RiskLevel.LOW;
        HarvestBatchStatus status = HarvestBatchStatus.HARVESTED;
        if (earliestSafeHarvestDate != null && harvestedDate.isBefore(earliestSafeHarvestDate)) {
            if (!request.overrideSafeHarvestDate() || !StringUtils.hasText(request.overrideReason())) {
                throw new IllegalStateException("Harvest date is before earliest safe harvest date");
            }
            riskLevel = RiskLevel.HIGH;
            status = HarvestBatchStatus.BLOCKED;
        }
        HarvestBatch saved = harvestBatchRepository.save(new HarvestBatch(
                newId(), request.batchCode(), request.cultivationSeasonId(), request.farmId(), request.plotId(),
                request.harvestedAt(), request.quantity(), request.quantityUnit(), request.expectedDestinationMarket(),
                earliestSafeHarvestDate, riskLevel, status, actorId, Instant.now()));
        audit(AuditAction.HARVEST_BATCH_CREATED, "HarvestBatch", saved.id(), actorId, null,
                Map.of("batchCode", saved.batchCode(), "status", saved.status().name()));
        if (request.overrideSafeHarvestDate()) {
            audit(AuditAction.SAFE_HARVEST_DATE_OVERRIDDEN, "HarvestBatch", saved.id(), actorId, request.overrideReason(),
                    Map.of("earliestSafeHarvestDate", String.valueOf(earliestSafeHarvestDate)));
        }
        return saved;
    }

    public HarvestBatch getHarvestBatch(String id) {
        HarvestBatch batch = harvestBatchRepository.findById(id).orElseThrow(() -> notFound("Harvest batch", id));
        if (!hasValidBatchAncestry(batch)) throw notFound("Harvest batch", id);
        return batch;
    }

    public List<HarvestBatch> listHarvestBatches(String cultivationSeasonId, String farmId, String plotId) {
        return harvestBatchRepository.findAll().stream()
                .filter(this::hasValidBatchAncestry)
                .filter(batch -> cultivationSeasonId == null || cultivationSeasonId.equals(batch.cultivationSeasonId()))
                .filter(batch -> farmId == null || farmId.equals(batch.farmId()))
                .filter(batch -> plotId == null || plotId.equals(batch.plotId()))
                .sorted(Comparator.comparing(HarvestBatch::harvestedAt).reversed())
                .toList();
    }

    public ComplianceAssessment assessCompliance(String cultivationSeasonId, CultivationCalendarDtos.AssessComplianceRequest request) {
        return assessCompliance(cultivationSeasonId, request, null);
    }

    private ComplianceAssessment assessCompliance(String cultivationSeasonId,
            CultivationCalendarDtos.AssessComplianceRequest request, String actorId) {
        List<BlockingReason> blockers = new ArrayList<>();
        List<ComplianceWarning> warnings = new ArrayList<>();
        HarvestBatch harvestBatch = request.harvestBatchId() == null ? null : getHarvestBatch(request.harvestBatchId());
        CultivationSeason season = requireSeason(cultivationSeasonId);
        String targetMarket = normalizeCode(request.targetMarketCode());
        if (harvestBatch != null) {
            if (!harvestBatch.cultivationSeasonId().equals(season.id())) {
                throw new IllegalArgumentException("Harvest batch does not belong to the requested cultivation season");
            }
            requireBatchScope(season, harvestBatch.farmId(), harvestBatch.plotId());
            requireMarketMatchesBatch(harvestBatch, targetMarket);
            if (harvestBatch.status() == HarvestBatchStatus.BLOCKED
                    || harvestBatch.status() == HarvestBatchStatus.CANCELLED) {
                blockers.add(BlockingReason.HARVEST_BATCH_NOT_ELIGIBLE);
            }
        }
        LocalDate harvestDate = harvestBatch == null ? null : LocalDate.ofInstant(harvestBatch.harvestedAt(), ZoneOffset.UTC);
        LocalDate applicableOn = harvestDate == null ? LocalDate.now(ZoneOffset.UTC) : harvestDate;
        String commodity = normalizeCode(season.crop());
        LocalDate earliestSafeHarvestDate = safeHarvestDate(cultivationSeasonId).orElse(null);
        if (harvestDate != null && earliestSafeHarvestDate != null && harvestDate.isBefore(earliestSafeHarvestDate)) {
            blockers.add(BlockingReason.PRE_HARVEST_INTERVAL_NOT_MET);
        }
        for (ActivityInputUsage usage : inputUsagesForSeason(cultivationSeasonId)) {
            evaluateInputUsage(usage, targetMarket, commodity, applicableOn, blockers, warnings);
        }
        evaluateLabResults(harvestBatch, targetMarket, commodity, blockers, warnings);
        ComplianceDecision decision = complianceEngine.assess(blockers, warnings);
        ComplianceAssessment saved = complianceAssessmentRepository.save(new ComplianceAssessment(
                newId(), cultivationSeasonId, request.harvestBatchId(), targetMarket, decision.riskScore(),
                decision.riskLevel(), decision.blockingReasons(), decision.warnings(), earliestSafeHarvestDate,
                decision.requiresLabTest(), decision.eligibleForHarvest(), decision.eligibleForExportRelease(),
                Map.of("rulesVersion", RULES_VERSION), Instant.now(), RULES_VERSION));
        audit(AuditAction.COMPLIANCE_ASSESSED, "ComplianceAssessment", saved.id(), actorId, null,
                Map.of("riskLevel", saved.riskLevel().name(), "targetMarketCode", saved.targetMarketCode()));
        return saved;
    }

    public ComplianceAssessment assessExportRelease(CultivationCalendarDtos.AssessComplianceRequest request) {
        HarvestBatch batch = getHarvestBatch(requireText(request.harvestBatchId(), "Harvest batch is required"));
        return assessCompliance(batch.cultivationSeasonId(), request);
    }

    private ExportRelease createExportRelease(CultivationCalendarDtos.CreateExportReleaseRequest request, String actorId) {
        HarvestBatch batch = getHarvestBatch(request.harvestBatchId());
        requireMarketMatchesBatch(batch, normalizeCode(request.targetMarketCode()));
        ComplianceAssessment assessment = assessCompliance(batch.cultivationSeasonId(),
                new CultivationCalendarDtos.AssessComplianceRequest(batch.id(), request.targetMarketCode()), actorId);
        ExportReleaseStatus status = assessment.eligibleForExportRelease() ? ExportReleaseStatus.DRAFT : ExportReleaseStatus.BLOCKED;
        ExportRelease saved = exportReleaseRepository.save(new ExportRelease(
                newId(), request.releaseCode(), batch.id(), normalizeCode(request.targetMarketCode()),
                traceabilitySnapshot(batch, assessment), complianceSnapshot(assessment), status,
                actorId, null, null, null, null, null, Instant.now()));
        audit(AuditAction.EXPORT_RELEASE_CREATED, "ExportRelease", saved.id(), actorId, null,
                Map.of("status", saved.status().name(), "harvestBatchId", saved.harvestBatchId()));
        return saved;
    }

    private ExportRelease submitExportRelease(String id, String userId) {
        ExportRelease release = getExportRelease(id);
        requireReleaseStatus(release, ExportReleaseStatus.DRAFT);
        ExportRelease submitted = saveRelease(release, ExportReleaseStatus.UNDER_REVIEW, userId, Instant.now(), release.reviewedBy(), release.reviewedAt(), null, release.releasedAt());
        audit(AuditAction.EXPORT_RELEASE_SUBMITTED, "ExportRelease", submitted.id(), userId, null, Map.of());
        return submitted;
    }

    private ExportRelease approveExportRelease(String id, String reviewerId) {
        ExportRelease release = getExportRelease(id);
        requireReleaseStatus(release, ExportReleaseStatus.UNDER_REVIEW);
        ComplianceAssessment assessment = reassessRelease(release, reviewerId);
        ExportRelease approved = saveAssessedRelease(release, assessment, ExportReleaseStatus.APPROVED,
                reviewerId, null);
        audit(AuditAction.EXPORT_RELEASE_APPROVED, "ExportRelease", approved.id(), reviewerId, null, Map.of());
        return approved;
    }

    private ExportRelease releaseExportRelease(String id, String reviewerId) {
        ExportRelease release = getExportRelease(id);
        requireReleaseStatus(release, ExportReleaseStatus.APPROVED);
        ComplianceAssessment assessment = reassessRelease(release, reviewerId);
        ExportRelease released = saveAssessedRelease(release, assessment, ExportReleaseStatus.RELEASED,
                reviewerId, Instant.now());
        audit(AuditAction.EXPORT_RELEASE_RELEASED, "ExportRelease", released.id(), reviewerId, null, Map.of());
        return released;
    }

    private ExportRelease recallExportRelease(String id, String reviewerId, String reason) {
        ExportRelease release = getExportRelease(id);
        requireReleaseStatus(release, ExportReleaseStatus.RELEASED);
        requireText(reason, "Recall reason is required");
        ExportRelease recalled = saveRelease(release, ExportReleaseStatus.RECALLED, release.submittedBy(), release.submittedAt(),
                release.reviewedBy(), release.reviewedAt(), reason, release.releasedAt());
        audit(AuditAction.EXPORT_RELEASE_RECALLED, "ExportRelease", recalled.id(), reviewerId, reason, Map.of());
        return recalled;
    }

    public ExportRelease getExportRelease(String id) {
        ExportRelease release = exportReleaseRepository.findById(id).orElseThrow(() -> notFound("Export release", id));
        if (!hasValidReleaseAncestry(release)) throw notFound("Export release", id);
        return release;
    }

    public List<ExportRelease> listExportReleases(String harvestBatchId, String targetMarketCode, ExportReleaseStatus status) {
        return exportReleaseRepository.findAll().stream()
                .filter(this::hasValidReleaseAncestry)
                .filter(release -> harvestBatchId == null || harvestBatchId.equals(release.harvestBatchId()))
                .filter(release -> targetMarketCode == null || targetMarketCode.equalsIgnoreCase(release.targetMarketCode()))
                .filter(release -> status == null || status == release.status())
                .sorted(Comparator.comparing(ExportRelease::createdAt).reversed())
                .toList();
    }

    public CultivationCalendarDtos.TraceabilityResponse traceability(String id) {
        ExportRelease release = getExportRelease(id);
        return traceabilityResponse(release);
    }

    public List<AuditLog> auditLogs(CultivationActor actor, String resourceType, String resourceId) {
        requireStandardAdministrator(actor);
        if (StringUtils.hasText(resourceType) && StringUtils.hasText(resourceId)) {
            return auditLogRepository.findByResourceTypeAndResourceId(resourceType.trim(), resourceId.trim());
        }
        return auditLogRepository.findAll();
    }

    private CultivationActivity terminalStatus(String id, ActivityStatus status) {
        CultivationActivity current = getActivity(id);
        requireNotPendingApproval(current);
        if (current.status() == ActivityStatus.COMPLETED) {
            throw new IllegalStateException("Completed activity cannot be changed");
        }
        return withStatus(current, status, current.approvedBy(), current.approvedAt(), current.rejectionReason());
    }

    private boolean hasValidPlanAncestry(CultivationPlan plan) {
        if (!StringUtils.hasText(plan.cultivationSeasonId())
                || isLegacyUnverified("cultivation_plans", plan.id())) return false;
        return seasonRepository.findById(plan.cultivationSeasonId())
                .filter(season -> Objects.equals(season.farmId(), plan.farmId())
                        && Objects.equals(season.plotId(), plan.plotId()))
                .isPresent();
    }

    private boolean hasValidActivityAncestry(CultivationActivity activity) {
        if (!StringUtils.hasText(activity.cultivationPlanId())
                || isLegacyUnverified("cultivation_activities", activity.id())) return false;
        return planRepository.findById(activity.cultivationPlanId())
                .filter(this::hasValidPlanAncestry)
                .filter(plan -> Objects.equals(plan.farmId(), activity.farmId())
                        && Objects.equals(plan.plotId(), activity.plotId())
                        && Objects.equals(plan.cultivationSeasonId(), activity.cultivationSeasonId()))
                .isPresent();
    }

    private boolean hasValidBatchAncestry(HarvestBatch batch) {
        if (!StringUtils.hasText(batch.cultivationSeasonId())
                || isLegacyUnverified("harvest_batches", batch.id())) return false;
        return seasonRepository.findById(batch.cultivationSeasonId())
                .filter(season -> Objects.equals(season.farmId(), batch.farmId())
                        && Objects.equals(season.plotId(), batch.plotId()))
                .isPresent();
    }

    private boolean hasValidLabSampleAncestry(LabSample sample) {
        if (!StringUtils.hasText(sample.cultivationSeasonId())
                || isLegacyUnverified("lab_samples", sample.id())) return false;
        if (!seasonRepository.existsById(sample.cultivationSeasonId())) return false;
        if (!StringUtils.hasText(sample.harvestBatchId())) return true;
        return harvestBatchRepository.findById(sample.harvestBatchId())
                .filter(this::hasValidBatchAncestry)
                .filter(batch -> Objects.equals(batch.cultivationSeasonId(), sample.cultivationSeasonId()))
                .isPresent();
    }

    private boolean hasValidReleaseAncestry(ExportRelease release) {
        if (!StringUtils.hasText(release.harvestBatchId())
                || isLegacyUnverified("export_releases", release.id())) return false;
        return harvestBatchRepository.findById(release.harvestBatchId())
                .filter(this::hasValidBatchAncestry)
                .isPresent();
    }

    private boolean isLegacyUnverified(String collection, String id) {
        return StringUtils.hasText(id) && integrityMarkerRepository.existsById(collection + ":" + id);
    }

    private void requireSamePlanScope(
            CultivationPlan plan,
            CultivationCalendarDtos.CreateActivityRequest request) {
        if (!Objects.equals(plan.farmId(), request.farmId().trim())
                || !Objects.equals(plan.plotId(), request.plotId().trim())
                || !Objects.equals(plan.cultivationSeasonId(), request.cultivationSeasonId().trim())) {
            throw new IllegalArgumentException("Activity scope must match the cultivation plan scope");
        }
    }

    private void requireCanViewSeason(CultivationActor actor, String cultivationSeasonId) {
        List<ResourceScope> scopes = seasonScopes(cultivationSeasonId);
        if (scopes.isEmpty()) {
            throw notFound("Cultivation season", cultivationSeasonId);
        }
        scopes.forEach(scope -> accessGuard.requireView(actor, "CultivationSeason", cultivationSeasonId, scope.farmId(), scope.plotId()));
    }

    private List<ResourceScope> seasonScopes(String cultivationSeasonId) {
        return seasonRepository.findById(requireText(cultivationSeasonId, "Cultivation season is required"))
                .map(season -> List.of(new ResourceScope(season.farmId(), season.plotId())))
                .orElseGet(List::of);
    }

    private boolean canViewLabSample(CultivationActor actor, LabSample sample) {
        if (StringUtils.hasText(sample.harvestBatchId())) {
            HarvestBatch batch = getHarvestBatch(sample.harvestBatchId());
            return accessGuard.canView(actor, batch.farmId(), batch.plotId());
        }
        List<ResourceScope> scopes = seasonScopes(sample.cultivationSeasonId());
        return !scopes.isEmpty() && scopes.stream()
                .allMatch(scope -> accessGuard.canView(actor, scope.farmId(), scope.plotId()));
    }

    private void requireCanViewLabSample(CultivationActor actor, LabSample sample) {
        if (!canViewLabSample(actor, sample)) {
            throw new com.duriancare.cultivation.security.CultivationAccessDeniedException("Access denied for cultivation resource");
        }
    }

    private boolean canViewExportRelease(CultivationActor actor, ExportRelease release) {
        HarvestBatch batch = getHarvestBatch(release.harvestBatchId());
        return accessGuard.canView(actor, batch.farmId(), batch.plotId());
    }

    private record ResourceScope(String farmId, String plotId) {
    }

    private CultivationActivity withStatus(CultivationActivity current, ActivityStatus status, String approvedBy, Instant approvedAt, String rejectionReason) {
        return activityRepository.save(new CultivationActivity(
                current.id(), current.cultivationPlanId(), current.cultivationSeasonId(), current.farmId(), current.plotId(),
                current.treeIds(), current.activityType(), current.title(), current.description(), current.scheduledStartAt(),
                current.scheduledEndAt(), current.recurrenceRule(), current.priority(), status, current.assignedUserIds(),
                current.approvalRequired(), approvedBy, approvedAt, rejectionReason, current.version(), current.createdAt(), Instant.now()));
    }

    private static void requirePendingApproval(CultivationActivity activity) {
        if (!activity.approvalRequired()) {
            throw new IllegalStateException("Activity does not require approval");
        }
        if (activity.status() != ActivityStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Activity must be pending approval");
        }
    }

    private static void requireNotPendingApproval(CultivationActivity activity) {
        if (activity.approvalRequired() && activity.status() == ActivityStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Activity must be approved before execution");
        }
    }

    private static void requireSameActor(CultivationActor actor, String requestUserId) {
        String actorId = requireText(actor.userId(), "Authenticated user is required");
        String reviewerId = requireText(requestUserId, "Request user is required");
        if (!actorId.equals(reviewerId)) {
            throw new IllegalArgumentException("Approval user must match authenticated actor");
        }
    }

    private void validateInputCanBePlanned(AgriculturalInput input, CultivationCalendarDtos.CreateActivityRequest request) {
        if (input.status() == InputStatus.PROHIBITED || input.biologicalLevel() == BiologicalLevel.PROHIBITED) {
            throw new IllegalArgumentException("Prohibited agricultural input cannot be planned");
        }
        if (input.status() == InputStatus.INACTIVE) {
            throw new IllegalArgumentException("Inactive agricultural input cannot be planned");
        }
        if (input.prohibitedMarketCodes() != null
                && request.cultivationPlanId() != null
                && input.prohibitedMarketCodes().stream().anyMatch(StringUtils::hasText)
                && input.allowedMarketCodes() != null) {
            // Market-level blocking is also re-evaluated during compliance assessment.
        }
    }

    private void validateInputCanBeUsed(AgriculturalInput input, CultivationCalendarDtos.ActivityInputUsageRequest request) {
        if (request.quantityUsed() == null || request.quantityUsed().signum() <= 0) {
            throw new IllegalArgumentException("Quantity used must be positive");
        }
        requireText(request.quantityUnit(), "Quantity unit is required");
        if ((request.waterVolume() != null && request.waterVolume().signum() < 0)
                || (request.concentration() != null && request.concentration().signum() < 0)
                || (request.treatedArea() != null && request.treatedArea().signum() < 0)) {
            throw new IllegalArgumentException("Usage measurements must not be negative");
        }
        if (input.status() != InputStatus.VERIFIED) {
            throw new IllegalArgumentException("Only verified agricultural inputs can be used");
        }
        if (input.biologicalLevel() == BiologicalLevel.PROHIBITED) {
            throw new IllegalArgumentException("Prohibited agricultural input cannot be used");
        }
        if (request.expiryDate() != null && request.expiryDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Expired agricultural input batch cannot be used");
        }
    }

    private boolean requiresChemicalApproval(AgriculturalInput input) {
        return input.biologicalLevel() == BiologicalLevel.CHEMICAL
                || input.biologicalLevel() == BiologicalLevel.RESTRICTED_CHEMICAL;
    }

    private List<AgriculturalInput> loadInputs(List<String> ids) {
        return copy(ids).stream()
                .map(id -> agriculturalInputRepository.findById(id).orElseThrow(() -> notFound("Agricultural input", id)))
                .toList();
    }

    private List<ActivityInputUsage> inputUsagesForSeason(String cultivationSeasonId) {
        return listActivities(cultivationSeasonId, null, null).stream()
                .flatMap(activity -> executionRepository.findByCultivationActivityId(activity.id()).stream())
                .flatMap(execution -> inputUsageRepository.findByActivityExecutionId(execution.id()).stream())
                .toList();
    }

    private void evaluateInputUsage(
            ActivityInputUsage usage,
            String targetMarketCode,
            String commodityCode,
            LocalDate applicableOn,
            List<BlockingReason> blockers,
            List<ComplianceWarning> warnings) {
        if (usage.agriculturalInputSnapshot() == null) {
            blockers.add(BlockingReason.MISSING_INPUT_INFORMATION);
            return;
        }
        if (usage.agriculturalInputSnapshot().biologicalLevel() == BiologicalLevel.PROHIBITED) {
            blockers.add(BlockingReason.PROHIBITED_INPUT_USED);
        }
        if ((usage.agriculturalInputSnapshot().biologicalLevel() == BiologicalLevel.CHEMICAL
                || usage.agriculturalInputSnapshot().biologicalLevel() == BiologicalLevel.RESTRICTED_CHEMICAL)
                && usage.agriculturalInputSnapshot().preHarvestIntervalDays() == null) {
            blockers.add(BlockingReason.MISSING_PRE_HARVEST_INTERVAL);
        }
        if (usage.batchNumber() == null || usage.batchNumber().isBlank()) {
            warnings.add(ComplianceWarning.MISSING_BATCH_NUMBER);
        }
        if (usage.activeIngredientSnapshots().isEmpty()) {
            blockers.add(BlockingReason.MISSING_ACTIVE_INGREDIENT);
            return;
        }
        if (usage.agriculturalInputSnapshot().prohibitedMarketCodes().stream().anyMatch(targetMarketCode::equalsIgnoreCase)) {
            blockers.add(BlockingReason.ACTIVE_INGREDIENT_NOT_ALLOWED_FOR_MARKET);
        }
        if (!usage.agriculturalInputSnapshot().allowedMarketCodes().isEmpty()
                && usage.agriculturalInputSnapshot().allowedMarketCodes().stream().noneMatch(targetMarketCode::equalsIgnoreCase)) {
            blockers.add(BlockingReason.ACTIVE_INGREDIENT_NOT_ALLOWED_FOR_MARKET);
        }
        for (var ingredient : usage.activeIngredientSnapshots()) {
            if (ingredient == null || !StringUtils.hasText(ingredient.code())) {
                blockers.add(BlockingReason.MISSING_ACTIVE_INGREDIENT);
                continue;
            }
            boolean standardExists = resolveStandard(targetMarketCode, commodityCode,
                    normalizeCode(ingredient.code()), applicableOn).isPresent();
            if (!standardExists) {
                blockers.add(BlockingReason.NO_APPLICABLE_MRL);
            }
        }
    }

    private void evaluateLabResults(HarvestBatch harvestBatch, String targetMarketCode,
            String commodityCode, List<BlockingReason> blockers, List<ComplianceWarning> warnings) {
        if (harvestBatch == null) {
            return;
        }
        List<LabSample> samples = labSampleRepository.findByCultivationSeasonId(harvestBatch.cultivationSeasonId()).stream()
                .filter(sample -> harvestBatch.id().equals(sample.harvestBatchId()))
                .filter(sample -> sample.status() != LabSampleStatus.CANCELLED)
                .filter(sample -> sample.sampleType() == SampleType.FRUIT)
                .toList();
        if (samples.isEmpty()) {
            warnings.add(ComplianceWarning.LAB_NOT_ASSESSED);
            return;
        }
        Set<String> requiredFromUsage = inputUsagesForSeason(harvestBatch.cultivationSeasonId()).stream()
                .flatMap(usage -> usage.activeIngredientSnapshots().stream())
                .filter(ingredient -> ingredient != null && StringUtils.hasText(ingredient.code()))
                .map(ingredient -> normalizeCode(ingredient.code())).collect(Collectors.toSet());
        Set<String> recordedAcrossSamples = new java.util.HashSet<>();
        for (LabSample sample : samples) {
            if (sample.sampledAt() == null || sample.sampledAt().isAfter(Instant.now())) {
                blockers.add(BlockingReason.LAB_EVIDENCE_UNVERIFIED);
                continue;
            }
            List<LabResidueResult> results = labResidueResultRepository.findByLabSampleId(sample.id());
            Set<String> recorded = results.stream()
                    .filter(result -> StringUtils.hasText(result.activeIngredientCode()))
                    .map(result -> normalizeCode(result.activeIngredientCode()))
                    .collect(Collectors.toSet());
            recordedAcrossSamples.addAll(recorded);
            if (recorded.size() != results.size()) {
                blockers.add(BlockingReason.LAB_EVIDENCE_UNVERIFIED);
            }
            if (sample.status() != LabSampleStatus.RESULT_RECORDED
                    || sample.expectedActiveIngredientCodes().isEmpty()
                    || !recorded.containsAll(sample.expectedActiveIngredientCodes())) {
                blockers.add(BlockingReason.LAB_RESULT_PENDING);
            }
            LocalDate sampledOn = LocalDate.ofInstant(sample.sampledAt(), ZoneOffset.UTC);
            for (LabResidueResult result : results) {
                if (!StringUtils.hasText(result.activeIngredientCode())) {
                    blockers.add(BlockingReason.LAB_EVIDENCE_UNVERIFIED);
                    continue;
                }
                if (!LAB_EVALUATION_VERSION.equals(result.evaluationPolicyVersion())) {
                    blockers.add(BlockingReason.LAB_EVIDENCE_UNVERIFIED);
                    continue;
                }
                ResidueStandard standard = resolveStandard(targetMarketCode, commodityCode,
                        normalizeCode(result.activeIngredientCode()), sampledOn).orElse(null);
                LabResultStatus liveStatus = evaluateLabMeasurement(result.measuredValue(), result.unit(),
                        result.detectionLimit(), result.quantificationLimit(), standard);
                if (liveStatus == LabResultStatus.NO_STANDARD_FOUND) {
                    blockers.add(BlockingReason.NO_APPLICABLE_MRL);
                } else if (liveStatus == LabResultStatus.FAIL) {
                    blockers.add(BlockingReason.LAB_RESULT_FAILED);
                } else if (liveStatus == LabResultStatus.PENDING_REVIEW) {
                    blockers.add(BlockingReason.LAB_RESULT_PENDING);
                }
            }
        }
        if (!recordedAcrossSamples.containsAll(requiredFromUsage)) {
            blockers.add(BlockingReason.LAB_RESULT_PENDING);
        }
    }

    private Map<String, Object> traceabilitySnapshot(HarvestBatch batch, ComplianceAssessment assessment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("harvestBatch", batch);
        snapshot.put("careHistory", careHistory(batch.cultivationSeasonId()));
        snapshot.put("complianceAssessment", complianceSnapshot(assessment));
        List<LabSample> samples = labSampleRepository.findByCultivationSeasonId(batch.cultivationSeasonId()).stream()
                .filter(sample -> batch.id().equals(sample.harvestBatchId())).toList();
        snapshot.put("labSamples", samples);
        snapshot.put("labResidueResults", samples.stream()
                .flatMap(sample -> labResidueResultRepository.findByLabSampleId(sample.id()).stream()).toList());
        String commodity = normalizeCode(requireSeason(batch.cultivationSeasonId()).crop());
        List<Map<String, Object>> resolvedLabEvidence = new ArrayList<>();
        for (LabSample sample : samples) {
            LocalDate sampledOn = sample.sampledAt() == null ? null
                    : LocalDate.ofInstant(sample.sampledAt(), ZoneOffset.UTC);
            for (LabResidueResult result : labResidueResultRepository.findByLabSampleId(sample.id())) {
                ResidueStandard standard = sampledOn != null && StringUtils.hasText(result.activeIngredientCode())
                        ? resolveStandard(assessment.targetMarketCode(), commodity,
                                normalizeCode(result.activeIngredientCode()), sampledOn).orElse(null)
                        : null;
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("labSampleId", sample.id());
                evidence.put("labResultId", result.id());
                evidence.put("activeIngredientCode", result.activeIngredientCode());
                evidence.put("measuredValue", result.measuredValue());
                evidence.put("measuredUnit", result.unit());
                evidence.put("residueStandardId", standard == null ? null : standard.id());
                evidence.put("applicableMrl", standard == null ? null : standard.mrlValue());
                evidence.put("mrlUnit", standard == null ? null : standard.unit());
                evidence.put("standardSourceType", standard == null ? null : standard.sourceType());
                evidence.put("standardSourceReference", standard == null ? null : standard.sourceReference());
                evidence.put("standardEffectiveFrom", standard == null ? null : standard.effectiveFrom());
                evidence.put("standardEffectiveTo", standard == null ? null : standard.effectiveTo());
                evidence.put("sampledOn", sampledOn);
                evidence.put("evaluationPolicyVersion", result.evaluationPolicyVersion());
                evidence.put("liveResultStatus", LAB_EVALUATION_VERSION.equals(result.evaluationPolicyVersion())
                        ? evaluateLabMeasurement(result.measuredValue(), result.unit(), result.detectionLimit(),
                                result.quantificationLimit(), standard)
                        : LabResultStatus.PENDING_REVIEW);
                resolvedLabEvidence.add(evidence);
            }
        }
        snapshot.put("resolvedLabEvidence", resolvedLabEvidence);
        snapshot.put("labAssessmentStatus", assessment.warnings().contains(ComplianceWarning.LAB_NOT_ASSESSED) ? "NOT_ASSESSED"
                : assessment.blockingReasons().stream().anyMatch(reason -> reason == BlockingReason.LAB_RESULT_PENDING
                        || reason == BlockingReason.LAB_RESULT_FAILED || reason == BlockingReason.LAB_EVIDENCE_UNVERIFIED
                        || reason == BlockingReason.NO_APPLICABLE_MRL) ? "NOT_CLEARED" : "ASSESSED");
        snapshot.put("generatedAt", Instant.now());
        return snapshot;
    }

    private Map<String, Object> complianceSnapshot(ComplianceAssessment assessment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", assessment.id());
        snapshot.put("riskScore", assessment.riskScore());
        snapshot.put("riskLevel", assessment.riskLevel());
        snapshot.put("blockingReasons", assessment.blockingReasons());
        snapshot.put("warnings", assessment.warnings());
        snapshot.put("eligibleForExportRelease", assessment.eligibleForExportRelease());
        snapshot.put("assessedAt", assessment.assessedAt());
        return snapshot;
    }

    private Optional<ResidueStandard> resolveStandard(String marketCode, String commodityCode,
            String ingredientCode, LocalDate applicableOn) {
        List<ResidueStandard> candidates = residueStandardRepository
                .findByMarketCodeAndCommodityCodeAndActiveIngredientCodeAndActiveTrueAndVerifiedTrueOrderByEffectiveFromDesc(
                        marketCode, commodityCode, ingredientCode);
        if (candidates == null) {
            return Optional.empty();
        }
        List<ResidueStandard> applicable = candidates.stream()
                .filter(standard -> standard.active() && standard.verified()
                        && StringUtils.hasText(standard.marketCode())
                        && StringUtils.hasText(standard.commodityCode())
                        && StringUtils.hasText(standard.activeIngredientCode())
                        && marketCode.equals(normalizeCode(standard.marketCode()))
                        && commodityCode.equals(normalizeCode(standard.commodityCode()))
                        && ingredientCode.equals(normalizeCode(standard.activeIngredientCode()))
                        && standard.effectiveFrom() != null && !standard.effectiveFrom().isAfter(applicableOn)
                        && (standard.effectiveTo() == null || !standard.effectiveTo().isBefore(applicableOn))
                        && standard.mrlValue() != null && standard.mrlValue().signum() >= 0
                        && StringUtils.hasText(standard.unit()))
                .sorted(Comparator.comparing(ResidueStandard::effectiveFrom).reversed()).toList();
        if (applicable.isEmpty()) {
            return Optional.empty();
        }
        if (applicable.size() > 1
                && applicable.get(0).effectiveFrom().equals(applicable.get(1).effectiveFrom())) {
            return Optional.empty();
        }
        return Optional.of(applicable.get(0));
    }

    private LabResultStatus evaluateLabMeasurement(BigDecimal measuredValue, String measuredUnit,
            BigDecimal detectionLimit, BigDecimal quantificationLimit, ResidueStandard standard) {
        if (standard == null) {
            return LabResultStatus.NO_STANDARD_FOUND;
        }
        String measurementUnit = canonicalResidueUnit(measuredUnit);
        if (measurementUnit == null || !measurementUnit.equals(canonicalResidueUnit(standard.unit()))) {
            return LabResultStatus.PENDING_REVIEW;
        }
        if (measuredValue != null && measuredValue.signum() < 0
                || detectionLimit != null && detectionLimit.signum() < 0
                || quantificationLimit != null && quantificationLimit.signum() < 0
                || detectionLimit != null && quantificationLimit != null
                        && quantificationLimit.compareTo(detectionLimit) < 0) {
            return LabResultStatus.PENDING_REVIEW;
        }
        if (measuredValue == null && detectionLimit == null) {
            return LabResultStatus.PENDING_REVIEW;
        }
        if (detectionLimit != null && detectionLimit.compareTo(standard.mrlValue()) > 0
                && (measuredValue == null || measuredValue.compareTo(detectionLimit) < 0)) {
            return LabResultStatus.PENDING_REVIEW;
        }
        if (quantificationLimit != null && quantificationLimit.compareTo(standard.mrlValue()) > 0
                && measuredValue != null && measuredValue.compareTo(quantificationLimit) < 0) {
            return LabResultStatus.PENDING_REVIEW;
        }
        return labResidueComparator.compare(measuredValue, detectionLimit, quantificationLimit, standard.mrlValue());
    }

    private String canonicalResidueUnit(String unit) {
        if (!StringUtils.hasText(unit)) {
            return null;
        }
        String normalized = unit.trim().toLowerCase(Locale.ROOT).replace('µ', 'u').replace('μ', 'u');
        return Set.of("mg/kg", "ug/kg", "ppm").contains(normalized) ? normalized : null;
    }

    private ComplianceAssessment reassessRelease(ExportRelease release, String actorId) {
        HarvestBatch batch = getHarvestBatch(release.harvestBatchId());
        requireMarketMatchesBatch(batch, release.targetMarketCode());
        ComplianceAssessment assessment = assessCompliance(batch.cultivationSeasonId(),
                new CultivationCalendarDtos.AssessComplianceRequest(batch.id(), release.targetMarketCode()), actorId);
        if (!assessment.eligibleForExportRelease()) {
            throw new IllegalStateException("Export release is blocked by current compliance: "
                    + assessment.blockingReasons() + "; warnings=" + assessment.warnings());
        }
        return assessment;
    }

    private ExportRelease saveAssessedRelease(ExportRelease release, ComplianceAssessment assessment,
            ExportReleaseStatus status, String reviewerId, Instant releasedAt) {
        HarvestBatch batch = getHarvestBatch(release.harvestBatchId());
        return exportReleaseRepository.save(new ExportRelease(
                release.id(), release.releaseCode(), release.harvestBatchId(), release.targetMarketCode(),
                traceabilitySnapshot(batch, assessment), complianceSnapshot(assessment), status,
                release.submittedBy(), release.submittedAt(), reviewerId, Instant.now(),
                release.rejectionReason(), releasedAt, release.createdAt()));
    }

    private CultivationCalendarDtos.TraceabilityResponse traceabilityResponse(ExportRelease release) {
        boolean recalled = release.status() == ExportReleaseStatus.RECALLED;
        Instant recalledAt = recalled ? auditLogRepository.findByResourceTypeAndResourceId("ExportRelease", release.id()).stream()
                .filter(log -> log.action() == AuditAction.EXPORT_RELEASE_RECALLED)
                .map(AuditLog::createdAt).max(Comparator.naturalOrder()).orElse(null) : null;
        return new CultivationCalendarDtos.TraceabilityResponse(release.id(), release.status(),
                recalledAt, recalled ? release.rejectionReason() : null,
                release.traceabilitySnapshot());
    }

    private LabSample withLabSampleStatus(LabSample sample, LabSampleStatus status) {
        return new LabSample(sample.id(), sample.cultivationSeasonId(), sample.harvestBatchId(),
                sample.sampleCode(), sample.sampleType(), sample.sampledAt(), sample.sampledBy(),
                sample.samplingLocation(), sample.laboratoryName(), sample.laboratoryAccreditation(),
                status, sample.attachments(), sample.createdAt(), sample.expectedActiveIngredientCodes());
    }

    private CultivationSeason requireSeason(String id) {
        return seasonRepository.findById(requireText(id, "Cultivation season is required"))
                .orElseThrow(() -> notFound("Cultivation season", id));
    }

    private void requireBatchScope(CultivationSeason season, String farmId, String plotId) {
        if (!Objects.equals(season.farmId(), farmId) || !Objects.equals(season.plotId(), plotId)) {
            throw new IllegalArgumentException("Harvest batch farm and zone must match its cultivation season");
        }
    }

    private void requireMarketMatchesBatch(HarvestBatch batch, String targetMarketCode) {
        if (!normalizeCode(batch.expectedDestinationMarket()).equals(normalizeCode(targetMarketCode))) {
            throw new IllegalArgumentException("Target market must match the harvest batch destination market");
        }
    }

    private void requireReleaseStatus(ExportRelease release, ExportReleaseStatus required) {
        if (release.status() != required) {
            throw new IllegalStateException("Export release must be " + required + " for this action");
        }
    }

    private void requireStandardAdministrator(CultivationActor actor) {
        if (actor == null || !StringUtils.hasText(actor.userId()) || !"ADMIN".equalsIgnoreCase(actor.role())) {
            throw new CultivationAccessDeniedException("Standard administration requires an authenticated administrator");
        }
    }

    private void requireTechnicalReviewer(CultivationActor actor) {
        if (actor == null || !StringUtils.hasText(actor.userId())
                || !("EXPERT".equalsIgnoreCase(actor.role()) || "ENGINEER".equalsIgnoreCase(actor.role()))) {
            throw new CultivationAccessDeniedException("This action requires an authorized expert or engineer");
        }
    }

    private static String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private ExportRelease saveRelease(
            ExportRelease release,
            ExportReleaseStatus status,
            String submittedBy,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            String rejectionReason,
            Instant releasedAt) {
        return exportReleaseRepository.save(new ExportRelease(
                release.id(), release.releaseCode(), release.harvestBatchId(), release.targetMarketCode(),
                release.traceabilitySnapshot(), release.complianceAssessmentSnapshot(), status, submittedBy, submittedAt,
                reviewedBy, reviewedAt, rejectionReason, releasedAt, release.createdAt()));
    }

    private void audit(AuditAction action, String resourceType, String resourceId, String actorId, String reason, Map<String, Object> details) {
        auditLogRepository.save(new AuditLog(
                newId(),
                action,
                resourceType,
                resourceId,
                actorId,
                reason,
                details == null ? Map.of() : Map.copyOf(details),
                Instant.now()));
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static NoSuchElementException notFound(String resource, String id) {
        return new NoSuchElementException(resource + " not found: " + id);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
