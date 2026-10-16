package com.duriancare.auth.dto;

public record UserProfileResponse(
        String fullName,
        String phoneNumber,
        String farmAddress,
        String avatarUrl) {
}
