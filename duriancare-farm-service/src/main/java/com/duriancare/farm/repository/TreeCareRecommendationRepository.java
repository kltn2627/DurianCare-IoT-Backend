package com.duriancare.farm.repository;

import com.duriancare.farm.domain.TreeCareRecommendation;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TreeCareRecommendationRepository extends MongoRepository<TreeCareRecommendation, String> {

    Optional<TreeCareRecommendation> findByDiagnosisIdAndSource(String diagnosisId, String source);

    Optional<TreeCareRecommendation> findTopByTreeIdOrderByCreatedAtDesc(String treeId);
}
