package com.duriancare.auth.service.impl;

import com.duriancare.auth.config.AuthProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.UserProfileResponse;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.entity.OtpVerification;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserPreference;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.event.UserRegisteredEvent;
import com.duriancare.auth.event.publisher.AuthEventPublisher;
import com.duriancare.auth.exception.AuthenticationFailedException;
import com.duriancare.auth.exception.ConflictException;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.OtpVerificationRepository;
import com.duriancare.auth.repository.UserPreferenceRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import com.duriancare.auth.security.IssuedToken;
import com.duriancare.auth.security.JwtService;
import com.duriancare.auth.security.RevokedTokenService;
import com.duriancare.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final OtpVerificationRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevokedTokenService revokedTokenService;
    private final AuthEventPublisher eventPublisher;
    private final AuthProperties authProperties;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            UserPreferenceRepository preferenceRepository,
            OtpVerificationRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RevokedTokenService revokedTokenService,
            AuthEventPublisher eventPublisher,
            AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.preferenceRepository = preferenceRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.revokedTokenService = revokedTokenService;
        this.eventPublisher = eventPublisher;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        UserRole role = request.role() == null ? UserRole.FARMER : request.role();
        if (role != UserRole.FARMER && role != UserRole.EXPERT) {
            throw new InvalidRequestException("Public registration only supports FARMER or EXPERT roles");
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                UserStatus.PENDING_VERIFICATION,
                role);
        userRepository.save(user);

        String otp = createOtp();
        OtpVerification verification = new OtpVerification(
                email,
                passwordEncoder.encode(otp),
                expiresAt(),
                request.fullName().trim(),
                normalizeNullable(request.phoneNumber()));
        otpRepository.save(verification);
        eventPublisher.publish(UserRegisteredEvent.registration(
                email,
                request.fullName().trim(),
                otp,
                authProperties.otpTtl().toMinutes()));
    }

    @Override
    @Transactional
    public void resendRegistrationOtp(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = findUserByEmail(email);
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new InvalidRequestException("Only pending accounts can request another OTP");
        }
        OtpVerification verification = otpRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Registration verification was not found"));
        String otp = createOtp();
        verification.renew(passwordEncoder.encode(otp), expiresAt());
        otpRepository.save(verification);
        eventPublisher.publish(UserRegisteredEvent.otpResent(
                email,
                verification.getPendingFullName(),
                otp,
                authProperties.otpTtl().toMinutes()));
    }

    @Override
    @Transactional
    public void verifyRegistrationOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        User user = findUserByEmail(email);
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new InvalidRequestException("Account is not pending verification");
        }
        OtpVerification verification = otpRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Registration verification was not found"));
        if (verification.isVerified()) {
            throw new InvalidRequestException("OTP has already been used");
        }
        if (verification.getExpiredAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidRequestException("OTP has expired");
        }
        if (!passwordEncoder.matches(request.otpCode(), verification.getOtpCode())) {
            throw new InvalidRequestException("OTP is incorrect");
        }

        user.setStatus(UserStatus.ACTIVE);
        verification.markVerified();
        profileRepository.save(new UserProfile(
                user,
                verification.getPendingFullName(),
                verification.getPendingPhoneNumber()));
        preferenceRepository.save(new UserPreference(user, "vi", true, true));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new AuthenticationFailedException("Email or password is incorrect"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Email or password is incorrect");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("Account is not active");
        }

        IssuedToken accessToken = jwtService.generateAccessToken(user);
        IssuedToken refreshToken = jwtService.generateRefreshToken(user);
        UserProfile profile = profileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User profile was not found"));
        return new AuthenticationResponse(
                accessToken.value(),
                refreshToken.value(),
                "Bearer",
                secondsUntil(accessToken.expiresAt()),
                user.getId(),
                user.getEmail(),
                user.getRole(),
                new UserProfileResponse(
                        profile.getFullName(),
                        profile.getPhoneNumber(),
                        profile.getFarmAddress(),
                        profile.getAvatarUrl()));
    }

    @Override
    public void logout(String accessToken) {
        Claims claims = jwtService.parseAccessToken(accessToken);
        revokedTokenService.revoke(claims);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtService.parseRefreshToken(request.refreshToken());
        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthenticationFailedException("Refresh token subject is invalid");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Refresh token user was not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("Account is not active");
        }
        IssuedToken accessToken = jwtService.generateAccessToken(user);
        return new AccessTokenResponse(
                accessToken.value(),
                "Bearer",
                secondsUntil(accessToken.expiresAt()));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
    }

    private String createOtp() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private LocalDateTime expiresAt() {
        return LocalDateTime.now(ZoneOffset.UTC).plus(authProperties.otpTtl());
    }

    private long secondsUntil(Instant expiration) {
        return Math.max(0, Duration.between(Instant.now(), expiration).toSeconds());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
