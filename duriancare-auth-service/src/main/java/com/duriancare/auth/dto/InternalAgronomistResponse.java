package com.duriancare.auth.dto;

import java.util.UUID;

public record InternalAgronomistResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        String accountStatus,
        String workplace,
        String specialization,
        Integer yearsExperience,
        String provinceCity,
        String avatarUrl,
        boolean eligible) {
}
