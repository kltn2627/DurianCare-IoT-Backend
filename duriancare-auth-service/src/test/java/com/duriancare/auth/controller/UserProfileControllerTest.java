package com.duriancare.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duriancare.auth.domain.UserGender;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.AvatarUploadResponse;
import com.duriancare.auth.dto.ProfileResponse;
import com.duriancare.auth.security.JwtService;
import com.duriancare.auth.security.RevokedTokenService;
import com.duriancare.auth.service.UserProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.duriancare.auth.exception.GlobalExceptionHandler.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private RevokedTokenService revokedTokenService;

    @Test
    void getMyProfileReturnsProfileResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getProfile(userId)).thenReturn(sampleResponse(userId));

        mockMvc.perform(get("/api/users/me").principal(authentication(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("farmer@example.com"))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
    }

    @Test
    void updateMyProfileReturnsUpdatedProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.updateProfile(any(), any()))
                .thenReturn(sampleResponse(userId));

        mockMvc.perform(put("/api/users/me")
                        .principal(authentication(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyen Van B",
                                  "phoneNumber": "0901234567",
                                  "dateOfBirth": "1995-05-20",
                                  "gender": "MALE",
                                  "address": "New address",
                                  "provinceCity": "Can Tho",
                                  "bio": "Durian farmer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"));
    }

    @Test
    void uploadAvatarReturnsUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.uploadAvatar(any(), any()))
                .thenReturn(new AvatarUploadResponse("https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/new.png"));
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                new byte[] {1, 2, 3, 4});

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(avatar)
                        .principal(authentication(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/new.png"));
    }

    @Test
    void deleteAvatarReturnsMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(delete("/api/users/me/avatar").principal(authentication(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Avatar removed."));

        verify(userProfileService).deleteAvatar(userId);
    }

    @Test
    void missingPrincipalIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authenticated user is required"));
    }

    @Test
    void invalidUpdatePayloadIsRejected() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .principal(authentication(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "dateOfBirth": "2099-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private ProfileResponse sampleResponse(UUID userId) {
        return new ProfileResponse(
                userId,
                "farmer@example.com",
                UserRole.FARMER,
                UserStatus.ACTIVE,
                "Nguyen Van A",
                "0901234567",
                LocalDate.of(1995, 5, 20),
                UserGender.OTHER,
                "Ward 1, District 1",
                "Can Tho",
                "Durian farmer",
                "https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/current.png",
                LocalDateTime.now(ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC));
    }

    private UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new com.duriancare.auth.security.AuthenticatedUser(
                        userId,
                        "farmer@example.com",
                        UserRole.FARMER,
                        "token-jti"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_FARMER")));
    }
}
