package com.duriancare.farm.repository;

import com.duriancare.farm.domain.FarmAuthorizationAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmAuthorizationAuditLogRepository
        extends MongoRepository<FarmAuthorizationAuditLog, String> {
}
