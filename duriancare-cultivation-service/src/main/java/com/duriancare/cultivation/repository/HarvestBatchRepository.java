package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.HarvestBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HarvestBatchRepository extends MongoRepository<HarvestBatch, String> {
    List<HarvestBatch> findByCultivationSeasonId(String cultivationSeasonId);
    Optional<HarvestBatch> findByBatchCode(String batchCode);
}
