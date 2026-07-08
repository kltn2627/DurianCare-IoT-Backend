package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserGender;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String email,
        UserRole role,
        UserStatus accountStatus,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        UserGender gender,
        String address,
        String provinceCity,
        String bio,
        String avatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
