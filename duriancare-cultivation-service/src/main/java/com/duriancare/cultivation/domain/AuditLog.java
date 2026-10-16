package com.duriancare.cultivation.domain;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cultivation_audit_logs")
public record AuditLog(
        @Id String id,
        @Indexed AuditAction action,
        @Indexed String resourceType,
        @Indexed String resourceId,
        String actorId,
        String reason,
        Map<String, Object> details,
        @Indexed Instant createdAt) {
}
