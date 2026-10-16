package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.HarvestDispatch;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HarvestDispatchRepository extends MongoRepository<HarvestDispatch, String> {
    List<HarvestDispatch> findByFarmId(String farmId);
}
