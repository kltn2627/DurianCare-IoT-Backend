package com.duriancare.auth.repository;

import com.duriancare.auth.entity.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUser_Id(UUID userId);
}
