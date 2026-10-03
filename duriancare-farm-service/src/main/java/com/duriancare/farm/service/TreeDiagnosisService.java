package com.duriancare.farm.service;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.RecoveryOutcome;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.domain.TreeHealthStatus;
import com.duriancare.farm.dto.RecoveryEvaluationResponse;
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

    public RecoveryEvaluationResponse evaluateRecovery(RequestActor actor, String treeId) {
        treeService.requireTree(actor, treeId);
        PageRequest top2 = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "diagnosedAt"));
        List<TreeDiagnosisRecord> recent = diagnosisRepository
                .findByTreeIdOrderByDiagnosedAtDesc(treeId, top2)
                .getContent();
        if (recent.size() < 2) {
            String code = recent.isEmpty() ? null : recent.get(0).diseaseCode();
            Double conf = recent.isEmpty() ? null : recent.get(0).confidence();
            return new RecoveryEvaluationResponse(treeId, RecoveryOutcome.UNCERTAIN,
                    null, null, code, conf,
                    "Không đủ dữ liệu chẩn đoán để đánh giá (cần ít nhất 2 lần chẩn đoán)",
                    Instant.now());
        }
        TreeDiagnosisRecord current = recent.get(0);
        TreeDiagnosisRecord previous = recent.get(1);
        String currentCode = normalizeCode(current.diseaseCode());
        String previousCode = normalizeCode(previous.diseaseCode());
        if (isHealthy(currentCode)) {
            return new RecoveryEvaluationResponse(treeId, RecoveryOutcome.RECOVERED,
                    previous.diseaseCode(), previous.confidence(),
                    current.diseaseCode(), current.confidence(),
                    "Chẩn đoán hiện tại là khỏe mạnh — cây đã phục hồi", Instant.now());
        }
        if (!currentCode.equals(previousCode)) {
            return new RecoveryEvaluationResponse(treeId, RecoveryOutcome.WORSENED,
                    previous.diseaseCode(), previous.confidence(),
                    current.diseaseCode(), current.confidence(),
                    "Loại bệnh thay đổi từ " + previousCode + " sang " + currentCode, Instant.now());
        }
        double prevConf = previous.confidence() != null ? previous.confidence() : 0.5;
        double currConf = current.confidence() != null ? current.confidence() : 0.5;
        double delta = prevConf - currConf;
        if (delta > 0.15) {
            return new RecoveryEvaluationResponse(treeId, RecoveryOutcome.IMPROVED,
                    previous.diseaseCode(), previous.confidence(),
                    current.diseaseCode(), current.confidence(),
                    String.format("Độ tin cậy giảm %.0f%% — bệnh đang được cải thiện", delta * 100), Instant.now());
        }
        if (delta < -0.15) {
            return new RecoveryEvaluationResponse(treeId, RecoveryOutcome.WORSENED,
                    previous.diseaseCode(), previous.confidence(),
                    current.diseaseCode(), current.confidence(),
                    String.format("Độ tin cậy tăng %.0f%% — bệnh đang diễn tiến nặng hơn", -delta * 100), Instant.now());
        }
        return new RecoveryEvaluationResponse(treeId, RecoveryOutcome.STABLE,
                previous.diseaseCode(), previous.confidence(),
                current.diseaseCode(), current.confidence(),
                "Bệnh không thay đổi đáng kể — cần tiếp tục theo dõi", Instant.now());
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) return "UNKNOWN";
        return code.trim().toUpperCase().replace("-", "_").replace(" ", "_");
    }

    private static boolean isHealthy(String normalizedCode) {
        return normalizedCode.equals("HEALTHY_LEAF")
                || normalizedCode.equals("HEALTHY")
                || normalizedCode.equals("RECOVERED_BY_FARMER");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    static TreeHealthStatus inferHealthStatus(String diseaseCode) {
        if (diseaseCode == null || diseaseCode.isBlank()) return TreeHealthStatus.SUSPECTED;
        String normalized = diseaseCode.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return (normalized.equals("HEALTHY_LEAF")
                || normalized.equals("HEALTHY")
                || normalized.equals("RECOVERED_BY_FARMER"))
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
