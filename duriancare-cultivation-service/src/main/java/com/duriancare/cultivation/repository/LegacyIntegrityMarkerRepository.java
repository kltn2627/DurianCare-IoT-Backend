package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.LegacyIntegrityMarker;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LegacyIntegrityMarkerRepository extends MongoRepository<LegacyIntegrityMarker, String> {
}
