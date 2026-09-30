package com.duriancare.farm.repository;

import com.duriancare.farm.domain.DurianTree;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DurianTreeRepository extends MongoRepository<DurianTree, String> {

    List<DurianTree> findByFarmZoneId(String farmZoneId);

    List<DurianTree> findByFarmZoneIdAndStatus(String farmZoneId, com.duriancare.farm.domain.TreeStatus status);

    boolean existsByFarmZoneIdAndTreeCode(String farmZoneId, String treeCode);

    long countByFarmZoneId(String farmZoneId);

    long countByFarmZoneIdAndStatus(String farmZoneId, com.duriancare.farm.domain.TreeStatus status);
}
