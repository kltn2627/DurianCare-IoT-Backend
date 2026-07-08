package com.duriancare.auth.service;

import com.duriancare.auth.dto.AvatarUploadResponse;
import com.duriancare.auth.dto.ProfileResponse;
import com.duriancare.auth.dto.UpdateProfileRequest;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    ProfileResponse getProfile(UUID userId);

    ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile avatar);

    void deleteAvatar(UUID userId);
}
