package com.duriancare.auth.service;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "duriancare.profile.avatar", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAvatarStorageService implements AvatarStorageService {

    @Override
    public StoredAvatar upload(UUID userId, MultipartFile avatar) {
        throw new AvatarStorageException("Avatar storage is not configured");
    }

    @Override
    public void delete(String avatarUrl) {
        throw new AvatarStorageException("Avatar storage is not configured");
    }
}
