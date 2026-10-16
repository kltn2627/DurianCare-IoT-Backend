package com.duriancare.auth.dto;

public record AccessTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn) {
}
