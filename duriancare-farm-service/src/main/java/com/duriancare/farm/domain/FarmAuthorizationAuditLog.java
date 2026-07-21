package com.duriancare.farm.domain;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "farm_authorization_audit_logs")
public record FarmAuthorizationAuditLog(
        @Id String id,
        FarmAuthorizationAuditAction action,
        String actorId,
        String actorRole,
        @Indexed String farmId,
        String targetAgronomistId,
        String resourceId,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant timestamp) {
}
