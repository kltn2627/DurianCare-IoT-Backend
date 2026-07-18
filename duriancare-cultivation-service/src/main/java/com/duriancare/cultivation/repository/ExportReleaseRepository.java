package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.ExportRelease;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExportReleaseRepository extends MongoRepository<ExportRelease, String> {
    List<ExportRelease> findByHarvestBatchId(String harvestBatchId);
    Optional<ExportRelease> findByReleaseCode(String releaseCode);
}
