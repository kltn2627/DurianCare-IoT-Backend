package com.duriancare.auth.community;

import com.duriancare.auth.domain.UserRole;
import java.util.UUID;

public record CommunityAuthorResponse(
        UUID id,
        String fullName,
        String avatar,
        UserRole role,
        String region) {
}
