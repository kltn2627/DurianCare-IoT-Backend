package com.duriancare.auth.mapper;

import com.duriancare.auth.dto.ProfileResponse;
import com.duriancare.auth.dto.UpdateProfileRequest;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserProfile;

public final class ProfileMapper {

    private ProfileMapper() {
    }

    public static ProfileResponse toResponse(User user, UserProfile profile) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                profile.getFullName(),
                profile.getPhoneNumber(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getAddress(),
                profile.getProvinceCity(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public static void apply(UpdateProfileRequest request, UserProfile profile) {
        if (request.fullName() != null && !request.fullName().isBlank()) {
            profile.setFullName(request.fullName().trim());
        }
        if (request.phoneNumber() != null) {
            profile.setPhoneNumber(trimToNull(request.phoneNumber()));
        }
        if (request.dateOfBirth() != null) {
            profile.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            profile.setGender(request.gender());
        }
        if (request.address() != null) {
            profile.setAddress(trimToNull(request.address()));
        }
        if (request.provinceCity() != null) {
            profile.setProvinceCity(trimToNull(request.provinceCity()));
        }
        if (request.bio() != null) {
            profile.setBio(trimToNull(request.bio()));
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
