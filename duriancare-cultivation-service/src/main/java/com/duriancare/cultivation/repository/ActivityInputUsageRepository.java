package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ActivityInputUsage;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityInputUsageRepository extends MongoRepository<ActivityInputUsage, String> {
    List<ActivityInputUsage> findByActivityExecutionId(String activityExecutionId);

    List<ActivityInputUsage> findByAgriculturalInputId(String agriculturalInputId);
}
