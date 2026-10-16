package com.duriancare.farm.repository;

import com.duriancare.farm.domain.AgronomistInvitation;
import com.duriancare.farm.domain.AgronomistInvitationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AgronomistInvitationRepository
        extends MongoRepository<AgronomistInvitation, String> {

    List<AgronomistInvitation> findByFarmIdAndOwnerId(
            String farmId,
            String ownerId);

    List<AgronomistInvitation> findByAgronomistIdAndStatus(
            String agronomistId,
            AgronomistInvitationStatus status);

    Optional<AgronomistInvitation> findByFarmIdAndAgronomistIdAndStatus(
            String farmId,
            String agronomistId,
            AgronomistInvitationStatus status);

    boolean existsByFarmIdAndAgronomistIdAndStatus(
            String farmId,
            String agronomistId,
            AgronomistInvitationStatus status);
}
