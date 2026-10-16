package com.duriancare.auth.service.impl;

import com.duriancare.auth.dto.AvatarUploadResponse;
import com.duriancare.auth.dto.ProfileResponse;
import com.duriancare.auth.dto.UpdateProfileRequest;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.mapper.ProfileMapper;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import com.duriancare.auth.service.AvatarStorageException;
import com.duriancare.auth.service.AvatarStorageService;
import com.duriancare.auth.service.StoredAvatar;
import com.duriancare.auth.service.UserProfileService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserProfileServiceImpl.class);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final AvatarStorageService avatarStorageService;

    public UserProfileServiceImpl(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            AvatarStorageService avatarStorageService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.avatarStorageService = avatarStorageService;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        User user = loadUser(userId);
        UserProfile profile = loadProfile(userId);
        return ProfileMapper.toResponse(user, profile);
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = loadUser(userId);
        UserProfile profile = loadProfile(userId);
        ProfileMapper.apply(request, profile);
        profileRepository.save(profile);
        return ProfileMapper.toResponse(user, profile);
    }

    @Override
    @Transactional
    public AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile avatar) {
        UserProfile profile = loadProfile(userId);
        String previousAvatarUrl = profile.getAvatarUrl();
        StoredAvatar storedAvatar = avatarStorageService.upload(userId, avatar);
        try {
            profile.setAvatarUrl(storedAvatar.avatarUrl());
            profileRepository.save(profile);
        } catch (RuntimeException exception) {
            try {
                avatarStorageService.delete(storedAvatar.avatarUrl());
            } catch (AvatarStorageException cleanupException) {
                LOGGER.warn("Failed to cleanup avatar after profile save error: {}", cleanupException.getMessage());
            }
            throw exception;
        }

        if (previousAvatarUrl != null && !previousAvatarUrl.isBlank()) {
            try {
                avatarStorageService.delete(previousAvatarUrl);
            } catch (AvatarStorageException exception) {
                LOGGER.warn("Failed to delete previous avatar {}: {}", previousAvatarUrl, exception.getMessage());
            }
        }

        return new AvatarUploadResponse(storedAvatar.avatarUrl());
    }

    @Override
    @Transactional
    public void deleteAvatar(UUID userId) {
        UserProfile profile = loadProfile(userId);
        String previousAvatarUrl = profile.getAvatarUrl();
        profile.setAvatarUrl(null);
        profileRepository.save(profile);

        if (previousAvatarUrl != null && !previousAvatarUrl.isBlank()) {
            try {
                avatarStorageService.delete(previousAvatarUrl);
            } catch (AvatarStorageException exception) {
                LOGGER.warn("Failed to delete avatar from storage {}: {}", previousAvatarUrl, exception.getMessage());
            }
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
    }

    private UserProfile loadProfile(UUID userId) {
        return profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile was not found"));
    }
}
