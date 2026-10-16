package com.duriancare.cultivation.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CultivationAccessGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(CultivationAccessGuard.class);

    private final FarmAccessClient farmAccessClient;

    public CultivationAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    public void requireView(CultivationActor actor, String resourceType, String resourceId, String farmId, String plotId) {
        require(actor, FarmPermissionType.VIEW_CARE_SCHEDULE, resourceType, resourceId, farmId, plotId);
    }

    public boolean canView(CultivationActor actor, String farmId, String plotId) {
        requireAuthenticated(actor);
        return farmAccessClient.canAccess(actor, FarmPermissionType.VIEW_CARE_SCHEDULE, farmId, plotId);
    }

    public void requireCreate(CultivationActor actor, String resourceType, String resourceId, String farmId, String plotId) {
        require(actor, FarmPermissionType.CREATE_CARE_SCHEDULE, resourceType, resourceId, farmId, plotId);
    }

    public void requireUpdate(CultivationActor actor, String resourceType, String resourceId, String farmId, String plotId) {
        require(actor, FarmPermissionType.UPDATE_CARE_SCHEDULE, resourceType, resourceId, farmId, plotId);
    }

    private void require(
            CultivationActor actor,
            FarmPermissionType permission,
            String resourceType,
            String resourceId,
            String farmId,
            String plotId) {
        requireAuthenticated(actor);
        boolean allowed = farmAccessClient.canAccess(actor, permission, farmId, plotId);
        if (!allowed) {
            LOGGER.warn(
                    "Cultivation access denied userId={} role={} permission={} resourceType={} resourceId={} farmId={} plotId={}",
                    actor.userId(), actor.role(), permission, resourceType, resourceId, farmId, plotId);
            throw new CultivationAccessDeniedException("Access denied for cultivation resource");
        }
    }

    private void requireAuthenticated(CultivationActor actor) {
        if (actor == null || !StringUtils.hasText(actor.userId()) || !StringUtils.hasText(actor.role())) {
            throw new CultivationAuthenticationException("Authenticated user is required");
        }
    }
}
