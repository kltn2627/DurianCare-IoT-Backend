package com.duriancare.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.domain.UserGender;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.UpdateProfileRequest;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import com.duriancare.auth.service.AvatarStorageService;
import com.duriancare.auth.service.StoredAvatar;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private AvatarStorageService avatarStorageService;

    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(userRepository, profileRepository, avatarStorageService);
    }

    @Test
    void getProfileReturnsProfileData() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        UserProfile profile = buildProfile(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        var response = service.getProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("farmer@example.com");
        assertThat(response.fullName()).isEqualTo("Nguyen Van A");
        assertThat(response.address()).isEqualTo("Ward 1, District 1");
    }

    @Test
    void updateProfileAppliesEditableFields() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        UserProfile profile = buildProfile(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateProfile(userId, new UpdateProfileRequest(
                "Nguyen Van B",
                "0900000000",
                LocalDate.of(1996, 1, 1),
                UserGender.MALE,
                "New address",
                "Ho Chi Minh",
                "Short bio"));

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo("Nguyen Van B");
        assertThat(profileCaptor.getValue().getPhoneNumber()).isEqualTo("0900000000");
        assertThat(profileCaptor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(1996, 1, 1));
        assertThat(response.gender()).isEqualTo(UserGender.MALE);
        assertThat(response.provinceCity()).isEqualTo("Ho Chi Minh");
    }

    @Test
    void uploadAvatarStoresReturnedUrlAndReplacesPreviousAvatar() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        UserProfile profile = buildProfile(user);
        ReflectionTestUtils.setField(profile, "avatarUrl", "https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/old.png");
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(avatarStorageService.upload(any(), any()))
                .thenReturn(new StoredAvatar("avatars/2026/07/08/new.png",
                        "https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/2026/07/08/new.png"));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.uploadAvatar(userId, new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                new byte[] {1, 2, 3, 4}));

        assertThat(response.avatarUrl()).contains("avatars/2026/07/08/new.png");
        verify(avatarStorageService).delete("https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/old.png");
    }

    @Test
    void deleteAvatarClearsProfileReference() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        UserProfile profile = buildProfile(user);
        ReflectionTestUtils.setField(profile, "avatarUrl", "https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/current.png");
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteAvatar(userId);

        assertThat(profile.getAvatarUrl()).isNull();
        verify(avatarStorageService).delete("https://bucket.s3.ap-southeast-1.amazonaws.com/avatars/current.png");
    }

    @Test
    void missingProfileIsRejected() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User profile was not found");
    }

    private User buildUser(UUID userId) {
        User user = new User(
                "farmer@example.com",
                "hash",
                UserStatus.ACTIVE,
                UserRole.FARMER);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private UserProfile buildProfile(User user) {
        UserProfile profile = new UserProfile(user, "Nguyen Van A", "0901234567");
        ReflectionTestUtils.setField(profile, "createdAt", LocalDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(profile, "updatedAt", LocalDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(profile, "farmAddress", "Ward 1, District 1");
        ReflectionTestUtils.setField(profile, "provinceCity", "Can Tho");
        ReflectionTestUtils.setField(profile, "dateOfBirth", LocalDate.of(1995, 5, 20));
        ReflectionTestUtils.setField(profile, "gender", UserGender.OTHER);
        ReflectionTestUtils.setField(profile, "bio", "Durian farmer");
        return profile;
    }
}
