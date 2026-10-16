package com.duriancare.auth.controller;

import com.duriancare.auth.dto.AvatarUploadResponse;
import com.duriancare.auth.dto.MessageResponse;
import com.duriancare.auth.dto.ProfileResponse;
import com.duriancare.auth.dto.UpdateProfileRequest;
import com.duriancare.auth.exception.InvalidTokenException;
import com.duriancare.auth.security.AuthenticatedUser;
import com.duriancare.auth.service.UserProfileService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    ProfileResponse getMyProfile(Principal principal) {
        return userProfileService.getProfile(resolveUserId(principal));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the authenticated user's profile")
    ProfileResponse updateMyProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.updateProfile(resolveUserId(principal), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the authenticated user's avatar")
    AvatarUploadResponse uploadAvatar(
            Principal principal,
            @RequestPart("avatar") MultipartFile avatar) {
        return userProfileService.uploadAvatar(resolveUserId(principal), avatar);
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Delete the authenticated user's avatar")
    ResponseEntity<MessageResponse> deleteAvatar(Principal principal) {
        userProfileService.deleteAvatar(resolveUserId(principal));
        return ResponseEntity.ok(new MessageResponse("Avatar removed."));
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.userId();
        }
        throw new InvalidTokenException("Authenticated user is required");
    }
}
