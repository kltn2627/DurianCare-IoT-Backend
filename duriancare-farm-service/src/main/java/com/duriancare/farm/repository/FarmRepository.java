package com.duriancare.farm.repository;

import com.duriancare.farm.domain.Farm;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmRepository extends MongoRepository<Farm, String> {

    List<Farm> findByOwnerUserId(String ownerUserId);
}
