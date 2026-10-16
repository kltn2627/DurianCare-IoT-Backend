package com.duriancare.farm.service;

import com.duriancare.farm.domain.AuthorizationStatus;
import com.duriancare.farm.domain.Farm;
import com.duriancare.farm.domain.FarmAuthorization;
import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.domain.FarmZone;
import com.duriancare.farm.dto.FarmAccessCheckRequest;
import com.duriancare.farm.repository.FarmAuthorizationRepository;
import com.duriancare.farm.repository.FarmRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FarmAccessCheckService {

    private final FarmRepository farmRepository;
    private final FarmAuthorizationRepository authorizationRepository;
    private final AgronomistRolePolicy rolePolicy;

    public FarmAccessCheckService(
            FarmRepository farmRepository,
            FarmAuthorizationRepository authorizationRepository,
            AgronomistRolePolicy rolePolicy) {
        this.farmRepository = farmRepository;
        this.authorizationRepository = authorizationRepository;
        this.rolePolicy = rolePolicy;
    }

    public boolean canAccess(FarmAccessCheckRequest request) {
        if (request == null || !StringUtils.hasText(request.userId()) || !StringUtils.hasText(request.role())) {
            throw new FarmAuthenticationException("Authenticated user is required");
        }
        String farmId = requireText(request.farmId());
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new FarmNotFoundException("Farm was not found"));
        String role = rolePolicy.normalize(request.role());
        String areaId = normalizeOptional(request.cultivationAreaId());

        if (rolePolicy.isOwner(role)) {
            return Objects.equals(farm.ownerUserId(), request.userId().trim())
                    && areaBelongsToFarm(farm, areaId);
        }
        if (!rolePolicy.isAgronomist(role)) {
            return false;
        }

        FarmAuthorization authorization = authorizationRepository
                .findByFarmIdAndAgronomistIdAndStatus(farm.id(), request.userId().trim(), AuthorizationStatus.ACTIVE)
                .orElse(null);
        if (authorization == null || isExpired(authorization.expiresAt())) {
            return false;
        }
        return hasPermission(authorization, request.permission())
                && areaAllowed(farm, authorization.allowedCultivationAreaIds(), areaId);
    }

    public List<String> alertRecipients(String farmId, String cultivationAreaId, FarmPermissionType permission) {
        Farm farm = farmRepository.findById(requireText(farmId))
                .orElseThrow(() -> new FarmNotFoundException("Farm was not found"));
        String areaId = normalizeOptional(cultivationAreaId);
        if (!areaBelongsToFarm(farm, areaId)) {
            return List.of();
        }
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        if (StringUtils.hasText(farm.ownerUserId())) {
            recipients.add(farm.ownerUserId());
        }
        authorizationRepository.findByFarmIdAndStatus(farm.id(), AuthorizationStatus.ACTIVE).stream()
                .filter(authorization -> !isExpired(authorization.expiresAt()))
                .filter(authorization -> hasPermission(authorization, permission))
                .filter(authorization -> areaAllowed(farm, authorization.allowedCultivationAreaIds(), areaId))
                .map(FarmAuthorization::agronomistId)
                .filter(StringUtils::hasText)
                .forEach(recipients::add);
        return List.copyOf(recipients);
    }

    private boolean hasPermission(FarmAuthorization authorization, FarmPermissionType permission) {
        return permission != null
                && authorization.permissions() != null
                && authorization.permissions().contains(permission);
    }

    private boolean areaAllowed(Farm farm, List<String> allowedAreaIds, String areaId) {
        if (!areaBelongsToFarm(farm, areaId)) {
            return false;
        }
        if (!StringUtils.hasText(areaId)) {
            return true;
        }
        return allowedAreaIds != null && allowedAreaIds.contains(areaId);
    }

    private boolean areaBelongsToFarm(Farm farm, String areaId) {
        if (!StringUtils.hasText(areaId)) {
            return true;
        }
        return farm.zones() != null
                && farm.zones().stream()
                        .map(FarmZone::id)
                        .filter(StringUtils::hasText)
                        .anyMatch(areaId::equals);
    }

    private boolean isExpired(Instant expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private String requireText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new FarmInvalidRequestException("Farm id is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
