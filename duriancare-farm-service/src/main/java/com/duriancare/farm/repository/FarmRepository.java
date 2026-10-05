package com.duriancare.farm.repository;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface FarmRepository extends MongoRepository<Farm, String> {

    List<Farm> findByOwnerUserId(String ownerUserId);

    @Query("{ 'zones.id': ?0 }")
    Optional<Farm> findByZoneId(String zoneId);

    List<Farm> findByOwnerUserIdAndStatusNot(String ownerUserId, FarmStatus status);
}
