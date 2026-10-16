package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.LabResidueResult;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LabResidueResultRepository extends MongoRepository<LabResidueResult, String> {
    List<LabResidueResult> findByLabSampleId(String labSampleId);
}
