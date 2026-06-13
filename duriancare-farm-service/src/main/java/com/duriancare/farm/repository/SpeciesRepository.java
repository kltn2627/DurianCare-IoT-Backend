package com.duriancare.farm.repository;

import com.duriancare.farm.domain.Species;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpeciesRepository extends MongoRepository<Species, String> {

    Optional<Species> findByCultivarIgnoreCase(String cultivar);
}
