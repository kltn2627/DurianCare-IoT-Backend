package com.duriancare.farm.repository;

import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmRepository extends MongoRepository<Farm, String> {

    List<Farm> findByOwnerUserId(String ownerUserId);

    List<Farm> findByOwnerUserIdAndStatusNot(String ownerUserId, FarmStatus status);
}
