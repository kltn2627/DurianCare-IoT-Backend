package com.duriancare.cultivation.domain;

public record CultivationPlanTemplateActivity(
        ActivityType activityType,
        String title,
        String description,
        Integer dayOffsetFromStart,
        String defaultPriority,
        boolean approvalRequired) {
}
