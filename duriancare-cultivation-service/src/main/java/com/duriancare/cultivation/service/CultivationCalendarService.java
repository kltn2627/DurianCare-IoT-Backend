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
import com.duriancare.cultivation.domain.AuditAction;
import com.duriancare.cultivation.domain.AuditLog;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceAssessment;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.CultivationActivity;
import com.duriancare.cultivation.domain.CultivationPlan;
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
import com.duriancare.cultivation.repository.ActivityExecutionRepository;
import com.duriancare.cultivation.repository.ActivityInputUsageRepository;
import com.duriancare.cultivation.repository.AgriculturalInputRepository;
import com.duriancare.cultivation.repository.AuditLogRepository;
import com.duriancare.cultivation.repository.ComplianceAssessmentRepository;
import com.duriancare.cultivation.repository.CultivationActivityRepository;
import com.duriancare.cultivation.repository.CultivationPlanRepository;
import com.duriancare.cultivation.repository.ExportReleaseRepository;
import com.duriancare.cultivation.repository.HarvestBatchRepository;
import com.duriancare.cultivation.repository.LabResidueResultRepository;
import com.duriancare.cultivation.repository.LabSampleRepository;
import com.duriancare.cultivation.repository.ResidueStandardRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CultivationCalendarService {

    private static final String DURIAN_COMMODITY_CODE = "DURIAN";
    private static final String RULES_VERSION = "cultivation-calendar-v1";

    private final CultivationPlanRepository planRepository;
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

    public CultivationCalendarService(
            CultivationPlanRepository planRepository,
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
            LabResidueComparator labResidueComparator) {
        this.planRepository = planRepository;
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
    }

    public CultivationPlan createPlan(CultivationCalendarDtos.CreatePlanRequest request) {
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
        return planRepository.findById(id).orElseThrow(() -> notFound("Cultivation plan", id));
    }

    public List<CultivationPlan> listPlans(String farmId, String plotId, String cultivationSeasonId) {
        return planRepository.findAll().stream()
                .filter(plan -> farmId == null || farmId.equals(plan.farmId()))
                .filter(plan -> plotId == null || plotId.equals(plan.plotId()))
                .filter(plan -> cultivationSeasonId == null || cultivationSeasonId.equals(plan.cultivationSeasonId()))
                .sorted(Comparator.comparing(CultivationPlan::startDate).reversed())
                .toList();
    }

    public List<CultivationActivity> getPlanCalendar(String id) {
        getPlan(id);
        return activityRepository.findByCultivationPlanId(id).stream()
                .sorted(Comparator.comparing(CultivationActivity::scheduledStartAt))
                .toList();
    }

    public CultivationActivity createActivity(CultivationCalendarDtos.CreateActivityRequest request) {
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
                .filter(activity -> cultivationSeasonId == null || cultivationSeasonId.equals(activity.cultivationSeasonId()))
                .filter(activity -> activityType == null || activityType == activity.activityType())
                .filter(activity -> status == null || status == activity.status())
                .sorted(Comparator.comparing(CultivationActivity::scheduledStartAt))
                .toList();
    }

    public CultivationActivity getActivity(String id) {
        return activityRepository.findById(id).orElseThrow(() -> notFound("Cultivation activity", id));
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
        if (!current.approvalRequired()) {
            throw new IllegalStateException("Activity does not require approval");
        }
        CultivationActivity approved = withStatus(current, ActivityStatus.APPROVED, userId, Instant.now(), null);
        audit(AuditAction.CULTIVATION_ACTIVITY_APPROVED, "CultivationActivity", approved.id(), userId, null, Map.of());
        return approved;
    }

    public CultivationActivity rejectActivity(String id, String userId, String reason) {
        CultivationActivity current = getActivity(id);
        if (!current.approvalRequired()) {
            throw new IllegalStateException("Activity does not require approval");
        }
        CultivationActivity rejected = withStatus(current, ActivityStatus.CANCELLED, userId, Instant.now(), reason);
        audit(AuditAction.CULTIVATION_ACTIVITY_REJECTED, "CultivationActivity", rejected.id(), userId, reason, Map.of());
        return rejected;
    }

    public CultivationActivity startActivity(String id) {
        CultivationActivity current = getActivity(id);
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
        if (activity.status() == ActivityStatus.CANCELLED || activity.status() == ActivityStatus.COMPLETED) {
            throw new IllegalStateException("Cancelled or completed activity cannot be completed");
        }
        if (request.completedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("Completion time must not be before start time");
        }
        ActivityExecution execution = executionRepository.save(new ActivityExecution(
                newId(), id, request.startedAt(), request.completedAt(), request.executedBy().trim(),
                trimToNull(request.supervisedBy()), request.actualTreeCount(), request.actualArea(), request.areaUnit(),
                request.weatherSnapshot(), request.applicationMethod(), request.equipment(), request.notes(),
                request.resultObservation(), copy(request.evidenceFiles()), true, Instant.now()));
        List<ActivityInputUsage> usages = new ArrayList<>();
        LocalDate completedDate = LocalDate.ofInstant(request.completedAt(), ZoneOffset.UTC);
        for (CultivationCalendarDtos.ActivityInputUsageRequest usageRequest : copy(request.inputUsages())) {
            AgriculturalInput input = agriculturalInputRepository.findById(usageRequest.agriculturalInputId())
                    .orElseThrow(() -> notFound("Agricultural input", usageRequest.agriculturalInputId()));
            validateInputCanBeUsed(input, usageRequest);
            LocalDate safeHarvestDate = safeHarvestDateCalculator.safeHarvestDate(
                    completedDate,
                    input.preHarvestIntervalDays());
            usages.add(inputUsageRepository.save(new ActivityInputUsage(
                    newId(), execution.id(), input.id(), CultivationCalendarDtos.snapshotOf(input),
                    trimToNull(usageRequest.batchNumber()), usageRequest.expiryDate(), usageRequest.quantityUsed(),
                    usageRequest.quantityUnit(), usageRequest.waterVolume(), usageRequest.waterVolumeUnit(),
                    usageRequest.concentration(), usageRequest.concentrationUnit(), usageRequest.treatedArea(),
                    usageRequest.areaUnit(), input.activeIngredients(), safeHarvestDate, Instant.now())));
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
        return activityRepository.findByCultivationSeasonIdAndActivityType(cultivationSeasonId, ActivityType.CHEMICAL_TREATMENT);
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

    public ResidueStandard createResidueStandard(CultivationCalendarDtos.CreateResidueStandardRequest request) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new IllegalArgumentException("Effective-to date must not be before effective-from date");
        }
        Instant now = Instant.now();
        ResidueStandard saved = residueStandardRepository.save(new ResidueStandard(
                newId(), request.marketCode(), request.commodityCode(), request.commodityName(),
                request.activeIngredientCode(), request.activeIngredientName(), request.mrlValue(), request.unit(),
                request.effectiveFrom(), request.effectiveTo(), request.sourceType(), request.sourceReference(), null,
                request.verified(), request.active(), now, now));
        audit(AuditAction.RESIDUE_STANDARD_CREATED, "ResidueStandard", saved.id(), null, null,
                Map.of("marketCode", saved.marketCode(), "activeIngredientCode", saved.activeIngredientCode()));
        return saved;
    }

    public List<ResidueStandard> importResidueStandards(List<CultivationCalendarDtos.CreateResidueStandardRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream().map(this::createResidueStandard).toList();
    }

    public List<ResidueStandard> listResidueStandards(String marketCode, String commodityCode, String activeIngredientCode) {
        return residueStandardRepository.findAll().stream()
                .filter(standard -> marketCode == null || marketCode.equalsIgnoreCase(standard.marketCode()))
                .filter(standard -> commodityCode == null || commodityCode.equalsIgnoreCase(standard.commodityCode()))
                .filter(standard -> activeIngredientCode == null || activeIngredientCode.equalsIgnoreCase(standard.activeIngredientCode()))
                .toList();
    }

    public LabSample createLabSample(CultivationCalendarDtos.CreateLabSampleRequest request) {
        LabSample saved = labSampleRepository.save(new LabSample(
                newId(), request.cultivationSeasonId(), trimToNull(request.harvestBatchId()), request.sampleCode(),
                request.sampleType(), request.sampledAt(), request.sampledBy(), request.samplingLocation(),
                request.laboratoryName(), request.laboratoryAccreditation(),
                request.status() == null ? LabSampleStatus.COLLECTED : request.status(),
                copy(request.attachments()), Instant.now()));
        audit(AuditAction.LAB_SAMPLE_CREATED, "LabSample", saved.id(), saved.sampledBy(), null,
                Map.of("sampleCode", saved.sampleCode()));
        return saved;
    }

    public List<LabSample> listLabSamples(String cultivationSeasonId, String harvestBatchId) {
        return labSampleRepository.findAll().stream()
                .filter(sample -> cultivationSeasonId == null || cultivationSeasonId.equals(sample.cultivationSeasonId()))
                .filter(sample -> harvestBatchId == null || harvestBatchId.equals(sample.harvestBatchId()))
                .sorted(Comparator.comparing(LabSample::sampledAt).reversed())
                .toList();
    }

    public LabSample getLabSample(String id) {
        return labSampleRepository.findById(id).orElseThrow(() -> notFound("Lab sample", id));
    }

    public LabResidueResult createLabResult(CultivationCalendarDtos.CreateLabResultRequest request) {
        LabResultStatus status = request.resultStatus() == null
                ? labResidueComparator.compare(request.measuredValue(), request.detectionLimit(), request.quantificationLimit(), request.applicableMrl())
                : request.resultStatus();
        LabResidueResult saved = labResidueResultRepository.save(new LabResidueResult(
                newId(), request.labSampleId(), request.activeIngredientCode(), request.activeIngredientName(),
                request.measuredValue(), request.unit(), request.detectionLimit(), request.quantificationLimit(),
                request.applicableMrl(), request.applicableMrlSource(), status, request.verifiedBy(),
                request.verifiedBy() == null ? null : Instant.now(), Instant.now()));
        audit(AuditAction.LAB_RESULT_RECORDED, "LabResidueResult", saved.id(), saved.verifiedBy(), null,
                Map.of("labSampleId", saved.labSampleId(), "resultStatus", saved.resultStatus().name()));
        return saved;
    }

    public HarvestBatch createHarvestBatch(CultivationCalendarDtos.CreateHarvestBatchRequest request) {
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
                earliestSafeHarvestDate, riskLevel, status, request.createdBy(), Instant.now()));
        audit(AuditAction.HARVEST_BATCH_CREATED, "HarvestBatch", saved.id(), saved.createdBy(), null,
                Map.of("batchCode", saved.batchCode(), "status", saved.status().name()));
        if (request.overrideSafeHarvestDate()) {
            audit(AuditAction.SAFE_HARVEST_DATE_OVERRIDDEN, "HarvestBatch", saved.id(), saved.createdBy(), request.overrideReason(),
                    Map.of("earliestSafeHarvestDate", String.valueOf(earliestSafeHarvestDate)));
        }
        return saved;
    }

    public HarvestBatch getHarvestBatch(String id) {
        return harvestBatchRepository.findById(id).orElseThrow(() -> notFound("Harvest batch", id));
    }

    public List<HarvestBatch> listHarvestBatches(String cultivationSeasonId, String farmId, String plotId) {
        return harvestBatchRepository.findAll().stream()
                .filter(batch -> cultivationSeasonId == null || cultivationSeasonId.equals(batch.cultivationSeasonId()))
                .filter(batch -> farmId == null || farmId.equals(batch.farmId()))
                .filter(batch -> plotId == null || plotId.equals(batch.plotId()))
                .sorted(Comparator.comparing(HarvestBatch::harvestedAt).reversed())
                .toList();
    }

    public ComplianceAssessment assessCompliance(String cultivationSeasonId, CultivationCalendarDtos.AssessComplianceRequest request) {
        List<BlockingReason> blockers = new ArrayList<>();
        List<ComplianceWarning> warnings = new ArrayList<>();
        HarvestBatch harvestBatch = request.harvestBatchId() == null ? null : getHarvestBatch(request.harvestBatchId());
        LocalDate harvestDate = harvestBatch == null ? null : LocalDate.ofInstant(harvestBatch.harvestedAt(), ZoneOffset.UTC);
        LocalDate earliestSafeHarvestDate = safeHarvestDate(cultivationSeasonId).orElse(null);
        if (harvestDate != null && earliestSafeHarvestDate != null && harvestDate.isBefore(earliestSafeHarvestDate)) {
            blockers.add(BlockingReason.PRE_HARVEST_INTERVAL_NOT_MET);
        }
        for (ActivityInputUsage usage : inputUsagesForSeason(cultivationSeasonId)) {
            evaluateInputUsage(usage, request.targetMarketCode(), blockers, warnings);
        }
        evaluateLabResults(harvestBatch, blockers);
        ComplianceDecision decision = complianceEngine.assess(blockers, warnings);
        ComplianceAssessment saved = complianceAssessmentRepository.save(new ComplianceAssessment(
                newId(), cultivationSeasonId, request.harvestBatchId(), request.targetMarketCode(), decision.riskScore(),
                decision.riskLevel(), decision.blockingReasons(), decision.warnings(), earliestSafeHarvestDate,
                decision.requiresLabTest(), decision.eligibleForHarvest(), decision.eligibleForExportRelease(),
                Map.of("rulesVersion", RULES_VERSION), Instant.now(), RULES_VERSION));
        audit(AuditAction.COMPLIANCE_ASSESSED, "ComplianceAssessment", saved.id(), null, null,
                Map.of("riskLevel", saved.riskLevel().name(), "targetMarketCode", saved.targetMarketCode()));
        return saved;
    }

    public ComplianceAssessment assessExportRelease(CultivationCalendarDtos.AssessComplianceRequest request) {
        HarvestBatch batch = getHarvestBatch(requireText(request.harvestBatchId(), "Harvest batch is required"));
        return assessCompliance(batch.cultivationSeasonId(), request);
    }

    public ExportRelease createExportRelease(CultivationCalendarDtos.CreateExportReleaseRequest request) {
        HarvestBatch batch = getHarvestBatch(request.harvestBatchId());
        ComplianceAssessment assessment = assessCompliance(batch.cultivationSeasonId(),
                new CultivationCalendarDtos.AssessComplianceRequest(batch.id(), request.targetMarketCode()));
        ExportReleaseStatus status = assessment.eligibleForExportRelease() ? ExportReleaseStatus.DRAFT : ExportReleaseStatus.BLOCKED;
        ExportRelease saved = exportReleaseRepository.save(new ExportRelease(
                newId(), request.releaseCode(), batch.id(), request.targetMarketCode(),
                traceabilitySnapshot(batch, assessment), complianceSnapshot(assessment), status,
                request.submittedBy(), null, null, null, null, null, Instant.now()));
        audit(AuditAction.EXPORT_RELEASE_CREATED, "ExportRelease", saved.id(), saved.submittedBy(), null,
                Map.of("status", saved.status().name(), "harvestBatchId", saved.harvestBatchId()));
        return saved;
    }

    public ExportRelease submitExportRelease(String id, String userId) {
        ExportRelease release = getExportRelease(id);
        ExportRelease submitted = saveRelease(release, ExportReleaseStatus.UNDER_REVIEW, userId, Instant.now(), release.reviewedBy(), release.reviewedAt(), null, release.releasedAt());
        audit(AuditAction.EXPORT_RELEASE_SUBMITTED, "ExportRelease", submitted.id(), userId, null, Map.of());
        return submitted;
    }

    public ExportRelease approveExportRelease(String id, String reviewerId) {
        ExportRelease release = getExportRelease(id);
        if (release.status() == ExportReleaseStatus.BLOCKED) {
            throw new IllegalStateException("Blocked export release cannot be approved");
        }
        ExportRelease approved = saveRelease(release, ExportReleaseStatus.APPROVED, release.submittedBy(), release.submittedAt(), reviewerId, Instant.now(), null, release.releasedAt());
        audit(AuditAction.EXPORT_RELEASE_APPROVED, "ExportRelease", approved.id(), reviewerId, null, Map.of());
        return approved;
    }

    public ExportRelease releaseExportRelease(String id, String reviewerId) {
        ExportRelease release = getExportRelease(id);
        if (release.status() != ExportReleaseStatus.APPROVED) {
            throw new IllegalStateException("Only approved export releases can be released");
        }
        ExportRelease released = saveRelease(release, ExportReleaseStatus.RELEASED, release.submittedBy(), release.submittedAt(), reviewerId, Instant.now(), null, Instant.now());
        audit(AuditAction.EXPORT_RELEASE_RELEASED, "ExportRelease", released.id(), reviewerId, null, Map.of());
        return released;
    }

    public ExportRelease recallExportRelease(String id, String reviewerId, String reason) {
        ExportRelease release = getExportRelease(id);
        ExportRelease recalled = saveRelease(release, ExportReleaseStatus.RECALLED, release.submittedBy(), release.submittedAt(), reviewerId, Instant.now(), reason, release.releasedAt());
        audit(AuditAction.EXPORT_RELEASE_RECALLED, "ExportRelease", recalled.id(), reviewerId, reason, Map.of());
        return recalled;
    }

    public ExportRelease getExportRelease(String id) {
        return exportReleaseRepository.findById(id).orElseThrow(() -> notFound("Export release", id));
    }

    public List<ExportRelease> listExportReleases(String harvestBatchId, String targetMarketCode, ExportReleaseStatus status) {
        return exportReleaseRepository.findAll().stream()
                .filter(release -> harvestBatchId == null || harvestBatchId.equals(release.harvestBatchId()))
                .filter(release -> targetMarketCode == null || targetMarketCode.equalsIgnoreCase(release.targetMarketCode()))
                .filter(release -> status == null || status == release.status())
                .sorted(Comparator.comparing(ExportRelease::createdAt).reversed())
                .toList();
    }

    public CultivationCalendarDtos.TraceabilityResponse traceability(String id) {
        ExportRelease release = getExportRelease(id);
        return new CultivationCalendarDtos.TraceabilityResponse(id, release.traceabilitySnapshot());
    }

    public List<AuditLog> auditLogs(String resourceType, String resourceId) {
        if (StringUtils.hasText(resourceType) && StringUtils.hasText(resourceId)) {
            return auditLogRepository.findByResourceTypeAndResourceId(resourceType.trim(), resourceId.trim());
        }
        return auditLogRepository.findAll();
    }

    private CultivationActivity terminalStatus(String id, ActivityStatus status) {
        CultivationActivity current = getActivity(id);
        if (current.status() == ActivityStatus.COMPLETED) {
            throw new IllegalStateException("Completed activity cannot be changed");
        }
        return withStatus(current, status, current.approvedBy(), current.approvedAt(), current.rejectionReason());
    }

    private CultivationActivity withStatus(CultivationActivity current, ActivityStatus status, String approvedBy, Instant approvedAt, String rejectionReason) {
        return activityRepository.save(new CultivationActivity(
                current.id(), current.cultivationPlanId(), current.cultivationSeasonId(), current.farmId(), current.plotId(),
                current.treeIds(), current.activityType(), current.title(), current.description(), current.scheduledStartAt(),
                current.scheduledEndAt(), current.recurrenceRule(), current.priority(), status, current.assignedUserIds(),
                current.approvalRequired(), approvedBy, approvedAt, rejectionReason, current.version(), current.createdAt(), Instant.now()));
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
            List<BlockingReason> blockers,
            List<ComplianceWarning> warnings) {
        if (usage.agriculturalInputSnapshot() == null) {
            blockers.add(BlockingReason.MISSING_INPUT_INFORMATION);
            return;
        }
        if (usage.agriculturalInputSnapshot().biologicalLevel() == BiologicalLevel.PROHIBITED) {
            blockers.add(BlockingReason.PROHIBITED_INPUT_USED);
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
            boolean standardExists = residueStandardRepository
                    .findFirstByMarketCodeAndCommodityCodeAndActiveIngredientCodeAndActiveTrueOrderByEffectiveFromDesc(
                            targetMarketCode, DURIAN_COMMODITY_CODE, ingredient.code())
                    .isPresent();
            if (!standardExists) {
                blockers.add(BlockingReason.NO_APPLICABLE_MRL);
            }
        }
    }

    private void evaluateLabResults(HarvestBatch harvestBatch, List<BlockingReason> blockers) {
        if (harvestBatch == null) {
            return;
        }
        List<LabSample> samples = labSampleRepository.findByCultivationSeasonId(harvestBatch.cultivationSeasonId()).stream()
                .filter(sample -> harvestBatch.id().equals(sample.harvestBatchId()))
                .toList();
        if (samples.isEmpty()) {
            return;
        }
        for (LabSample sample : samples) {
            List<LabResidueResult> results = labResidueResultRepository.findByLabSampleId(sample.id());
            if (results.isEmpty()) {
                blockers.add(BlockingReason.LAB_RESULT_PENDING);
            }
            for (LabResidueResult result : results) {
                if (result.resultStatus() == LabResultStatus.FAIL) {
                    blockers.add(BlockingReason.LAB_RESULT_FAILED);
                } else if (result.resultStatus() == LabResultStatus.PENDING_REVIEW) {
                    blockers.add(BlockingReason.LAB_RESULT_PENDING);
                }
            }
        }
    }

    private Map<String, Object> traceabilitySnapshot(HarvestBatch batch, ComplianceAssessment assessment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("harvestBatch", batch);
        snapshot.put("careHistory", careHistory(batch.cultivationSeasonId()));
        snapshot.put("complianceAssessment", complianceSnapshot(assessment));
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
