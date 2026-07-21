package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.CultivationActivity;
import com.duriancare.cultivation.domain.ExportReleaseStatus;
import com.duriancare.cultivation.domain.InputStatus;
import com.duriancare.cultivation.domain.ResidueStandard;
import com.duriancare.cultivation.service.CultivationCalendarService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CultivationCalendarController {

    private final CultivationCalendarService service;

    public CultivationCalendarController(CultivationCalendarService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/cultivation-plans")
    ResponseEntity<?> createPlan(@Valid @RequestBody CultivationCalendarDtos.CreatePlanRequest request) {
        var plan = service.createPlan(request);
        return ResponseEntity.created(URI.create("/api/v1/cultivation-plans/" + plan.id())).body(plan);
    }

    @GetMapping("/api/v1/cultivation-plans/{id}")
    Object getPlan(@PathVariable String id) {
        return service.getPlan(id);
    }

    @GetMapping("/api/v1/cultivation-plans")
    Object listPlans(
            @RequestParam(required = false) String farmId,
            @RequestParam(required = false) String plotId,
            @RequestParam(required = false) String cultivationSeasonId) {
        return service.listPlans(farmId, plotId, cultivationSeasonId);
    }

    @GetMapping("/api/v1/cultivation-plans/{id}/calendar")
    List<CultivationActivity> getPlanCalendar(@PathVariable String id) {
        return service.getPlanCalendar(id);
    }

    @PostMapping("/api/v1/cultivation-activities")
    ResponseEntity<?> createActivity(@Valid @RequestBody CultivationCalendarDtos.CreateActivityRequest request) {
        var activity = service.createActivity(request);
        return ResponseEntity.created(URI.create("/api/v1/cultivation-activities/" + activity.id())).body(activity);
    }

    @GetMapping("/api/v1/cultivation-activities")
    List<CultivationActivity> listActivities(
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestParam(required = false) ActivityType activityType,
            @RequestParam(required = false) ActivityStatus status) {
        return service.listActivities(cultivationSeasonId, activityType, status);
    }

    @GetMapping("/api/v1/cultivation-activities/{id}")
    CultivationActivity getActivity(@PathVariable String id) {
        return service.getActivity(id);
    }

    @PutMapping("/api/v1/cultivation-activities/{id}")
    CultivationActivity updateActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.UpdateActivityRequest request) {
        return service.updateActivity(id, request);
    }

    @PatchMapping("/api/v1/cultivation-activities/{id}")
    CultivationActivity patchActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.UpdateActivityRequest request) {
        return service.updateActivity(id, request);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/approve")
    CultivationActivity approveActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request) {
        return service.approveActivity(id, request.userId());
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/reject")
    CultivationActivity rejectActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.RejectionRequest request) {
        return service.rejectActivity(id, request.userId(), request.reason());
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/start")
    CultivationActivity startActivity(@PathVariable String id) {
        return service.startActivity(id);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/complete")
    CultivationCalendarDtos.CompleteActivityResponse completeActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.CompleteActivityRequest request) {
        return service.completeActivity(id, request);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/skip")
    CultivationActivity skipActivity(@PathVariable String id) {
        return service.skipActivity(id);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/cancel")
    CultivationActivity cancelActivity(@PathVariable String id) {
        return service.cancelActivity(id);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}/care-history")
    CultivationCalendarDtos.CareHistoryResponse careHistory(@PathVariable String id) {
        return service.careHistory(id);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}/chemical-history")
    List<CultivationActivity> chemicalHistory(@PathVariable String id) {
        return service.chemicalHistory(id);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}/safe-harvest-date")
    LocalDate safeHarvestDate(@PathVariable String id) {
        return service.safeHarvestDate(id).orElse(null);
    }

    @PostMapping("/api/v1/cultivation-seasons/{id}/compliance-assessments")
    Object assessSeasonCompliance(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.AssessComplianceRequest request) {
        return service.assessCompliance(id, request);
    }

    @PostMapping("/api/v1/agricultural-inputs")
    ResponseEntity<?> createAgriculturalInput(@Valid @RequestBody CultivationCalendarDtos.CreateAgriculturalInputRequest request) {
        var input = service.createAgriculturalInput(request);
        return ResponseEntity.created(URI.create("/api/v1/agricultural-inputs/" + input.id())).body(input);
    }

    @GetMapping("/api/v1/agricultural-inputs")
    Object listAgriculturalInputs(
            @RequestParam(required = false) BiologicalLevel biologicalLevel,
            @RequestParam(required = false) InputStatus status) {
        return service.listAgriculturalInputs(biologicalLevel, status);
    }

    @GetMapping("/api/v1/agricultural-inputs/{id}")
    Object getAgriculturalInput(@PathVariable String id) {
        return service.getAgriculturalInput(id);
    }

    @PostMapping("/api/v1/residue-standards")
    ResponseEntity<?> createResidueStandard(@Valid @RequestBody CultivationCalendarDtos.CreateResidueStandardRequest request) {
        var standard = service.createResidueStandard(request);
        return ResponseEntity.created(URI.create("/api/v1/residue-standards/" + standard.id())).body(standard);
    }

    @GetMapping("/api/v1/residue-standards")
    List<ResidueStandard> listResidueStandards(
            @RequestParam(required = false) String marketCode,
            @RequestParam(required = false) String commodityCode,
            @RequestParam(required = false) String activeIngredientCode) {
        return service.listResidueStandards(marketCode, commodityCode, activeIngredientCode);
    }

    @PostMapping("/api/v1/residue-standards/import")
    List<ResidueStandard> importResidueStandards(
            @Valid @RequestBody List<CultivationCalendarDtos.CreateResidueStandardRequest> requests) {
        return service.importResidueStandards(requests);
    }

    @PostMapping("/api/v1/lab-samples")
    ResponseEntity<?> createLabSample(@Valid @RequestBody CultivationCalendarDtos.CreateLabSampleRequest request) {
        var sample = service.createLabSample(request);
        return ResponseEntity.created(URI.create("/api/v1/lab-samples/" + sample.id())).body(sample);
    }

    @GetMapping("/api/v1/lab-samples")
    Object listLabSamples(
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestParam(required = false) String harvestBatchId) {
        return service.listLabSamples(cultivationSeasonId, harvestBatchId);
    }

    @GetMapping("/api/v1/lab-samples/{id}")
    Object getLabSample(@PathVariable String id) {
        return service.getLabSample(id);
    }

    @PostMapping("/api/v1/lab-results")
    ResponseEntity<?> createLabResult(@Valid @RequestBody CultivationCalendarDtos.CreateLabResultRequest request) {
        var result = service.createLabResult(request);
        return ResponseEntity.created(URI.create("/api/v1/lab-results/" + result.id())).body(result);
    }

    @PostMapping("/api/v1/harvest-batches")
    ResponseEntity<?> createHarvestBatch(@Valid @RequestBody CultivationCalendarDtos.CreateHarvestBatchRequest request) {
        var batch = service.createHarvestBatch(request);
        return ResponseEntity.created(URI.create("/api/v1/harvest-batches/" + batch.id())).body(batch);
    }

    @GetMapping("/api/v1/harvest-batches/{id}")
    Object getHarvestBatch(@PathVariable String id) {
        return service.getHarvestBatch(id);
    }

    @GetMapping("/api/v1/harvest-batches")
    Object listHarvestBatches(
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestParam(required = false) String farmId,
            @RequestParam(required = false) String plotId) {
        return service.listHarvestBatches(cultivationSeasonId, farmId, plotId);
    }

    @PostMapping("/api/v1/export-releases/assess")
    Object assessExportRelease(@Valid @RequestBody CultivationCalendarDtos.AssessComplianceRequest request) {
        return service.assessExportRelease(request);
    }

    @PostMapping("/api/v1/export-releases")
    ResponseEntity<?> createExportRelease(@Valid @RequestBody CultivationCalendarDtos.CreateExportReleaseRequest request) {
        var release = service.createExportRelease(request);
        return ResponseEntity.created(URI.create("/api/v1/export-releases/" + release.id())).body(release);
    }

    @GetMapping("/api/v1/export-releases")
    Object listExportReleases(
            @RequestParam(required = false) String harvestBatchId,
            @RequestParam(required = false) String targetMarketCode,
            @RequestParam(required = false) ExportReleaseStatus status) {
        return service.listExportReleases(harvestBatchId, targetMarketCode, status);
    }

    @GetMapping("/api/v1/export-releases/{id}")
    Object getExportRelease(@PathVariable String id) {
        return service.getExportRelease(id);
    }

    @PostMapping("/api/v1/export-releases/{id}/submit")
    Object submitExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request) {
        return service.submitExportRelease(id, request.userId());
    }

    @PostMapping("/api/v1/export-releases/{id}/approve")
    Object approveExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request) {
        return service.approveExportRelease(id, request.userId());
    }

    @PostMapping("/api/v1/export-releases/{id}/release")
    Object releaseExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request) {
        return service.releaseExportRelease(id, request.userId());
    }

    @PostMapping("/api/v1/export-releases/{id}/recall")
    Object recallExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.RecallRequest request) {
        return service.recallExportRelease(id, request.userId(), request.reason());
    }

    @GetMapping("/api/v1/export-releases/{id}/traceability")
    CultivationCalendarDtos.TraceabilityResponse traceability(@PathVariable String id) {
        return service.traceability(id);
    }

    @GetMapping("/api/v1/audit-logs")
    Object auditLogs(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId) {
        return service.auditLogs(resourceType, resourceId);
    }
}
