package com.duriancare.farm.service;

import com.duriancare.farm.domain.FarmAuthorizationAuditAction;
import com.duriancare.farm.domain.FarmAuthorizationAuditLog;
import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.repository.FarmAuthorizationAuditLogRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FarmAuthorizationAuditService {

    private final FarmAuthorizationAuditLogRepository repository;

    public FarmAuthorizationAuditService(FarmAuthorizationAuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            FarmAuthorizationAuditAction action,
            RequestActor actor,
            String farmId,
            String targetAgronomistId,
            String resourceId,
            Map<String, Object> before,
            Map<String, Object> after) {
        repository.save(new FarmAuthorizationAuditLog(
                null,
                action,
                actor.userId(),
                actor.role(),
                farmId,
                targetAgronomistId,
                resourceId,
                before == null ? Map.of() : before,
                after == null ? Map.of() : after,
                Instant.now()));
    }
}
