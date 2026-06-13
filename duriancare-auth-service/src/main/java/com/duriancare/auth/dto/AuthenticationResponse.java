package com.duriancare.auth.dto;

import com.duriancare.auth.domain.UserRole;
import java.util.UUID;

public record AuthenticationResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        UUID userId,
        String email,
        UserRole role,
        UserProfileResponse profile) {
}
