package com.duriancare.farm.repository;

import com.duriancare.farm.domain.TreeCarePlan;
import com.duriancare.farm.domain.TreeCarePlanStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TreeCarePlanRepository extends MongoRepository<TreeCarePlan, String> {

    List<TreeCarePlan> findByTreeIdOrderByCreatedAtDesc(String treeId);

    List<TreeCarePlan> findByTreeIdAndStatus(String treeId, TreeCarePlanStatus status);
}
