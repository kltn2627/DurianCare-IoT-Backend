package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.CultivationSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CultivationScheduleRepository extends MongoRepository<CultivationSchedule, String> {
}
