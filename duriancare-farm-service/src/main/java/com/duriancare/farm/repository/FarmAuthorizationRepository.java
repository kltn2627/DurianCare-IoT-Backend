package com.duriancare.farm.repository;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.FarmAuthorization;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmAuthorizationRepository
        extends MongoRepository<FarmAuthorization, String> {

    List<FarmAuthorization> findByEngineerUserIdAndStatus(
            String engineerUserId,
            AuthorizationStatus status);
}
