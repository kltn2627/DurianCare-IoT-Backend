package com.duriancare.auth.domain;

import java.time.Instant;

public record UserProfile(
        String fullName,
        String phoneNumber,
        String avatarUrl,
        String address,
        String province,
        String agriculturalLicenseNumber,
        String biography,
        Instant createdAt,
        Instant updatedAt) {
}
