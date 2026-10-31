package com.duriancare.farm.domain;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tree_care_tasks")
@CompoundIndex(name = "care_plan_task_order_idx", def = "{'carePlanId': 1, 'sortOrder': 1}", unique = true)
public record TreeCareTask(
        @Id String id,
        @Indexed String carePlanId,
        @Indexed String diagnosisId,
        @Indexed String treeId,
        String treeCode,
        String farmId,
        String farmZoneId,
        LocalDate scheduledDate,
        String title,
        String description,
        TreeCareTaskStatus status,
        String source,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt) {
}
