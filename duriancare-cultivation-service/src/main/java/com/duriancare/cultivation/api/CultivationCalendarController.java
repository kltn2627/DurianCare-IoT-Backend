package com.duriancare.cultivation.api;

import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.BiologicalLevel;
import com.duriancare.cultivation.domain.CultivationActivity;
import com.duriancare.cultivation.domain.ExportReleaseStatus;
import com.duriancare.cultivation.domain.InputStatus;
import com.duriancare.cultivation.domain.ResidueStandard;
import com.duriancare.cultivation.security.CultivationActor;
import com.duriancare.cultivation.service.CultivationCalendarService;
import com.duriancare.cultivation.service.CultivationSeasonCatalogService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CultivationCalendarController {

    private final CultivationCalendarService service;
    private final CultivationSeasonCatalogService seasonCatalogService;

    public CultivationCalendarController(
            CultivationCalendarService service,
            CultivationSeasonCatalogService seasonCatalogService) {
        this.service = service;
        this.seasonCatalogService = seasonCatalogService;
    }

    @PostMapping("/api/v1/cultivation-plans")
    ResponseEntity<?> createPlan(
            @Valid @RequestBody CultivationCalendarDtos.CreatePlanRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var plan = service.createPlan(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/cultivation-plans/" + plan.id())).body(plan);
    }

    @GetMapping("/api/v1/cultivation-plans/{id}")
    Object getPlan(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getPlan(actor(userId, email, role), id);
    }

    @GetMapping("/api/v1/cultivation-plans")
    Object listPlans(
            @RequestParam(required = false) String farmId,
            @RequestParam(required = false) String plotId,
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listPlans(actor(userId, email, role), farmId, plotId, cultivationSeasonId);
    }

    @GetMapping("/api/v1/cultivation-plans/{id}/calendar")
    List<CultivationActivity> getPlanCalendar(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getPlanCalendar(actor(userId, email, role), id);
    }

    @PostMapping("/api/v1/cultivation-activities")
    ResponseEntity<?> createActivity(
            @Valid @RequestBody CultivationCalendarDtos.CreateActivityRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var activity = service.createActivity(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/cultivation-activities/" + activity.id())).body(activity);
    }

    @GetMapping("/api/v1/cultivation-activities")
    List<CultivationActivity> listActivities(
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestParam(required = false) ActivityType activityType,
            @RequestParam(required = false) ActivityStatus status,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listActivities(actor(userId, email, role), cultivationSeasonId, activityType, status);
    }

    @GetMapping("/api/v1/cultivation-activities/{id}")
    CultivationActivity getActivity(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getActivity(actor(userId, email, role), id);
    }

    @PutMapping("/api/v1/cultivation-activities/{id}")
    CultivationActivity updateActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.UpdateActivityRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.updateActivity(actor(userId, email, role), id, request);
    }

    @PatchMapping("/api/v1/cultivation-activities/{id}")
    CultivationActivity patchActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.UpdateActivityRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.updateActivity(actor(userId, email, role), id, request);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/approve")
    CultivationActivity approveActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.approveActivity(actor(userId, email, role), id, request.userId());
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/reject")
    CultivationActivity rejectActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.RejectionRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.rejectActivity(actor(userId, email, role), id, request.userId(), request.reason());
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/start")
    CultivationActivity startActivity(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.startActivity(actor(userId, email, role), id);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/complete")
    CultivationCalendarDtos.CompleteActivityResponse completeActivity(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.CompleteActivityRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.completeActivity(actor(userId, email, role), id, request);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/skip")
    CultivationActivity skipActivity(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.skipActivity(actor(userId, email, role), id);
    }

    @PostMapping("/api/v1/cultivation-activities/{id}/cancel")
    CultivationActivity cancelActivity(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.cancelActivity(actor(userId, email, role), id);
    }

    @GetMapping("/api/v1/cultivation-seasons")
    Object listCultivationSeasons(
            @RequestParam String farmId,
            @RequestParam(required = false) String plotId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return seasonCatalogService.listSeasons(actor(userId, email, role), farmId, plotId);
    }

    @PostMapping("/api/v1/cultivation-seasons")
    ResponseEntity<?> createCultivationSeason(
            @Valid @RequestBody CultivationCalendarDtos.CreateCultivationSeasonRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var season = seasonCatalogService.createSeason(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/cultivation-seasons/" + season.id())).body(season);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}")
    Object getCultivationSeason(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return seasonCatalogService.getSeason(actor(userId, email, role), id);
    }

    @PatchMapping("/api/v1/cultivation-seasons/{id}")
    Object updateCultivationSeason(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.UpdateCultivationSeasonRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return seasonCatalogService.updateSeason(actor(userId, email, role), id, request);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}/care-history")
    CultivationCalendarDtos.CareHistoryResponse careHistory(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.careHistory(actor(userId, email, role), id);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}/chemical-history")
    List<CultivationActivity> chemicalHistory(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.chemicalHistory(actor(userId, email, role), id);
    }

    @GetMapping("/api/v1/cultivation-seasons/{id}/safe-harvest-date")
    LocalDate safeHarvestDate(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.safeHarvestDate(actor(userId, email, role), id).orElse(null);
    }

    @PostMapping("/api/v1/cultivation-seasons/{id}/compliance-assessments")
    Object assessSeasonCompliance(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.AssessComplianceRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.assessCompliance(actor(userId, email, role), id, request);
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
    ResponseEntity<?> createLabSample(
            @Valid @RequestBody CultivationCalendarDtos.CreateLabSampleRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var sample = service.createLabSample(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/lab-samples/" + sample.id())).body(sample);
    }

    @GetMapping("/api/v1/lab-samples")
    Object listLabSamples(
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestParam(required = false) String harvestBatchId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listLabSamples(actor(userId, email, role), cultivationSeasonId, harvestBatchId);
    }

    @GetMapping("/api/v1/lab-samples/{id}")
    Object getLabSample(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getLabSample(actor(userId, email, role), id);
    }

    @PostMapping("/api/v1/lab-results")
    ResponseEntity<?> createLabResult(
            @Valid @RequestBody CultivationCalendarDtos.CreateLabResultRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var result = service.createLabResult(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/lab-results/" + result.id())).body(result);
    }

    @PostMapping("/api/v1/harvest-batches")
    ResponseEntity<?> createHarvestBatch(
            @Valid @RequestBody CultivationCalendarDtos.CreateHarvestBatchRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var batch = service.createHarvestBatch(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/harvest-batches/" + batch.id())).body(batch);
    }

    @GetMapping("/api/v1/harvest-batches/{id}")
    Object getHarvestBatch(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getHarvestBatch(actor(userId, email, role), id);
    }

    @GetMapping("/api/v1/harvest-batches")
    Object listHarvestBatches(
            @RequestParam(required = false) String cultivationSeasonId,
            @RequestParam(required = false) String farmId,
            @RequestParam(required = false) String plotId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listHarvestBatches(actor(userId, email, role), cultivationSeasonId, farmId, plotId);
    }

    @PostMapping("/api/v1/export-releases/assess")
    Object assessExportRelease(
            @Valid @RequestBody CultivationCalendarDtos.AssessComplianceRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.assessExportRelease(actor(userId, email, role), request);
    }

    @PostMapping("/api/v1/export-releases")
    ResponseEntity<?> createExportRelease(
            @Valid @RequestBody CultivationCalendarDtos.CreateExportReleaseRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        var release = service.createExportRelease(actor(userId, email, role), request);
        return ResponseEntity.created(URI.create("/api/v1/export-releases/" + release.id())).body(release);
    }

    @GetMapping("/api/v1/export-releases")
    Object listExportReleases(
            @RequestParam(required = false) String harvestBatchId,
            @RequestParam(required = false) String targetMarketCode,
            @RequestParam(required = false) ExportReleaseStatus status,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.listExportReleases(actor(userId, email, role), harvestBatchId, targetMarketCode, status);
    }

    @GetMapping("/api/v1/export-releases/{id}")
    Object getExportRelease(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.getExportRelease(actor(userId, email, role), id);
    }

    @PostMapping("/api/v1/export-releases/{id}/submit")
    Object submitExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.submitExportRelease(actor(userId, email, role), id, request.userId());
    }

    @PostMapping("/api/v1/export-releases/{id}/approve")
    Object approveExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.approveExportRelease(actor(userId, email, role), id, request.userId());
    }

    @PostMapping("/api/v1/export-releases/{id}/release")
    Object releaseExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.ApprovalRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.releaseExportRelease(actor(userId, email, role), id, request.userId());
    }

    @PostMapping("/api/v1/export-releases/{id}/recall")
    Object recallExportRelease(
            @PathVariable String id,
            @Valid @RequestBody CultivationCalendarDtos.RecallRequest request,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.recallExportRelease(actor(userId, email, role), id, request.userId(), request.reason());
    }

    @GetMapping("/api/v1/export-releases/{id}/traceability")
    CultivationCalendarDtos.TraceabilityResponse traceability(
            @PathVariable String id,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return service.traceability(actor(userId, email, role), id);
    }

    @GetMapping("/api/v1/audit-logs")
    Object auditLogs(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId) {
        return service.auditLogs(resourceType, resourceId);
    }

    private CultivationActor actor(String userId, String email, String role) {
        return new CultivationActor(userId == null ? "" : userId.trim(), email == null ? "" : email.trim(), role == null ? "" : role.trim());
    }
}
