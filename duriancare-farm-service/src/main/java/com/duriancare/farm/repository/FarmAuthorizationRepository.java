package com.duriancare.farm.repository;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmAuthorization;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmAuthorizationRepository
        extends MongoRepository<FarmAuthorization, String> {

    List<FarmAuthorization> findByAgronomistIdAndStatus(
            String agronomistId,
            AuthorizationStatus status);

    List<FarmAuthorization> findByFarmIdAndOwnerId(
            String farmId,
            String ownerId);

    Optional<FarmAuthorization> findByFarmIdAndAgronomistIdAndStatus(
            String farmId,
            String agronomistId,
            AuthorizationStatus status);

    boolean existsByFarmIdAndAgronomistIdAndStatus(
            String farmId,
            String agronomistId,
            AuthorizationStatus status);
}
