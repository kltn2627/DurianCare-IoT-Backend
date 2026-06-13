package com.duriancare.auth.repository;

import com.duriancare.auth.entity.OtpVerification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findByEmailIgnoreCase(String email);
}
