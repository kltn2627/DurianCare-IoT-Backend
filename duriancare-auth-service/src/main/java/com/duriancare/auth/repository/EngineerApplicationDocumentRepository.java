package com.duriancare.auth.repository;

import com.duriancare.auth.entity.EngineerApplicationDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerApplicationDocumentRepository
        extends JpaRepository<EngineerApplicationDocument, UUID> {

    List<EngineerApplicationDocument> findAllByApplication_IdOrderByCreatedAtAsc(UUID applicationId);
}
