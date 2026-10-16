package com.duriancare.auth.controller;

import com.duriancare.auth.domain.EngineerApplicationStatus;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.InternalAgronomistResponse;
import com.duriancare.auth.entity.EngineerApplication;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.repository.EngineerApplicationRepository;
import com.duriancare.auth.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/internal/v1/auth/agronomists")
public class InternalAgronomistController {

    private static final Set<UserRole> AGRONOMIST_ROLES = Set.of(UserRole.ENGINEER, UserRole.EXPERT);

    private final UserRepository userRepository;
    private final EngineerApplicationRepository engineerApplicationRepository;
    private final String internalToken;

    public InternalAgronomistController(
            UserRepository userRepository,
            EngineerApplicationRepository engineerApplicationRepository,
            @Value("${duriancare.internal.auth-token:${INTERNAL_SERVICE_TOKEN:local-internal-token}}")
            String internalToken) {
        this.userRepository = userRepository;
        this.engineerApplicationRepository = engineerApplicationRepository;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<InternalAgronomistResponse> list(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam(required = false) String query) {
        requireInternalToken(token);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByRoleInAndStatus(AGRONOMIST_ROLES, UserStatus.ACTIVE).stream()
                .map(this::toResponse)
                .filter(response -> !StringUtils.hasText(normalizedQuery) || matches(response, normalizedQuery))
                .toList();
    }

    @GetMapping("/{userId}")
    @Transactional(readOnly = true)
    InternalAgronomistResponse get(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable UUID userId) {
        requireInternalToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.duriancare.auth.exception.ResourceNotFoundException("Agronomist was not found"));
        return toResponse(user);
    }

    private void requireInternalToken(String token) {
        if (StringUtils.hasText(internalToken) && !internalToken.equals(token)) {
            throw new com.duriancare.auth.exception.InvalidTokenException("Internal service token is invalid");
        }
    }

    private boolean matches(InternalAgronomistResponse response, String query) {
        return contains(response.fullName(), query)
                || contains(response.email(), query)
                || contains(response.specialization(), query)
                || contains(response.workplace(), query)
                || contains(response.provinceCity(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private InternalAgronomistResponse toResponse(User user) {
        UserProfile profile = user.getProfile();
        EngineerApplication application = engineerApplicationRepository.findByUser_Id(user.getId()).orElse(null);
        boolean approvedApplication = application == null || application.getStatus() == EngineerApplicationStatus.APPROVED;
        boolean eligible = AGRONOMIST_ROLES.contains(user.getRole())
                && user.getStatus() == UserStatus.ACTIVE
                && approvedApplication;
        return new InternalAgronomistResponse(
                user.getId(),
                profile == null ? user.getEmail() : profile.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                application == null ? null : application.getWorkplace(),
                application == null ? null : application.getSpecialization(),
                application == null ? null : application.getYearsExperience(),
                profile == null ? null : profile.getProvinceCity(),
                profile == null ? null : profile.getAvatarUrl(),
                eligible);
    }
}
