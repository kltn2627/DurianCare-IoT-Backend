package com.duriancare.farm.service;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.domain.TreeHealthStatus;
import com.duriancare.farm.domain.TreeStatus;
import com.duriancare.farm.dto.BulkUpdateTreeHealthStatusResponse;
import com.duriancare.farm.dto.CreateTreeRequest;
import com.duriancare.farm.dto.GenerateTreesRequest;
import com.duriancare.farm.dto.GenerateTreesResult;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.TreeDetailResponse;
import com.duriancare.farm.dto.TreeSummaryResponse;
import com.duriancare.farm.dto.UpdateTreeRequest;
import com.duriancare.farm.dto.ZoneDetailResponse;
import com.duriancare.farm.dto.ZoneSafetySummaryResponse;
import com.duriancare.farm.repository.DurianTreeRepository;
import com.duriancare.farm.repository.FarmRepository;
import com.duriancare.farm.repository.TreeDiagnosisRecordRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TreeService {

    private final FarmRepository farmRepository;
    private final DurianTreeRepository treeRepository;
    private final TreeDiagnosisRecordRepository diagnosisRepository;

    public TreeService(
            FarmRepository farmRepository,
            DurianTreeRepository treeRepository,
            TreeDiagnosisRecordRepository diagnosisRepository) {
        this.farmRepository = farmRepository;
        this.treeRepository = treeRepository;
        this.diagnosisRepository = diagnosisRepository;
    }

    public ZoneDetailResponse getZoneDetail(RequestActor actor, String zoneId) {
        Farm farm = requireFarmByZone(actor, zoneId);
        FarmZone zone = findZone(farm, zoneId);
        long total = treeRepository.countByFarmZoneId(zoneId);
        long active = treeRepository.countByFarmZoneIdAndStatus(zoneId, TreeStatus.ACTIVE);
        return new ZoneDetailResponse(
                zone.id(), farm.id(), zone.name(), zone.code(), zone.areaSquareMeters(),
                zone.boundaryGeoJson(), zone.description(),
                zone.status() != null ? zone.status().name() : null,
                total, active, zone.createdAt(), zone.updatedAt());
    }

    public List<TreeSummaryResponse> listTreesForMap(RequestActor actor, String zoneId) {
        requireFarmByZone(actor, zoneId);
        List<DurianTree> trees = treeRepository.findByFarmZoneIdAndStatus(zoneId, TreeStatus.ACTIVE);
        return trees.stream().map(tree -> {
            Optional<TreeDiagnosisRecord> latest =
                    diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(tree.id());
            long count = diagnosisRepository.countByTreeId(tree.id());
            return new TreeSummaryResponse(
                    tree.id(), tree.treeCode(), tree.nickname(), tree.variety(),
                    tree.positionX(), tree.positionY(),
                    tree.healthStatus() != null ? tree.healthStatus().name() : null,
                    tree.status() != null ? tree.status().name() : null,
                    latest.map(TreeDiagnosisRecord::diagnosedAt).orElse(null),
                    latest.map(TreeDiagnosisRecord::diseaseCode).orElse(null),
                    count);
        }).toList();
    }

    public TreeDetailResponse getTreeDetail(RequestActor actor, String treeId) {
        DurianTree tree = requireTree(actor, treeId);
        Optional<TreeDiagnosisRecord> latest =
                diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(treeId);
        long count = diagnosisRepository.countByTreeId(treeId);
        return toDetailResponse(tree, latest, count);
    }

    public TreeDetailResponse createTree(RequestActor actor, String zoneId, CreateTreeRequest req) {
        Farm farm = requireFarmByZone(actor, zoneId);
        String treeCode = resolveTreeCode(req.treeCode(), zoneId);
        if (treeRepository.existsByFarmZoneIdAndTreeCode(zoneId, treeCode)) {
            throw new FarmConflictException("Tree code already exists in this zone: " + treeCode);
        }
        Instant now = Instant.now();
        DurianTree tree = new DurianTree(
                null, farm.id(), zoneId, req.speciesId(), treeCode,
                req.nickname(), req.variety(), req.plantedDate(),
                req.latitude(), req.longitude(), req.positionX(), req.positionY(),
                TreeHealthStatus.HEALTHY, TreeStatus.ACTIVE, req.notes(), now, now);
        DurianTree saved = treeRepository.save(tree);
        return toDetailResponse(saved, Optional.empty(), 0);
    }

    public TreeDetailResponse updateTree(RequestActor actor, String treeId, UpdateTreeRequest req) {
        DurianTree existing = requireTree(actor, treeId);
        Instant now = Instant.now();
        DurianTree updated = new DurianTree(
                existing.id(), existing.farmId(), existing.farmZoneId(), req.speciesId() != null ? req.speciesId() : existing.speciesId(),
                existing.treeCode(), req.nickname(), req.variety(),
                req.plantedDate() != null ? req.plantedDate() : existing.plantedDate(),
                req.latitude() != null ? req.latitude() : existing.latitude(),
                req.longitude() != null ? req.longitude() : existing.longitude(),
                req.positionX() != null ? req.positionX() : existing.positionX(),
                req.positionY() != null ? req.positionY() : existing.positionY(),
                existing.healthStatus(), existing.status(), req.notes(), existing.createdAt(), now);
        DurianTree saved = treeRepository.save(updated);
        Optional<TreeDiagnosisRecord> latest =
                diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(treeId);
        long count = diagnosisRepository.countByTreeId(treeId);
        return toDetailResponse(saved, latest, count);
    }

    public TreeDetailResponse removeTree(RequestActor actor, String treeId) {
        DurianTree existing = requireTree(actor, treeId);
        if (existing.status() == TreeStatus.REMOVED) {
            Optional<TreeDiagnosisRecord> latest =
                    diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(treeId);
            long count = diagnosisRepository.countByTreeId(treeId);
            return toDetailResponse(existing, latest, count);
        }
        Instant now = Instant.now();
        DurianTree removed = new DurianTree(
                existing.id(), existing.farmId(), existing.farmZoneId(), existing.speciesId(),
                existing.treeCode(), existing.nickname(), existing.variety(), existing.plantedDate(),
                existing.latitude(), existing.longitude(), existing.positionX(), existing.positionY(),
                existing.healthStatus(), TreeStatus.REMOVED, existing.notes(), existing.createdAt(), now);
        DurianTree saved = treeRepository.save(removed);
        Optional<TreeDiagnosisRecord> latest =
                diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(treeId);
        long count = diagnosisRepository.countByTreeId(treeId);
        return toDetailResponse(saved, latest, count);
    }

    public GenerateTreesResult generateTrees(
            RequestActor actor,
            String zoneId,
            GenerateTreesRequest req) {
        Farm farm = requireFarmByZone(actor, zoneId);
        Instant now = Instant.now();
        int generated = 0;
        int skipped = 0;
        String prefix = "H";
        for (int row = 1; row <= req.rows(); row++) {
            for (int col = 1; col <= req.treesPerRow(); col++) {
                String treeCode = String.format("H%02d-C%02d", row, col);
                if (treeRepository.existsByFarmZoneIdAndTreeCode(zoneId, treeCode)) {
                    skipped++;
                    continue;
                }
                // Normalize position to [0,1] with a small margin so trees don't clip at edges
                double margin = 0.05;
                double posX = req.treesPerRow() == 1 ? 0.5
                        : margin + (col - 1) * (1.0 - 2 * margin) / (req.treesPerRow() - 1);
                double posY = req.rows() == 1 ? 0.5
                        : margin + (row - 1) * (1.0 - 2 * margin) / (req.rows() - 1);
                DurianTree tree = new DurianTree(
                        null, farm.id(), zoneId, null, treeCode,
                        null, req.variety(), req.plantedDate(),
                        null, null,
                        (double) Math.round(posX * 10000) / 10000,
                        (double) Math.round(posY * 10000) / 10000,
                        TreeHealthStatus.HEALTHY, TreeStatus.ACTIVE, req.notes(), now, now);
                treeRepository.save(tree);
                generated++;
            }
        }
        return new GenerateTreesResult(zoneId, generated, skipped, prefix);
    }

    public TreeDetailResponse transitionHealthStatus(
            RequestActor actor, String treeId, TreeHealthStatus newStatus) {
        // Business rule (2026-10-22): the owning farmer may set any TreeHealthStatus on an ACTIVE tree.
        // AI diagnosis and recovery evaluation are advisory only; no diagnosis record is created here.
        if (newStatus == null) {
            throw new FarmInvalidRequestException("healthStatus is required");
        }
        requireActiveTreeForMutation(actor, treeId, "Tree health status cannot be changed after removal");
        updateTreeHealthStatus(treeId, newStatus);
        return getTreeDetail(actor, treeId);
    }

    /**
     * Applies {@link #transitionHealthStatus} to each tree independently (ownership and ACTIVE
     * status are checked per tree). Returns what was actually saved;
     * a failed tree never blocks or rolls back the others.
     */
    public BulkUpdateTreeHealthStatusResponse bulkTransitionHealthStatus(
            RequestActor actor, List<String> treeIds, TreeHealthStatus newStatus) {
        if (newStatus == null) {
            throw new FarmInvalidRequestException("healthStatus is required");
        }
        List<String> uniqueIds = new ArrayList<>(new LinkedHashSet<>(treeIds));
        List<BulkUpdateTreeHealthStatusResponse.Result> results = new ArrayList<>();
        for (String treeId : uniqueIds) {
            results.add(transitionOne(actor, treeId, newStatus));
        }
        int updated = (int) results.stream().filter(BulkUpdateTreeHealthStatusResponse.Result::updated).count();
        return new BulkUpdateTreeHealthStatusResponse(
                newStatus.name(), uniqueIds.size(), updated, uniqueIds.size() - updated, results);
    }

    private BulkUpdateTreeHealthStatusResponse.Result transitionOne(
            RequestActor actor, String treeId, TreeHealthStatus newStatus) {
        try {
            TreeDetailResponse detail = transitionHealthStatus(actor, treeId, newStatus);
            return new BulkUpdateTreeHealthStatusResponse.Result(
                    treeId, detail.treeCode(), true, detail.healthStatus(), null, null);
        } catch (FarmNotFoundException ex) {
            return failed(treeId, "NOT_FOUND", "Không tìm thấy cây.");
        } catch (FarmAccessDeniedException ex) {
            return failed(treeId, "FORBIDDEN", "Bạn không có quyền cập nhật cây này.");
        } catch (FarmConflictException ex) {
            return failed(treeId, "NOT_ACTIVE", "Cây không còn hoạt động nên không thể đổi trạng thái.");
        } catch (FarmInvalidRequestException ex) {
            return failed(treeId, "INVALID", ex.getMessage());
        }
    }

    private static BulkUpdateTreeHealthStatusResponse.Result failed(String treeId, String code, String message) {
        return new BulkUpdateTreeHealthStatusResponse.Result(treeId, null, false, null, code, message);
    }

    public void updateTreeHealthStatus(String treeId, TreeHealthStatus newStatus) {
        treeRepository.findById(treeId).ifPresent(existing -> {
            Instant now = Instant.now();
            DurianTree updated = new DurianTree(
                    existing.id(), existing.farmId(), existing.farmZoneId(), existing.speciesId(),
                    existing.treeCode(), existing.nickname(), existing.variety(), existing.plantedDate(),
                    existing.latitude(), existing.longitude(), existing.positionX(), existing.positionY(),
                    newStatus, existing.status(), existing.notes(), existing.createdAt(), now);
            treeRepository.save(updated);
        });
    }

    public ZoneSafetySummaryResponse getZoneSafety(RequestActor actor, String zoneId) {
        Farm farm = requireFarmByZone(actor, zoneId);
        FarmZone zone = findZone(farm, zoneId);
        List<DurianTree> activeTrees = treeRepository.findByFarmZoneIdAndStatus(zoneId, TreeStatus.ACTIVE);
        long totalTrees = activeTrees.size();

        long safeTrees = activeTrees.stream()
                .filter(t -> t.healthStatus() == TreeHealthStatus.HEALTHY)
                .count();
        long attentionTrees = activeTrees.stream()
                .filter(t -> t.healthStatus() == TreeHealthStatus.DISEASED
                        || t.healthStatus() == TreeHealthStatus.TREATING
                        || t.healthStatus() == TreeHealthStatus.SUSPECTED)
                .count();
        long assessedTrees = activeTrees.stream()
                .filter(t -> diagnosisRepository.countByTreeId(t.id()) > 0)
                .count();
        long notAssessedTrees = totalTrees - assessedTrees;

        Double safetyRate = assessedTrees > 0 ? (safeTrees * 100.0 / assessedTrees) : null;
        String label = assessedTrees > 0
                ? safeTrees + " / " + assessedTrees + " cây đã đánh giá đạt an toàn"
                : "Chưa có cây nào được đánh giá";

        return new ZoneSafetySummaryResponse(
                zoneId, zone.name(), totalTrees, assessedTrees,
                safeTrees, attentionTrees, notAssessedTrees,
                safetyRate != null ? Math.round(safetyRate * 100.0) / 100.0 : null,
                label, Instant.now());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Farm requireFarmByZone(RequestActor actor, String zoneId) {
        Farm farm = farmRepository.findByZoneId(zoneId)
                .orElseThrow(() -> new FarmNotFoundException("Zone not found: " + zoneId));
        if (!farm.ownerUserId().equals(actor.userId())) {
            throw new FarmAccessDeniedException("Access denied to zone: " + zoneId);
        }
        return farm;
    }

    DurianTree requireTree(RequestActor actor, String treeId) {
        DurianTree tree = treeRepository.findById(treeId)
                .orElseThrow(() -> new FarmNotFoundException("Tree not found: " + treeId));
        Farm farm = farmRepository.findById(tree.farmId())
                .orElseThrow(() -> new FarmNotFoundException("Farm not found for tree: " + treeId));
        if (!farm.ownerUserId().equals(actor.userId())) {
            throw new FarmAccessDeniedException("Access denied to tree: " + treeId);
        }
        return tree;
    }

    DurianTree requireActiveTreeForMutation(RequestActor actor, String treeId, String message) {
        DurianTree tree = requireTree(actor, treeId);
        if (tree.status() != null && tree.status() != TreeStatus.ACTIVE) {
            throw new FarmConflictException(message + ": " + tree.treeCode());
        }
        return tree;
    }

    private static FarmZone findZone(Farm farm, String zoneId) {
        return farm.zones().stream()
                .filter(z -> zoneId.equals(z.id()))
                .findFirst()
                .orElseThrow(() -> new FarmNotFoundException("Zone not found: " + zoneId));
    }

    private String resolveTreeCode(String requested, String zoneId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        long count = treeRepository.countByFarmZoneId(zoneId);
        return "DC-T" + String.format("%03d", count + 1);
    }

    private TreeDetailResponse toDetailResponse(
            DurianTree tree,
            Optional<TreeDiagnosisRecord> latest,
            long count) {
        return new TreeDetailResponse(
                tree.id(), tree.farmId(), tree.farmZoneId(), tree.speciesId(),
                tree.treeCode(), tree.nickname(), tree.variety(), tree.plantedDate(),
                tree.latitude(), tree.longitude(), tree.positionX(), tree.positionY(),
                tree.healthStatus() != null ? tree.healthStatus().name() : null,
                tree.status() != null ? tree.status().name() : null,
                tree.notes(), count,
                latest.map(TreeDiagnosisRecord::diagnosedAt).orElse(null),
                latest.map(TreeDiagnosisRecord::diseaseCode).orElse(null),
                latest.map(TreeDiagnosisRecord::confidence).orElse(null),
                tree.createdAt(), tree.updatedAt());
    }
}
