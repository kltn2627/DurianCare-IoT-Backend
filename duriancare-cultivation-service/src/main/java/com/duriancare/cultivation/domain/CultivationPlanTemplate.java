package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cultivation_plan_templates")
public record CultivationPlanTemplate(
        @Id String id,
        @Indexed(unique = true) String code,
        String name,
        String description,
        String durianVarietyId,
        String growthStage,
        List<CultivationPlanTemplateActivity> activities,
        @Version Long version,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public CultivationPlanTemplate {
        activities = activities == null ? List.of() : List.copyOf(activities);
    }
}
