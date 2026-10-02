package com.duriancare.farm.controller;

import com.duriancare.farm.dto.CreateTreeRequest;
import com.duriancare.farm.dto.GenerateTreesRequest;
import com.duriancare.farm.dto.GenerateTreesResult;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.SaveTreeDiagnosisRequest;
import com.duriancare.farm.dto.TreeDetailResponse;
import com.duriancare.farm.dto.TreeDiagnosisResponse;
import com.duriancare.farm.dto.TreeSummaryResponse;
import com.duriancare.farm.dto.UpdateTreeHealthStatusRequest;
import com.duriancare.farm.dto.UpdateTreeRequest;
import com.duriancare.farm.dto.ZoneDetailResponse;
import com.duriancare.farm.dto.ZoneSafetySummaryResponse;
import com.duriancare.farm.service.TreeDiagnosisService;
import com.duriancare.farm.service.TreeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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
public class TreeController {

    private final TreeService treeService;
    private final TreeDiagnosisService diagnosisService;
    private final RequestActorResolver actorResolver;

    public TreeController(
            TreeService treeService,
            TreeDiagnosisService diagnosisService,
            RequestActorResolver actorResolver) {
        this.treeService = treeService;
        this.diagnosisService = diagnosisService;
        this.actorResolver = actorResolver;
    }

    // ── Zone endpoints (/api/zones/...) ───────────────────────────────────────

    @GetMapping("/api/zones/{zoneId}")
    public ZoneDetailResponse getZone(
            @PathVariable String zoneId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return treeService.getZoneDetail(actorResolver.resolve(userId, email, role), zoneId);
    }

    @GetMapping("/api/zones/{zoneId}/trees")
    public List<TreeSummaryResponse> listTrees(
            @PathVariable String zoneId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return treeService.listTreesForMap(actorResolver.resolve(userId, email, role), zoneId);
    }

    @PostMapping("/api/zones/{zoneId}/trees")
    public ResponseEntity<TreeDetailResponse> createTree(
            @PathVariable String zoneId,
            @RequestBody CreateTreeRequest req,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        TreeDetailResponse created = treeService.createTree(
                actorResolver.resolve(userId, email, role), zoneId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/api/zones/{zoneId}/trees/generate")
    public ResponseEntity<GenerateTreesResult> generateTrees(
            @PathVariable String zoneId,
            @Valid @RequestBody GenerateTreesRequest req,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        GenerateTreesResult result = treeService.generateTrees(
                actorResolver.resolve(userId, email, role), zoneId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/api/zones/{zoneId}/safety-summary")
    public ZoneSafetySummaryResponse getZoneSafety(
            @PathVariable String zoneId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return treeService.getZoneSafety(actorResolver.resolve(userId, email, role), zoneId);
    }

    // ── Tree endpoints (/api/trees/...) ───────────────────────────────────────

    @GetMapping("/api/trees/{treeId}")
    public TreeDetailResponse getTree(
            @PathVariable String treeId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return treeService.getTreeDetail(actorResolver.resolve(userId, email, role), treeId);
    }

    @PutMapping("/api/trees/{treeId}")
    public TreeDetailResponse updateTree(
            @PathVariable String treeId,
            @RequestBody UpdateTreeRequest req,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return treeService.updateTree(actorResolver.resolve(userId, email, role), treeId, req);
    }

    @GetMapping("/api/trees/{treeId}/diagnoses")
    public Page<TreeDiagnosisResponse> listDiagnoses(
            @PathVariable String treeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return diagnosisService.listDiagnoses(
                actorResolver.resolve(userId, email, role), treeId, page, size);
    }

    @PostMapping("/api/trees/{treeId}/diagnoses")
    public ResponseEntity<TreeDiagnosisResponse> saveDiagnosis(
            @PathVariable String treeId,
            @Valid @RequestBody SaveTreeDiagnosisRequest req,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        TreeDiagnosisResponse saved = diagnosisService.saveDiagnosis(
                actorResolver.resolve(userId, email, role), treeId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/api/trees/{treeId}/diagnoses/latest")
    public TreeDiagnosisResponse getLatestDiagnosis(
            @PathVariable String treeId,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return diagnosisService.getLatestDiagnosis(
                actorResolver.resolve(userId, email, role), treeId);
    }

    @PatchMapping("/api/trees/{treeId}/health-status")
    public TreeDetailResponse updateHealthStatus(
            @PathVariable String treeId,
            @Valid @RequestBody UpdateTreeHealthStatusRequest req,
            @RequestHeader("X-Auth-User-Id") String userId,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader("X-Auth-Role") String role) {
        return treeService.transitionHealthStatus(
                actorResolver.resolve(userId, email, role), treeId, req.healthStatus());
    }
}
