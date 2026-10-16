package com.duriancare.farm.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgronomistRolePolicy {

    private static final Set<String> AGRONOMIST_ROLES = Set.of("ENGINEER", "EXPERT");

    public boolean isAgronomist(String role) {
        return AGRONOMIST_ROLES.contains(normalize(role));
    }

    public boolean isOwner(String role) {
        return "FARMER".equals(normalize(role));
    }

    public String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
