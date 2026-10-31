package com.duriancare.farm.repository;

import com.duriancare.farm.domain.TreeCareTask;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TreeCareTaskRepository extends MongoRepository<TreeCareTask, String> {

    List<TreeCareTask> findByCarePlanIdOrderBySortOrderAsc(String carePlanId);

    boolean existsByCarePlanId(String carePlanId);
}
