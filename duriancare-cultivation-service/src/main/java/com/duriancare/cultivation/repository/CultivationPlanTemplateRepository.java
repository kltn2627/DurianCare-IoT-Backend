package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.CultivationPlanTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CultivationPlanTemplateRepository extends MongoRepository<CultivationPlanTemplate, String> {
    Optional<CultivationPlanTemplate> findByCode(String code);

    List<CultivationPlanTemplate> findByActiveTrue();
}
