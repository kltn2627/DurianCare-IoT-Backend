package com.duriancare.auth.community;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {
    List<CommunityComment> findTop20ByPostIdOrderByCreatedAtAsc(UUID postId);

    long countByParentId(UUID parentId);
}
