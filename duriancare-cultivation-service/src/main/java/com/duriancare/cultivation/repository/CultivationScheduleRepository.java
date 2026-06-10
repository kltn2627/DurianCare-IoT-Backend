package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.CultivationSchedule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CultivationScheduleRepository extends JpaRepository<CultivationSchedule, UUID> {
}
