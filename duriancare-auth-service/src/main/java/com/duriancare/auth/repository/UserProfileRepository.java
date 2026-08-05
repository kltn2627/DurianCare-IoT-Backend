package com.duriancare.auth.repository;

import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUser_Id(UUID userId);

    @Query("""
            select p from UserProfile p
            join fetch p.user u
            where u.id <> :currentUserId
              and u.role = :role
              and u.status = :status
              and lower(replace(replace(replace(replace(replace(coalesce(p.phoneNumber, ''), ' ', ''), '-', ''), '.', ''), '(', ''), ')', '')) = :phoneNumber
            order by p.updatedAt desc
            """)
    List<UserProfile> findCounterpartsByNormalizedPhone(
            @Param("currentUserId") UUID currentUserId,
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            @Param("phoneNumber") String phoneNumber,
            Pageable pageable);

    @Query(value = """
            select p from UserProfile p
            join fetch p.user u
            where u.id <> :currentUserId
              and u.role = :role
              and u.status = :status
              and (
                :query = ''
                or lower(p.fullName) like lower(concat('%', :query, '%'))
                or lower(coalesce(p.provinceCity, '')) like lower(concat('%', :query, '%'))
              )
            order by p.fullName asc
            """,
            countQuery = """
            select count(p) from UserProfile p
            join p.user u
            where u.id <> :currentUserId
              and u.role = :role
              and u.status = :status
              and (
                :query = ''
                or lower(p.fullName) like lower(concat('%', :query, '%'))
                or lower(coalesce(p.provinceCity, '')) like lower(concat('%', :query, '%'))
              )
            """)
    org.springframework.data.domain.Page<UserProfile> findCommunityUsers(
            @Param("currentUserId") UUID currentUserId,
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            @Param("query") String query,
            Pageable pageable);
}
