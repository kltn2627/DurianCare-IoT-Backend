package com.duriancare.auth.domain;

import java.time.Instant;

public record UserPreference(
        String language,
        String timezone,
        boolean pushNotificationEnabled,
        boolean emailNotificationEnabled,
        boolean diseaseAlertEnabled,
        boolean treatmentReminderEnabled,
        Instant updatedAt) {
}
