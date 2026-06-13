package com.duriancare.auth.security;

import com.duriancare.auth.domain.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, UserRole role, String tokenId) {
}
