package com.duriancare.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.config.AuthProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.entity.OtpVerification;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserPreference;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.event.UserRegisteredEvent;
import com.duriancare.auth.event.publisher.AuthEventPublisher;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.repository.OtpVerificationRepository;
import com.duriancare.auth.repository.UserPreferenceRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import com.duriancare.auth.security.JwtService;
import com.duriancare.auth.security.RevokedTokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private UserPreferenceRepository preferenceRepository;
    @Mock
    private OtpVerificationRepository otpRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RevokedTokenService revokedTokenService;
    @Mock
    private AuthEventPublisher eventPublisher;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                profileRepository,
                preferenceRepository,
                otpRepository,
                passwordEncoder,
                jwtService,
                revokedTokenService,
                eventPublisher,
                new AuthProperties(Duration.ofMinutes(5), "duriancare.auth.events"));
    }

    @Test
    void registerCreatesPendingUserAndPublishesSixDigitOtp() {
        when(userRepository.existsByEmailIgnoreCase("farmer@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "hash-" + invocation.getArgument(0, String.class));

        authService.register(new RegisterRequest(
                " Farmer@Example.com ",
                "Password123",
                "Nguyen Van A",
                "0901234567",
                null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<OtpVerification> otpCaptor = ArgumentCaptor.forClass(OtpVerification.class);
        ArgumentCaptor<UserRegisteredEvent> eventCaptor =
                ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(userRepository).save(userCaptor.capture());
        verify(otpRepository).save(otpCaptor.capture());
        verify(eventPublisher).publish(eventCaptor.capture());

        assertThat(userCaptor.getValue().getEmail()).isEqualTo("farmer@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.FARMER);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(otpCaptor.getValue().getPendingFullName()).isEqualTo("Nguyen Van A");
        assertThat(eventCaptor.getValue().otpCode()).matches("\\d{6}");
        assertThat(eventCaptor.getValue().expiresInMinutes()).isEqualTo(5);
    }

    @Test
    void registerRejectsPrivilegedRole() {
        when(userRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "admin@example.com",
                "Password123",
                "Admin",
                null,
                UserRole.ADMIN)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("FARMER or EXPERT");
    }

    @Test
    void verifyOtpActivatesUserAndCreatesProfileAndPreferences() {
        User user = new User(
                "farmer@example.com",
                "password-hash",
                UserStatus.PENDING_VERIFICATION,
                UserRole.FARMER);
        OtpVerification verification = new OtpVerification(
                "farmer@example.com",
                "otp-hash",
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                "Nguyen Van A",
                "0901234567");
        when(userRepository.findByEmailIgnoreCase("farmer@example.com"))
                .thenReturn(Optional.of(user));
        when(otpRepository.findByEmailIgnoreCase("farmer@example.com"))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "otp-hash")).thenReturn(true);

        authService.verifyRegistrationOtp(new VerifyOtpRequest(
                "farmer@example.com",
                "123456"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(verification.isVerified()).isTrue();
        verify(profileRepository).save(any(UserProfile.class));
        verify(preferenceRepository).save(any(UserPreference.class));
    }
}
