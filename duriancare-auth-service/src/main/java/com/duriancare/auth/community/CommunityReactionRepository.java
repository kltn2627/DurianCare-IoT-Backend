package com.duriancare.auth.community;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityReactionRepository extends JpaRepository<CommunityReaction, UUID> {
    Optional<CommunityReaction> findByPostIdAndUserId(UUID postId, UUID userId);
    long countByPostId(UUID postId);
}
