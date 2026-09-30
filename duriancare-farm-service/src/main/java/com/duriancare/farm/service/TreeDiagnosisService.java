package com.duriancare.farm.service;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.domain.TreeHealthStatus;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.SaveTreeDiagnosisRequest;
import com.duriancare.farm.dto.TreeDiagnosisResponse;
import com.duriancare.farm.repository.DurianTreeRepository;
import com.duriancare.farm.repository.TreeDiagnosisRecordRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class TreeDiagnosisService {

    private final DurianTreeRepository treeRepository;
    private final TreeDiagnosisRecordRepository diagnosisRepository;
    private final TreeService treeService;

    public TreeDiagnosisService(
            DurianTreeRepository treeRepository,
            TreeDiagnosisRecordRepository diagnosisRepository,
            TreeService treeService) {
        this.treeRepository = treeRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.treeService = treeService;
    }

    public TreeDiagnosisResponse saveDiagnosis(
            RequestActor actor, String treeId, SaveTreeDiagnosisRequest req) {
        DurianTree tree = treeService.requireTree(actor, treeId);
        TreeHealthStatus implied = inferHealthStatus(req.diseaseCode());
        Instant now = Instant.now();
        TreeDiagnosisRecord record = new TreeDiagnosisRecord(
                null, treeId, tree.farmId(), tree.farmZoneId(),
                req.imageUrl(), req.diseaseCode(), req.diseaseName(),
                req.confidence(), req.boundingBox(),
                req.source() != null ? req.source() : "UNKNOWN",
                actor.userId(), implied, now, now);
        TreeDiagnosisRecord saved = diagnosisRepository.save(record);
        treeService.updateTreeHealthStatus(treeId, implied);
        return toResponse(saved, tree.treeCode());
    }

    public Page<TreeDiagnosisResponse> listDiagnoses(
            RequestActor actor, String treeId, int page, int size) {
        DurianTree tree = treeService.requireTree(actor, treeId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "diagnosedAt"));
        return diagnosisRepository
                .findByTreeIdOrderByDiagnosedAtDesc(treeId, pageable)
                .map(r -> toResponse(r, tree.treeCode()));
    }

    public TreeDiagnosisResponse getLatestDiagnosis(RequestActor actor, String treeId) {
        DurianTree tree = treeService.requireTree(actor, treeId);
        return diagnosisRepository
                .findTopByTreeIdOrderByDiagnosedAtDesc(treeId)
                .map(r -> toResponse(r, tree.treeCode()))
                .orElseThrow(() -> new FarmNotFoundException("No diagnosis found for tree: " + treeId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TreeHealthStatus inferHealthStatus(String diseaseCode) {
        if (diseaseCode == null || diseaseCode.isBlank()) return TreeHealthStatus.SUSPECTED;
        String normalized = diseaseCode.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return (normalized.equals("HEALTHY_LEAF") || normalized.equals("HEALTHY"))
                ? TreeHealthStatus.HEALTHY
                : TreeHealthStatus.DISEASED;
    }

    private static TreeDiagnosisResponse toResponse(TreeDiagnosisRecord r, String treeCode) {
        return new TreeDiagnosisResponse(
                r.id(), r.treeId(), treeCode, r.imageUrl(),
                r.diseaseCode(), r.diseaseName(), r.confidence(), r.boundingBox(),
                r.source(),
                r.impliedHealthStatus() != null ? r.impliedHealthStatus().name() : null,
                r.diagnosedAt(), r.createdAt());
    }
}
