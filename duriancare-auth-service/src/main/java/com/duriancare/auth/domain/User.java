package com.duriancare.auth.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@CompoundIndex(name = "role_status_idx", def = "{'role': 1, 'status': 1}")
public record User(
        @Id String id,
        @Indexed(unique = true) String email,
        String passwordHash,
        Role role,
        UserStatus status,
        boolean emailVerified,
        Instant lastLoginAt,
        UserProfile profile,
        UserPreference preference,
        Instant createdAt,
        Instant updatedAt) {
}
