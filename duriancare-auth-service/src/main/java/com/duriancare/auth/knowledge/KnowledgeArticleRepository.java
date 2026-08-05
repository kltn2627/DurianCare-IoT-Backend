package com.duriancare.auth.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {

    boolean existsBySlug(String slug);

    Optional<KnowledgeArticle> findBySlugAndStatus(String slug, KnowledgeArticleStatus status);

    Page<KnowledgeArticle> findByStatus(KnowledgeArticleStatus status, Pageable pageable);

    Page<KnowledgeArticle> findByAuthorId(UUID authorId, Pageable pageable);

    Page<KnowledgeArticle> findByAuthorIdAndStatus(UUID authorId, KnowledgeArticleStatus status, Pageable pageable);
}
