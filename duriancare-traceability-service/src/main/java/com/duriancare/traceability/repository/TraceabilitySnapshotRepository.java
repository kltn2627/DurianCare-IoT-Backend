package com.duriancare.traceability.repository;

import com.duriancare.traceability.domain.TraceabilitySnapshot;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TraceabilitySnapshotRepository
        extends MongoRepository<TraceabilitySnapshot, String> {

    List<TraceabilitySnapshot> findByTraceabilityProfileIdOrderByVersionDesc(
            String traceabilityProfileId);
}
