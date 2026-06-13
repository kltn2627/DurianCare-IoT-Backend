package com.duriancare.traceability.repository;

import com.duriancare.traceability.domain.QrTraceability;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface QrTraceabilityRepository
        extends MongoRepository<QrTraceability, String> {

    Optional<QrTraceability> findByTokenHashAndActiveTrue(String tokenHash);
}
