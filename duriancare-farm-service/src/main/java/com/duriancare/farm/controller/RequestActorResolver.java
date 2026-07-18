package com.duriancare.farm.controller;

import com.duriancare.farm.dto.RequestActor;
import com.duriancare.farm.service.FarmAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RequestActorResolver {

    public RequestActor resolve(String userId, String email, String role) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(role)) {
            throw new FarmAuthenticationException("Authenticated user headers are required");
        }
        return new RequestActor(userId.trim(), email == null ? "" : email.trim(), role.trim());
    }
}
