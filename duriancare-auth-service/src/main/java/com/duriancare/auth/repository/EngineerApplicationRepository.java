package com.duriancare.auth.repository;

import com.duriancare.auth.domain.EngineerApplicationStatus;
import com.duriancare.auth.entity.EngineerApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerApplicationRepository extends JpaRepository<EngineerApplication, UUID> {

    Optional<EngineerApplication> findByUser_Id(UUID userId);

    Optional<EngineerApplication> findByIdAndStatus(UUID id, EngineerApplicationStatus status);

    List<EngineerApplication> findAllByStatusOrderByCreatedAtDesc(EngineerApplicationStatus status);
}
