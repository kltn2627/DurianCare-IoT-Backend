package com.duriancare.cultivation.repository;

import com.duriancare.cultivation.domain.AgriculturalInput;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AgriculturalInputRepository extends MongoRepository<AgriculturalInput, String> {
    Optional<AgriculturalInput> findByCode(String code);
}
