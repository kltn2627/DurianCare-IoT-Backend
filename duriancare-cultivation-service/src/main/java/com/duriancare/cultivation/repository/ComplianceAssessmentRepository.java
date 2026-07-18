package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ComplianceAssessment;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ComplianceAssessmentRepository extends MongoRepository<ComplianceAssessment, String> {
    List<ComplianceAssessment> findByCultivationSeasonId(String cultivationSeasonId);
    List<ComplianceAssessment> findByHarvestBatchId(String harvestBatchId);
}
