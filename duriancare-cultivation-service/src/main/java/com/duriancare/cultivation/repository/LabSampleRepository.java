package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.LabSample;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LabSampleRepository extends MongoRepository<LabSample, String> {
    List<LabSample> findByCultivationSeasonId(String cultivationSeasonId);
    Optional<LabSample> findBySampleCode(String sampleCode);
}
