package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ResidueStandard;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResidueStandardRepository extends MongoRepository<ResidueStandard, String> {
    Optional<ResidueStandard> findFirstByMarketCodeAndCommodityCodeAndActiveIngredientCodeAndActiveTrueOrderByEffectiveFromDesc(
            String marketCode,
            String commodityCode,
            String activeIngredientCode);
}
