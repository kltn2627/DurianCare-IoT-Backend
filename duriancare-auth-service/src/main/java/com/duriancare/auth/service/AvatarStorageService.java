package com.duriancare.auth.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {

    StoredAvatar upload(UUID userId, MultipartFile avatar);

    void delete(String avatarUrl);
}
