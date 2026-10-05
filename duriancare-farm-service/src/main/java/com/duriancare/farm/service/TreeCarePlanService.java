package com.duriancare.farm.service;

import com.duriancare.farm.domain.DurianTree;
import com.duriancare.farm.domain.TreeCarePlan;
import com.duriancare.farm.domain.TreeCarePlanStatus;
import com.duriancare.farm.dto.CreateTreeCarePlanRequest;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.dto.TreeCarePlanResponse;
import com.duriancare.farm.event.publisher.FarmNotificationEventPublisher;
import com.duriancare.farm.repository.TreeCarePlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TreeCarePlanService {

    private final TreeService treeService;
    private final TreeCarePlanRepository carePlanRepository;
    private final FarmNotificationEventPublisher eventPublisher;

    public TreeCarePlanService(
            TreeService treeService,
            TreeCarePlanRepository carePlanRepository,
            FarmNotificationEventPublisher eventPublisher) {
        this.treeService = treeService;
        this.carePlanRepository = carePlanRepository;
        this.eventPublisher = eventPublisher;
    }

    public TreeCarePlanResponse createCarePlan(
            RequestActor actor, String treeId, CreateTreeCarePlanRequest req) {
        DurianTree tree = treeService.requireTree(actor, treeId);
        Instant now = Instant.now();
        TreeCarePlan plan = new TreeCarePlan(
                null, treeId, tree.farmId(), req.diagnosisId(), req.diseaseCode(),
                req.knowledgeArticleId(), req.treatment(), req.startDate(), req.followUpDate(),
                TreeCarePlanStatus.PLANNED, actor.userId(), now, now);
        TreeCarePlan saved = carePlanRepository.save(plan);
        if (req.followUpDate() != null) {
            publishFollowUpNotification(actor.userId(), tree.treeCode(), treeId, saved.id(), req.followUpDate());
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
        treeService.requireTree(actor, plan.treeId());
        TreeCarePlan updated = new TreeCarePlan(
                plan.id(), plan.treeId(), plan.farmId(), plan.diagnosisId(), plan.diseaseCode(),
                plan.knowledgeArticleId(), plan.treatment(), plan.startDate(), plan.followUpDate(),
                newStatus, plan.createdByUserId(), plan.createdAt(), Instant.now());
        return TreeCarePlanResponse.from(carePlanRepository.save(updated));
    }

    private void publishFollowUpNotification(
            String receiverId,
            String treeCode,
            String treeId,
            String planId,
            java.time.LocalDate followUpDate) {
        eventPublisher.publish(
                "CARE_FOLLOWUP",
                receiverId,
                "Lịch tái khám cây " + treeCode,
                "Kế hoạch chăm sóc cây " + treeCode + " đã được tạo. Ngày tái khám: " + followUpDate + ". Hãy kiểm tra lại cây vào ngày này.",
                "CARE",
                Map.of("treeId", treeId, "carePlanId", planId, "followUpDate", followUpDate.toString()));
    }
}
