package com.duriancare.auth.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {

    boolean existsBySlug(String slug);

    Optional<KnowledgeArticle> findBySlugAndStatus(String slug, KnowledgeArticleStatus status);

    Page<KnowledgeArticle> findByStatus(KnowledgeArticleStatus status, Pageable pageable);

    Page<KnowledgeArticle> findByAuthorId(UUID authorId, Pageable pageable);

    Page<KnowledgeArticle> findByAuthorIdAndStatus(UUID authorId, KnowledgeArticleStatus status, Pageable pageable);

    @Query("""
            select article from KnowledgeArticle article
            where article.status = :status
              and (:category is null or lower(article.category) = :category)
              and (
                    :searchPattern is null
                    or lower(article.title) like :searchPattern
                    or lower(article.excerpt) like :searchPattern
                  )
            """)
    Page<KnowledgeArticle> searchByStatus(
            @Param("status") KnowledgeArticleStatus status,
            @Param("searchPattern") String searchPattern,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            select article from KnowledgeArticle article
            where (:status is null or article.status = :status)
              and (:category is null or lower(article.category) = :category)
              and (
                    :searchPattern is null
                    or lower(article.title) like :searchPattern
                    or lower(article.excerpt) like :searchPattern
                    or lower(article.authorName) like :searchPattern
                  )
            """)
    Page<KnowledgeArticle> searchForAdmin(
            @Param("status") KnowledgeArticleStatus status,
            @Param("searchPattern") String searchPattern,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            select article from KnowledgeArticle article
            where article.author.id = :authorId
              and (:status is null or article.status = :status)
              and (:category is null or lower(article.category) = :category)
              and (
                    :searchPattern is null
                    or lower(article.title) like :searchPattern
                    or lower(article.excerpt) like :searchPattern
                  )
            """)
    Page<KnowledgeArticle> searchMine(
            @Param("authorId") UUID authorId,
            @Param("status") KnowledgeArticleStatus status,
            @Param("searchPattern") String searchPattern,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            select article from KnowledgeArticle article
            where article.status = :status
              and article.featured = true
            """)
    Page<KnowledgeArticle> findFeaturedByStatus(
            @Param("status") KnowledgeArticleStatus status,
            Pageable pageable);

    @Query("""
            select article from KnowledgeArticle article
            where article.status = :status
              and lower(article.category) = :category
              and article.id <> :excludedId
            """)
    Page<KnowledgeArticle> findRelatedByStatusAndCategory(
            @Param("status") KnowledgeArticleStatus status,
            @Param("category") String category,
            @Param("excludedId") UUID excludedId,
            Pageable pageable);

    @Query("""
            select article.category, count(article)
            from KnowledgeArticle article
            where article.status = :status
            group by article.category
            order by count(article) desc, article.category asc
            """)
    java.util.List<Object[]> countByStatusGroupByCategory(@Param("status") KnowledgeArticleStatus status);
}
