package com.duriancare.traceability.repository;

import com.duriancare.traceability.domain.TraceabilityProfile;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TraceabilityProfileRepository
        extends MongoRepository<TraceabilityProfile, String> {

    Optional<TraceabilityProfile> findByPublicSlug(String publicSlug);
}
