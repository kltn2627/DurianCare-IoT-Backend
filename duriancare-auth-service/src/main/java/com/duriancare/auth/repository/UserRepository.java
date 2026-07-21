package com.duriancare.auth.repository;

import com.duriancare.auth.entity.User;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<User> findByRoleInAndStatus(Set<com.duriancare.auth.domain.UserRole> roles,
            com.duriancare.auth.domain.UserStatus status);
}
