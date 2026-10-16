package com.duriancare.farm.service;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.RecommendedCareTaskDraft;
import com.duriancare.farm.domain.TreeCarePlan;
import com.duriancare.farm.domain.TreeCarePlanStatus;
import com.duriancare.farm.domain.TreeCareRecommendation;
import com.duriancare.farm.domain.TreeCareRecommendationStatus;
import com.duriancare.farm.domain.TreeCareTask;
import com.duriancare.farm.domain.TreeCareTaskStatus;
import com.duriancare.farm.domain.TreeDiagnosisRecord;
import com.duriancare.farm.dto.AcceptCareRecommendationRequest;
import com.duriancare.farm.dto.CareRecommendationRequest;
import com.duriancare.farm.dto.CreateTreeCarePlanRequest;
import com.duriancare.farm.dto.RecommendedCareTaskRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.TreeCarePlanResponse;
import com.duriancare.farm.dto.TreeCareRecommendationResponse;
import com.duriancare.farm.dto.TreeCareTaskResponse;
import com.duriancare.farm.dto.UpdateCareRecommendationRequest;
import com.duriancare.farm.event.publisher.FarmNotificationEventPublisher;
import com.duriancare.farm.repository.TreeCarePlanRepository;
import com.duriancare.farm.repository.TreeCareRecommendationRepository;
import com.duriancare.farm.repository.TreeCareTaskRepository;
import com.duriancare.farm.repository.TreeDiagnosisRecordRepository;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class TreeCarePlanService {

    private final TreeService treeService;
    private final TreeCarePlanRepository carePlanRepository;
    private final TreeCareRecommendationRepository recommendationRepository;
    private final TreeCareTaskRepository taskRepository;
    private final TreeDiagnosisRecordRepository diagnosisRepository;
    private final AiCareRecommendationClient aiRecommendationClient;
    private final FarmNotificationEventPublisher eventPublisher;
    private final int defaultFollowUpDays;

    @Autowired
    public TreeCarePlanService(
            TreeService treeService,
            TreeCarePlanRepository carePlanRepository,
            TreeCareRecommendationRepository recommendationRepository,
            TreeCareTaskRepository taskRepository,
            TreeDiagnosisRecordRepository diagnosisRepository,
            AiCareRecommendationClient aiRecommendationClient,
            FarmNotificationEventPublisher eventPublisher) {
        this(treeService, carePlanRepository, recommendationRepository, taskRepository,
                diagnosisRepository, aiRecommendationClient, eventPublisher, 7);
    }

    public TreeCarePlanService(
            TreeService treeService,
            TreeCarePlanRepository carePlanRepository,
            TreeCareRecommendationRepository recommendationRepository,
            TreeCareTaskRepository taskRepository,
            TreeDiagnosisRecordRepository diagnosisRepository,
            AiCareRecommendationClient aiRecommendationClient,
            FarmNotificationEventPublisher eventPublisher,
            @Value("${duriancare.tree-care.default-follow-up-days:7}") int defaultFollowUpDays) {
        this.treeService = treeService;
        this.carePlanRepository = carePlanRepository;
        this.recommendationRepository = recommendationRepository;
        this.taskRepository = taskRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.aiRecommendationClient = aiRecommendationClient;
        this.eventPublisher = eventPublisher;
        this.defaultFollowUpDays = Math.max(1, defaultFollowUpDays);
    }

    public TreeCarePlanResponse createCarePlan(
            RequestActor actor, String treeId, CreateTreeCarePlanRequest req) {
        DurianTree tree = treeService.requireActiveTreeForMutation(
                actor, treeId, "Care plan cannot be created after tree removal");
        String diseaseCode = resolveDiseaseCode(tree, req);
        String diagnosisId = trimToNull(req.diagnosisId());
        String source = "MANUAL";
        if (StringUtils.hasText(diagnosisId)) {
            Optional<TreeCarePlan> existing =
                    carePlanRepository.findByDiagnosisIdAndSource(diagnosisId, source);
            if (existing.isPresent()) {
                return TreeCarePlanResponse.from(existing.get());
            }
        }
        Instant now = Instant.now();
        TreeCarePlan plan = new TreeCarePlan(
                null, treeId, tree.farmId(), diagnosisId, diseaseCode,
                req.knowledgeArticleId(), req.treatment(), req.startDate(), req.followUpDate(),
                TreeCarePlanStatus.PLANNED, source, null, null, List.of(), null,
                actor.userId(), now, now);
        TreeCarePlan saved = carePlanRepository.save(plan);
        if (req.followUpDate() != null) {
            publishFollowUpNotification(
                    actor.userId(), tree.treeCode(), treeId, tree.farmId(),
                    tree.farmZoneId(), saved.id(), req.followUpDate());
        }
        return TreeCarePlanResponse.from(saved);
    }

    public List<TreeCarePlanResponse> listCarePlans(RequestActor actor, String treeId) {
        treeService.requireTree(actor, treeId);
        return carePlanRepository.findByTreeIdOrderByCreatedAtDesc(treeId)
                .stream().map(TreeCarePlanResponse::from).toList();
    }

    public TreeCarePlanResponse getCarePlan(RequestActor actor, String planId) {
        TreeCarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new FarmNotFoundException("Care plan not found: " + planId));
        treeService.requireTree(actor, plan.treeId());
        return TreeCarePlanResponse.from(plan);
    }

    public TreeCarePlanResponse updateStatus(
            RequestActor actor, String planId, TreeCarePlanStatus newStatus) {
        TreeCarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new FarmNotFoundException("Care plan not found: " + planId));
        treeService.requireActiveTreeForMutation(
                actor, plan.treeId(), "Care plan status cannot be changed after tree removal");
        if (newStatus == null) {
            throw new FarmInvalidRequestException("Care plan status is required");
        }
        if (plan.status() == newStatus) {
            return TreeCarePlanResponse.from(plan);
        }
        if (!isAllowedTransition(plan.status(), newStatus)) {
            throw new FarmConflictException(
                    "Invalid care plan status transition: " + plan.status() + " -> " + newStatus);
        }
        TreeCarePlan updated = new TreeCarePlan(
                plan.id(), plan.treeId(), plan.farmId(), plan.diagnosisId(), plan.diseaseCode(),
                plan.knowledgeArticleId(), plan.treatment(), plan.startDate(), plan.followUpDate(),
                newStatus, plan.source(), plan.recommendationId(), plan.recommendationSnapshot(),
                plan.taskIds(), plan.acceptedAt(), plan.createdByUserId(), plan.createdAt(), Instant.now());
        return TreeCarePlanResponse.from(carePlanRepository.save(updated));
    }

    public TreeCareRecommendationResponse createRecommendation(
            RequestActor actor, String treeId, CareRecommendationRequest req) {
        DurianTree tree = treeService.requireActiveTreeForMutation(
                actor, treeId, "Care recommendation cannot be created after tree removal");
        TreeDiagnosisRecord diagnosis = resolveDiagnosis(tree, req == null ? null : req.diagnosisId());
        String source = "AI_RECOMMENDED";
        Optional<TreeCareRecommendation> existing =
                recommendationRepository.findByDiagnosisIdAndSource(diagnosis.id(), source);
        if (existing.isPresent()) {
            return TreeCareRecommendationResponse.from(existing.get());
        }

        Instant now = Instant.now();
        Map<String, Object> snapshot = buildRecommendationSnapshot(diagnosis.diseaseCode());
        LocalDate followUpDate = now.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(defaultFollowUpDays);
        List<RecommendedCareTaskDraft> tasks = buildTaskDrafts(snapshot, followUpDate);
        TreeCareRecommendation saved = recommendationRepository.save(new TreeCareRecommendation(
                null, tree.id(), tree.farmId(), tree.farmZoneId(), tree.treeCode(), diagnosis.id(),
                diagnosis.diseaseCode(), source, TreeCareRecommendationStatus.DRAFT, snapshot, tasks,
                followUpDate, null, actor.userId(), now, now));
        return TreeCareRecommendationResponse.from(saved);
    }

    public TreeCareRecommendationResponse getLatestRecommendation(RequestActor actor, String treeId) {
        treeService.requireTree(actor, treeId);
        return recommendationRepository.findTopByTreeIdOrderByCreatedAtDesc(treeId)
                .map(TreeCareRecommendationResponse::from)
                .orElseThrow(() -> new FarmNotFoundException("Care recommendation not found for tree: " + treeId));
    }

    public TreeCareRecommendationResponse updateRecommendation(
            RequestActor actor, String treeId, String recommendationId, UpdateCareRecommendationRequest req) {
        DurianTree tree = treeService.requireActiveTreeForMutation(
                actor, treeId, "Care recommendation cannot be changed after tree removal");
        TreeCareRecommendation current = requireRecommendationForTree(tree, recommendationId);
        if (current.status() == TreeCareRecommendationStatus.ACCEPTED) {
            throw new FarmConflictException("Accepted recommendation cannot be edited");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(current.recommendationSnapshot());
        if (StringUtils.hasText(req.treatment())) {
            snapshot.put("farmerTreatment", req.treatment().trim());
        }
        LocalDate followUpDate = req.followUpDate() == null ? current.followUpDate() : req.followUpDate();
        List<RecommendedCareTaskDraft> tasks = req.tasks() == null || req.tasks().isEmpty()
                ? current.tasks()
                : normalizeTaskRequests(req.tasks(), followUpDate);
        TreeCareRecommendation updated = new TreeCareRecommendation(
                current.id(), current.treeId(), current.farmId(), current.farmZoneId(), current.treeCode(),
                current.diagnosisId(), current.diseaseCode(), current.source(), current.status(),
                snapshot, tasks, followUpDate, current.acceptedCarePlanId(), current.createdByUserId(),
                current.createdAt(), Instant.now());
        return TreeCareRecommendationResponse.from(recommendationRepository.save(updated));
    }

    public TreeCarePlanResponse acceptRecommendation(
            RequestActor actor, String treeId, String recommendationId, AcceptCareRecommendationRequest req) {
        DurianTree tree = treeService.requireActiveTreeForMutation(
                actor, treeId, "Care recommendation cannot be accepted after tree removal");
        TreeCareRecommendation recommendation = requireRecommendationForTree(tree, recommendationId);
        Optional<TreeCarePlan> existingPlan = carePlanRepository.findByRecommendationId(recommendation.id());
        if (existingPlan.isPresent()) {
            return TreeCarePlanResponse.from(existingPlan.get());
        }

        LocalDate startDate = req != null && req.startDate() != null ? req.startDate() : LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate followUpDate = req != null && req.followUpDate() != null ? req.followUpDate() : recommendation.followUpDate();
        List<RecommendedCareTaskDraft> taskDrafts = req != null && req.tasks() != null && !req.tasks().isEmpty()
                ? normalizeTaskRequests(req.tasks(), followUpDate)
                : recommendation.tasks();
        String treatment = req != null && StringUtils.hasText(req.treatment())
                ? req.treatment().trim()
                : treatmentFromSnapshot(recommendation.recommendationSnapshot());
        Instant now = Instant.now();
        TreeCarePlan savedPlan = carePlanRepository.save(new TreeCarePlan(
                null, tree.id(), tree.farmId(), recommendation.diagnosisId(), recommendation.diseaseCode(),
                null, treatment, startDate, followUpDate, TreeCarePlanStatus.PLANNED,
                recommendation.source(), recommendation.id(), recommendation.recommendationSnapshot(), List.of(),
                now, actor.userId(), now, now));
        List<TreeCareTask> savedTasks;
        try {
            savedTasks = createTasksIfMissing(savedPlan, tree, taskDrafts, now);
        } catch (RuntimeException exception) {
            carePlanRepository.deleteById(savedPlan.id());
            throw exception;
        }
        TreeCarePlan planWithTasks = new TreeCarePlan(
                savedPlan.id(), savedPlan.treeId(), savedPlan.farmId(), savedPlan.diagnosisId(),
                savedPlan.diseaseCode(), savedPlan.knowledgeArticleId(), savedPlan.treatment(),
                savedPlan.startDate(), savedPlan.followUpDate(), savedPlan.status(), savedPlan.source(),
                savedPlan.recommendationId(), savedPlan.recommendationSnapshot(),
                savedTasks.stream().map(TreeCareTask::id).toList(), savedPlan.acceptedAt(),
                savedPlan.createdByUserId(), savedPlan.createdAt(), Instant.now());
        TreeCarePlan finalPlan = carePlanRepository.save(planWithTasks);
        TreeCareRecommendation accepted = new TreeCareRecommendation(
                recommendation.id(), recommendation.treeId(), recommendation.farmId(), recommendation.farmZoneId(),
                recommendation.treeCode(), recommendation.diagnosisId(), recommendation.diseaseCode(),
                recommendation.source(), TreeCareRecommendationStatus.ACCEPTED, recommendation.recommendationSnapshot(),
                taskDrafts, followUpDate, finalPlan.id(), recommendation.createdByUserId(),
                recommendation.createdAt(), Instant.now());
        recommendationRepository.save(accepted);
        if (followUpDate != null) {
            publishFollowUpNotification(
                    actor.userId(), tree.treeCode(), tree.id(), tree.farmId(),
                    tree.farmZoneId(), finalPlan.id(), followUpDate);
        }
        return TreeCarePlanResponse.from(finalPlan);
    }

    public List<TreeCareTaskResponse> listCareTasks(RequestActor actor, String planId) {
        TreeCarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new FarmNotFoundException("Care plan not found: " + planId));
        treeService.requireTree(actor, plan.treeId());
        return taskRepository.findByCarePlanIdOrderBySortOrderAsc(plan.id()).stream()
                .map(TreeCareTaskResponse::from)
                .toList();
    }

    private String resolveDiseaseCode(DurianTree tree, CreateTreeCarePlanRequest req) {
        String diagnosisId = trimToNull(req.diagnosisId());
        String requestedDiseaseCode = trimToNull(req.diseaseCode());
        if (!StringUtils.hasText(diagnosisId)) {
            if (!StringUtils.hasText(requestedDiseaseCode)) {
                throw new FarmConflictException("Disease code is required when diagnosisId is not provided");
            }
            return requestedDiseaseCode;
        }

        TreeDiagnosisRecord diagnosis = diagnosisRepository.findById(diagnosisId)
                .orElseThrow(() -> new FarmNotFoundException("Diagnosis not found: " + diagnosisId));
        if (!tree.id().equals(diagnosis.treeId()) || !tree.farmId().equals(diagnosis.farmId())) {
            throw new FarmConflictException("Diagnosis does not belong to this tree");
        }

        String diagnosisDiseaseCode = trimToNull(diagnosis.diseaseCode());
        if (!StringUtils.hasText(diagnosisDiseaseCode)) {
            throw new FarmConflictException("Diagnosis does not contain a disease code");
        }
        if (StringUtils.hasText(requestedDiseaseCode)
                && !normalizeDiseaseCode(requestedDiseaseCode).equals(normalizeDiseaseCode(diagnosisDiseaseCode))) {
            throw new FarmConflictException("Disease code must match the referenced diagnosis");
        }
        return diagnosisDiseaseCode;
    }

    private TreeDiagnosisRecord resolveDiagnosis(DurianTree tree, String requestedDiagnosisId) {
        String diagnosisId = trimToNull(requestedDiagnosisId);
        TreeDiagnosisRecord diagnosis = StringUtils.hasText(diagnosisId)
                ? diagnosisRepository.findById(diagnosisId)
                        .orElseThrow(() -> new FarmNotFoundException("Diagnosis not found: " + diagnosisId))
                : diagnosisRepository.findTopByTreeIdOrderByDiagnosedAtDesc(tree.id())
                        .orElseThrow(() -> new FarmConflictException("Tree has no diagnosis to recommend care from"));
        if (!tree.id().equals(diagnosis.treeId()) || !tree.farmId().equals(diagnosis.farmId())) {
            throw new FarmConflictException("Diagnosis does not belong to this tree");
        }
        if (!StringUtils.hasText(diagnosis.diseaseCode())) {
            throw new FarmConflictException("Diagnosis does not contain a disease code");
        }
        return diagnosis;
    }

    private TreeCareRecommendation requireRecommendationForTree(DurianTree tree, String recommendationId) {
        if (!StringUtils.hasText(recommendationId)) {
            throw new FarmNotFoundException("Care recommendation not found");
        }
        TreeCareRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new FarmNotFoundException("Care recommendation not found: " + recommendationId));
        if (!tree.id().equals(recommendation.treeId()) || !tree.farmId().equals(recommendation.farmId())) {
            throw new FarmConflictException("Care recommendation does not belong to this tree");
        }
        return recommendation;
    }

    private Map<String, Object> buildRecommendationSnapshot(String diseaseCode) {
        Instant generatedAt = Instant.now();
        Optional<Map<String, Object>> ai = aiRecommendationClient.fetchRecommendation(diseaseCode);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("diseaseCode", diseaseCode);
        snapshot.put("generatedAt", generatedAt.toString());
        if (ai.isPresent()) {
            Map<String, Object> data = ai.get();
            snapshot.put("source", "AI_SERVICE_STRUCTURED_KB");
            snapshot.put("summary", firstString(data, "diseaseSummary", "disease_summary"));
            snapshot.put("vietnameseName", firstString(data, "vietnameseName", "vietnamese_name"));
            snapshot.put("englishName", firstString(data, "englishName", "english_name"));
            snapshot.put("careActions", collectActions(data, "prevention", "biologicalTreatments", "organicTreatments", "chemicalTreatments"));
            snapshot.put("monitoringActions", collectMonitoringActions(data));
            snapshot.put("warnings", collectWarnings(data));
            snapshot.put("references", data.getOrDefault("references", List.of()));
            return snapshot;
        }
        snapshot.put("source", "CONSERVATIVE_MONITORING");
        snapshot.put("summary", "Chưa có khuyến nghị chi tiết từ KB cho nhãn này. Ưu tiên theo dõi, cách ly lá nghi ngờ và tái kiểm tra.");
        snapshot.put("careActions", List.of(
                "Theo dõi lá/cành có triệu chứng và ghi nhận thay đổi hằng ngày.",
                "Giữ tán thông thoáng, thu gom lá bệnh rụng nếu có.",
                "Không sử dụng thuốc hoặc liều lượng khi chưa có hướng dẫn từ KB hoặc chuyên gia."));
        snapshot.put("monitoringActions", List.of("Chụp lại ảnh rõ nét trong lần tái kiểm tra.", "Liên hệ chuyên gia nếu triệu chứng lan rộng."));
        snapshot.put("warnings", List.of("Khuyến nghị này không bao gồm thuốc, liều lượng hoặc thời gian cách ly."));
        snapshot.put("references", List.of());
        return snapshot;
    }

    private List<RecommendedCareTaskDraft> buildTaskDrafts(Map<String, Object> snapshot, LocalDate followUpDate) {
        List<String> actions = stringList(snapshot.get("careActions"));
        List<String> monitoring = stringList(snapshot.get("monitoringActions"));
        List<RecommendedCareTaskDraft> tasks = new ArrayList<>();
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        int order = 1;
        for (String action : actions.stream().limit(4).toList()) {
            tasks.add(new RecommendedCareTaskDraft(titleFromAction(action), action, today.plusDays(order - 1L), order++));
        }
        for (String action : monitoring.stream().limit(2).toList()) {
            tasks.add(new RecommendedCareTaskDraft("Theo dõi tiến triển", action, followUpDate, order++));
        }
        if (tasks.isEmpty()) {
            tasks.add(new RecommendedCareTaskDraft("Theo dõi cây sau chẩn đoán",
                    "Kiểm tra lại cây và chụp ảnh rõ nét vào ngày tái kiểm tra.", followUpDate, order));
        }
        return tasks;
    }

    private List<RecommendedCareTaskDraft> normalizeTaskRequests(List<RecommendedCareTaskRequest> requests, LocalDate fallbackDate) {
        return requests.stream()
                .filter(task -> StringUtils.hasText(task.title()) || StringUtils.hasText(task.description()))
                .sorted(Comparator.comparing(task -> task.sortOrder() == null ? Integer.MAX_VALUE : task.sortOrder()))
                .map(task -> new RecommendedCareTaskDraft(
                        StringUtils.hasText(task.title()) ? task.title().trim() : titleFromAction(task.description()),
                        trimToNull(task.description()),
                        task.scheduledDate() == null ? fallbackDate : task.scheduledDate(),
                        task.sortOrder()))
                .toList();
    }

    private List<TreeCareTask> createTasksIfMissing(
            TreeCarePlan plan, DurianTree tree, List<RecommendedCareTaskDraft> drafts, Instant now) {
        if (taskRepository.existsByCarePlanId(plan.id())) {
            return taskRepository.findByCarePlanIdOrderBySortOrderAsc(plan.id());
        }
        List<TreeCareTask> tasks = new ArrayList<>();
        int order = 1;
        for (RecommendedCareTaskDraft draft : drafts) {
            int sortOrder = draft.sortOrder() == null ? order : draft.sortOrder();
            tasks.add(new TreeCareTask(
                    null, plan.id(), plan.diagnosisId(), tree.id(), tree.treeCode(), tree.farmId(), tree.farmZoneId(),
                    draft.scheduledDate(), draft.title(), draft.description(), TreeCareTaskStatus.PLANNED,
                    plan.source(), sortOrder, now, now));
            order++;
        }
        return taskRepository.saveAll(tasks);
    }

    private String treatmentFromSnapshot(Map<String, Object> snapshot) {
        List<String> actions = stringList(snapshot.get("careActions"));
        if (actions.isEmpty()) {
            return String.valueOf(snapshot.getOrDefault("summary", "Theo dõi và tái kiểm tra theo khuyến nghị hệ thống."));
        }
        return String.join("\n", actions);
    }

    private List<String> collectActions(Map<String, Object> data, String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object text = map.get("text");
                        if (text == null) {
                            text = map.get("treatmentText");
                        }
                        if (text == null) {
                            text = map.get("treatment_text");
                        }
                        if (text != null && StringUtils.hasText(text.toString())) {
                            result.add(text.toString().trim());
                        }
                    }
                }
            }
        }
        return result.stream().distinct().limit(8).toList();
    }

    private List<String> collectMonitoringActions(Map<String, Object> data) {
        String name = firstString(data, "englishName", "english_name");
        if (StringUtils.hasText(name)) {
            return List.of("Theo dõi lại dấu hiệu của " + name + " vào ngày tái kiểm tra.");
        }
        return List.of("Theo dõi lại cây vào ngày tái kiểm tra.");
    }

    private List<String> collectWarnings(Map<String, Object> data) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Chỉ sử dụng thuốc, liều lượng và thời gian cách ly khi thông tin đó có trong KB/nhãn hợp lệ.");
        if (data.get("chemicalTreatments") == null) {
            warnings.add("Không có khuyến nghị hóa học có cấu trúc trong KB cho bệnh này.");
        }
        return warnings;
    }

    private boolean isAllowedTransition(TreeCarePlanStatus current, TreeCarePlanStatus next) {
        if (current == TreeCarePlanStatus.PLANNED) {
            return next == TreeCarePlanStatus.IN_PROGRESS || next == TreeCarePlanStatus.CANCELLED;
        }
        if (current == TreeCarePlanStatus.IN_PROGRESS) {
            return next == TreeCarePlanStatus.COMPLETED || next == TreeCarePlanStatus.CANCELLED;
        }
        return false;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && StringUtils.hasText(item.toString()))
                .map(item -> item.toString().trim())
                .toList();
    }

    private String firstString(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private String titleFromAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "Theo dõi cây";
        }
        String cleaned = action.trim();
        return cleaned.length() <= 80 ? cleaned : cleaned.substring(0, 77) + "...";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeDiseaseCode(String value) {
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private void publishFollowUpNotification(
            String receiverId,
            String treeCode,
            String treeId,
            String farmId,
            String zoneId,
            String planId,
            java.time.LocalDate followUpDate) {
        eventPublisher.publish(
                "CARE_FOLLOWUP",
                "CARE_FOLLOWUP:" + planId + ":" + followUpDate,
                receiverId,
                "Lịch tái khám cây " + treeCode,
                "Kế hoạch chăm sóc cây " + treeCode + " đã được tạo. Ngày tái khám: " + followUpDate + ". Hãy kiểm tra lại cây vào ngày này.",
                "CARE",
                Map.of(
                        "treeId", treeId,
                        "treeCode", treeCode,
                        "farmId", farmId,
                        "zoneId", zoneId,
                        "carePlanId", planId,
                        "followUpDate", followUpDate.toString(),
                        "targetUrl", "/dashboard/client/farms/" + farmId
                                + "/zones/" + zoneId + "?treeId=" + treeId));
    }
}
