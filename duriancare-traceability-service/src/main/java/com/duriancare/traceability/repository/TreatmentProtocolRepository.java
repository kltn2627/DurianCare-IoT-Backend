package com.duriancare.traceability.repository;

import com.duriancare.traceability.domain.ProtocolStatus;
import com.duriancare.traceability.domain.TreatmentProtocol;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TreatmentProtocolRepository
        extends MongoRepository<TreatmentProtocol, String> {

    List<TreatmentProtocol> findByFarmZoneIdAndStatus(
            String farmZoneId,
            ProtocolStatus status);
}
