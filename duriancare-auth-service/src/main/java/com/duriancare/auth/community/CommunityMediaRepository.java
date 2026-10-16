package com.duriancare.auth.community;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityMediaRepository extends JpaRepository<CommunityMedia, UUID> {
    List<CommunityMedia> findByPostIdOrderBySortOrderAsc(UUID postId);
}
