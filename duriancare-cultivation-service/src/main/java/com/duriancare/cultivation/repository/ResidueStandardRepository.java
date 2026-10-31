package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ResidueStandard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResidueStandardRepository extends MongoRepository<ResidueStandard, String> {
    List<ResidueStandard> findByMarketCodeAndCommodityCodeAndActiveIngredientCodeAndActiveTrueAndVerifiedTrueOrderByEffectiveFromDesc(
            String marketCode, String commodityCode, String activeIngredientCode);
    Optional<ResidueStandard> findFirstByMarketCodeAndCommodityCodeAndActiveIngredientCodeAndActiveTrueOrderByEffectiveFromDesc(
            String marketCode,
            String commodityCode,
            String activeIngredientCode);
}
