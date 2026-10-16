package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.CultivationPlan;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CultivationPlanRepository extends MongoRepository<CultivationPlan, String> {
    List<CultivationPlan> findByCultivationSeasonId(String cultivationSeasonId);

    List<CultivationPlan> findByFarmIdAndPlotId(String farmId, String plotId);
}
