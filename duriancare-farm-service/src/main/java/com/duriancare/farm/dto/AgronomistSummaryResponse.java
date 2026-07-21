package com.duriancare.farm.dto;

public record AgronomistSummaryResponse(
        String id,
        String fullName,
        String email,
        String role,
        String workplace,
        String specialization,
        Integer yearsExperience,
        String provinceCity,
        String avatarUrl,
        boolean eligible) {
}
