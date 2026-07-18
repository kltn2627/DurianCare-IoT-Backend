package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ActivityStatus;
import com.duriancare.cultivation.domain.ActivityType;
import com.duriancare.cultivation.domain.CultivationActivity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CultivationActivityRepository extends MongoRepository<CultivationActivity, String> {
    List<CultivationActivity> findByCultivationPlanId(String cultivationPlanId);
    List<CultivationActivity> findByCultivationSeasonIdAndStatus(String cultivationSeasonId, ActivityStatus status);
    List<CultivationActivity> findByCultivationSeasonIdAndActivityType(String cultivationSeasonId, ActivityType activityType);
}
