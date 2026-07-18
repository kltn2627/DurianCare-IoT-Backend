package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.AuditLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId);
}
