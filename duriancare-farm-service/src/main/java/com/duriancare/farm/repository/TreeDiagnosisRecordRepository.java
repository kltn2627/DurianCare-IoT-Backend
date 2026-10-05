package com.duriancare.farm.repository;

import com.duriancare.farm.domain.TreeDiagnosisRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TreeDiagnosisRecordRepository extends MongoRepository<TreeDiagnosisRecord, String> {

    Page<TreeDiagnosisRecord> findByTreeIdOrderByDiagnosedAtDesc(String treeId, Pageable pageable);

    Optional<TreeDiagnosisRecord> findTopByTreeIdOrderByDiagnosedAtDesc(String treeId);

    long countByTreeId(String treeId);

    List<String> findDistinctTreeIdByFarmZoneId(String farmZoneId);
}
