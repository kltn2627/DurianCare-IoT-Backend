package com.duriancare.auth.community;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query(value = """
            select p from CommunityPost p
            join fetch p.author a
            left join fetch a.profile pr
            where p.status <> com.duriancare.auth.community.CommunityPostStatus.HIDDEN
              and (:topic = '' or p.topic = :topic)
              and (
                :query = ''
                or lower(p.content) like lower(concat('%', :query, '%'))
                or lower(coalesce(pr.fullName, a.email)) like lower(concat('%', :query, '%'))
              )
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from CommunityPost p
            join p.author a
            left join a.profile pr
            where p.status <> com.duriancare.auth.community.CommunityPostStatus.HIDDEN
              and (:topic = '' or p.topic = :topic)
              and (
                :query = ''
                or lower(p.content) like lower(concat('%', :query, '%'))
                or lower(coalesce(pr.fullName, a.email)) like lower(concat('%', :query, '%'))
              )
            """)
    Page<CommunityPost> feed(@Param("topic") String topic, @Param("query") String query, Pageable pageable);

    @Query(value = """
            select p from CommunityPost p
            join fetch p.author a
            left join fetch a.profile pr
            where p.author.id = :authorId
              and p.status <> com.duriancare.auth.community.CommunityPostStatus.HIDDEN
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from CommunityPost p
            where p.author.id = :authorId
            and p.status <> com.duriancare.auth.community.CommunityPostStatus.HIDDEN
            """)
    Page<CommunityPost> findVisibleByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);

    @Query(value = """
            select p from CommunityPost p
            join fetch p.author a
            left join fetch a.profile pr
            where (:status is null or p.status = :status)
              and (:topic = '' or p.topic = :topic)
              and (
                :query = ''
                or lower(p.content) like lower(concat('%', :query, '%'))
                or lower(coalesce(pr.fullName, a.email)) like lower(concat('%', :query, '%'))
              )
            order by p.createdAt desc
            """,
            countQuery = """
            select count(p) from CommunityPost p
            join p.author a
            left join a.profile pr
            where (:status is null or p.status = :status)
              and (:topic = '' or p.topic = :topic)
              and (
                :query = ''
                or lower(p.content) like lower(concat('%', :query, '%'))
                or lower(coalesce(pr.fullName, a.email)) like lower(concat('%', :query, '%'))
              )
            """)
    Page<CommunityPost> adminList(
            @Param("status") CommunityPostStatus status,
            @Param("topic") String topic,
            @Param("query") String query,
            Pageable pageable);
}
