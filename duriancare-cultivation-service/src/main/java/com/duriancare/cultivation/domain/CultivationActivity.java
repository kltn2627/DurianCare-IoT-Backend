package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cultivation_activities")
@CompoundIndex(name = "activity_plan_schedule_idx", def = "{'cultivationPlanId': 1, 'scheduledStartAt': 1}")
@CompoundIndex(name = "activity_plot_status_idx", def = "{'plotId': 1, 'status': 1}")
public record CultivationActivity(
        @Id String id,
        @Indexed String cultivationPlanId,
        @Indexed String cultivationSeasonId,
        @Indexed String farmId,
        @Indexed String plotId,
        List<String> treeIds,
        @Indexed ActivityType activityType,
        String title,
        String description,
        @Indexed Instant scheduledStartAt,
        Instant scheduledEndAt,
        String recurrenceRule,
        String priority,
        @Indexed ActivityStatus status,
        List<String> assignedUserIds,
        boolean approvalRequired,
        String approvedBy,
        Instant approvedAt,
        String rejectionReason,
        @Version Long version,
        Instant createdAt,
        Instant updatedAt) {
}
