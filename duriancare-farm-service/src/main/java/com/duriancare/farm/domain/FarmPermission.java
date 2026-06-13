package com.duriancare.farm.domain;

public record FarmPermission(
        FarmPermissionType permission,
        boolean granted) {
}
