package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ActivityExecution;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityExecutionRepository extends MongoRepository<ActivityExecution, String> {
    List<ActivityExecution> findByCultivationActivityId(String cultivationActivityId);
}
