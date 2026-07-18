package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.AgriculturalInputSnapshot;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.BlockingReason;
import com.duriancare.cultivation.domain.ComplianceWarning;
import com.duriancare.cultivation.domain.InputCategory;
import com.duriancare.cultivation.domain.InputStatus;
import com.duriancare.cultivation.domain.LabResultStatus;
import com.duriancare.cultivation.domain.LabSampleStatus;
import com.duriancare.cultivation.domain.SampleType;
import com.duriancare.cultivation.domain.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CultivationCalendarDtos {

    private CultivationCalendarDtos() {
    }

    public record CreatePlanRequest(
            @NotBlank String farmId,
            @NotBlank String plotId,
            @NotBlank String cultivationSeasonId,
            String templateId,
            @NotBlank @Size(max = 160) String name,
            @NotNull LocalDate startDate,
            LocalDate expectedHarvestDate,
            List<String> targetMarketCodes,
            @NotBlank String createdBy) {
    }

    public record CreateActivityRequest(
            @NotBlank String cultivationPlanId,
            @NotBlank String cultivationSeasonId,
            @NotBlank String farmId,
            @NotBlank String plotId,
            List<String> treeIds,
            @NotNull ActivityType activityType,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotNull Instant scheduledStartAt,
            Instant scheduledEndAt,
            String recurrenceRule,
            String priority,
            List<String> assignedUserIds,
            boolean approvalRequired,
            List<String> agriculturalInputIds,
            String targetPestOrDisease,
            String biologicalControlReason,
            String approvalUserId) {
    }

    public record UpdateActivityRequest(
            @Size(max = 180) String title,
            @Size(max = 2000) String description,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            String priority,
            List<String> assignedUserIds) {
    }

    public record ApprovalRequest(@NotBlank String userId) {
    }

    public record RejectionRequest(@NotBlank String userId, @NotBlank String reason) {
    }

    public record RecallRequest(@NotBlank String userId, @NotBlank String reason) {
    }

    public record ActivityInputUsageRequest(
            @NotBlank String agriculturalInputId,
            String batchNumber,
            LocalDate expiryDate,
            @NotNull @Positive BigDecimal quantityUsed,
            @NotBlank String quantityUnit,
            @PositiveOrZero BigDecimal waterVolume,
            String waterVolumeUnit,
            @PositiveOrZero BigDecimal concentration,
            String concentrationUnit,
            @PositiveOrZero BigDecimal treatedArea,
            String areaUnit) {
    }

    public record CompleteActivityRequest(
            @NotNull Instant startedAt,
            @NotNull Instant completedAt,
            @NotBlank String executedBy,
            String supervisedBy,
            @PositiveOrZero Integer actualTreeCount,
            @PositiveOrZero BigDecimal actualArea,
            String areaUnit,
            Map<String, Object> weatherSnapshot,
            String applicationMethod,
            String equipment,
            String notes,
            String resultObservation,
            List<String> evidenceFiles,
            List<@Valid ActivityInputUsageRequest> inputUsages) {
    }

    public record CreateAgriculturalInputRequest(
            @NotBlank String code,
            @NotBlank String productName,
            String tradeName,
            String manufacturer,
            String registrationNumber,
            @NotNull InputCategory category,
            @NotNull BiologicalLevel biologicalLevel,
            String formulation,
            String unit,
            List<com.duriancare.cultivation.domain.ActiveIngredientSnapshot> activeIngredients,
            List<String> beneficialOrganisms,
            String recommendedDose,
            String maximumDose,
            @PositiveOrZero Integer preHarvestIntervalDays,
            @PositiveOrZero Integer reEntryIntervalHours,
            List<String> targetPests,
            List<String> applicableCrops,
            List<String> allowedMarketCodes,
            List<String> prohibitedMarketCodes,
            String labelDocumentUrl,
            List<String> certificateDocumentUrls,
            InputStatus status,
            String verifiedBy) {
    }

    public record CreateResidueStandardRequest(
            @NotBlank String marketCode,
            @NotBlank String commodityCode,
            @NotBlank String commodityName,
            @NotBlank String activeIngredientCode,
            @NotBlank String activeIngredientName,
            @NotNull @PositiveOrZero BigDecimal mrlValue,
            @NotBlank String unit,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull SourceType sourceType,
            String sourceReference,
            boolean verified,
            boolean active) {
    }

    public record CreateLabSampleRequest(
            @NotBlank String cultivationSeasonId,
            String harvestBatchId,
            @NotBlank String sampleCode,
            @NotNull SampleType sampleType,
            @NotNull Instant sampledAt,
            @NotBlank String sampledBy,
            String samplingLocation,
            @NotBlank String laboratoryName,
            String laboratoryAccreditation,
            LabSampleStatus status,
            List<String> attachments) {
    }

    public record CreateLabResultRequest(
            @NotBlank String labSampleId,
            @NotBlank String activeIngredientCode,
            @NotBlank String activeIngredientName,
            BigDecimal measuredValue,
            @NotBlank String unit,
            BigDecimal detectionLimit,
            BigDecimal quantificationLimit,
            BigDecimal applicableMrl,
            String applicableMrlSource,
            LabResultStatus resultStatus,
            String verifiedBy) {
    }

    public record CreateHarvestBatchRequest(
            @NotBlank String batchCode,
            @NotBlank String cultivationSeasonId,
            @NotBlank String farmId,
            @NotBlank String plotId,
            @NotNull Instant harvestedAt,
            @NotNull @Positive BigDecimal quantity,
            @NotBlank String quantityUnit,
            @NotBlank String expectedDestinationMarket,
            @NotBlank String createdBy,
            boolean overrideSafeHarvestDate,
            String overrideReason) {
    }

    public record AssessComplianceRequest(
            String harvestBatchId,
            @NotBlank String targetMarketCode) {
    }

    public record CreateExportReleaseRequest(
            @NotBlank String releaseCode,
            @NotBlank String harvestBatchId,
            @NotBlank String targetMarketCode,
            @NotBlank String submittedBy) {
    }

    public record MessageResponse(String message) {
    }

    public record CompleteActivityResponse(
            Object activity,
            Object execution,
            List<?> inputUsages,
            LocalDate earliestSafeHarvestDate) {
    }

    public record CareHistoryResponse(List<?> activities, List<?> executions, List<?> inputUsages) {
    }

    public record TraceabilityResponse(String releaseId, Map<String, Object> snapshot) {
    }

    public static AgriculturalInputSnapshot snapshotOf(com.duriancare.cultivation.domain.AgriculturalInput input) {
        return new AgriculturalInputSnapshot(
                input.code(),
                input.productName(),
                input.tradeName(),
                input.manufacturer(),
                input.category(),
                input.biologicalLevel(),
                input.preHarvestIntervalDays(),
                input.activeIngredients(),
                input.allowedMarketCodes(),
                input.prohibitedMarketCodes());
    }
}
