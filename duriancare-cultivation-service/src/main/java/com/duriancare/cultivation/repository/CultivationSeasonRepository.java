package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.CultivationSeason;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CultivationSeasonRepository extends MongoRepository<CultivationSeason, String> {

    List<CultivationSeason> findByFarmIdOrderByStartDateDesc(String farmId);

    List<CultivationSeason> findByFarmIdAndPlotIdOrderByStartDateDesc(String farmId, String plotId);
}
