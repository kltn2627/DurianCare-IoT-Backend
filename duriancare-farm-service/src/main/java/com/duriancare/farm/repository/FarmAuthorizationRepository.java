package com.duriancare.farm.repository;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmAuthorization;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface FarmAuthorizationRepository
        extends MongoRepository<FarmAuthorization, String> {

    @Query("{'farmId': ?0, 'ownerUserId': ?1}")
    List<FarmAuthorization> findByFarmIdAndOwnerId(String farmId, String ownerId);

    @Query(value = "{'farmId': ?0, 'engineerUserId': ?1, 'status': ?2}", exists = true)
    boolean existsByFarmIdAndAgronomistIdAndStatus(
            String farmId,
            String agronomistId,
            AuthorizationStatus status);

    @Query("{'engineerUserId': ?0, 'status': ?1}")
    List<FarmAuthorization> findByAgronomistIdAndStatus(
            String agronomistId,
            AuthorizationStatus status);

    List<FarmAuthorization> findByEngineerUserIdAndStatus(
            String engineerUserId,
            AuthorizationStatus status);
}
