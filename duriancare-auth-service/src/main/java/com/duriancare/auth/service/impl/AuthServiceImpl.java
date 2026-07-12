package com.duriancare.auth.service.impl;

import com.duriancare.auth.config.AuthProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.AccessTokenResponse;
import com.duriancare.auth.dto.AuthenticationResponse;
import com.duriancare.auth.dto.EngineerApplicationDetailResponse;
import com.duriancare.auth.dto.EngineerApplicationDocumentResponse;
import com.duriancare.auth.dto.EngineerApplicationSummaryResponse;
import com.duriancare.auth.dto.EngineerRegistrationRequest;
import com.duriancare.auth.dto.LoginRequest;
import com.duriancare.auth.dto.RefreshTokenRequest;
import com.duriancare.auth.dto.RegisterRequest;
import com.duriancare.auth.dto.ReviewEngineerApplicationRequest;
import com.duriancare.auth.dto.UserProfileResponse;
import com.duriancare.auth.dto.VerifyOtpRequest;
import com.duriancare.auth.domain.EngineerApplicationStatus;
import com.duriancare.auth.entity.OtpVerification;
import com.duriancare.auth.entity.EngineerApplication;
import com.duriancare.auth.entity.EngineerApplicationDocument;
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
import com.duriancare.auth.repository.EngineerApplicationDocumentRepository;
import com.duriancare.auth.repository.EngineerApplicationRepository;
import com.duriancare.auth.repository.UserPreferenceRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import com.duriancare.auth.security.IssuedToken;
import com.duriancare.auth.security.JwtService;
import com.duriancare.auth.security.RefreshTokenSessionService;
import com.duriancare.auth.security.RevokedTokenService;
import com.duriancare.auth.service.AuthService;
import com.duriancare.auth.service.EngineerDocumentStorageException;
import com.duriancare.auth.service.EngineerDocumentStorageService;
import com.duriancare.auth.service.StoredEngineerDocument;
import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final OtpVerificationRepository otpRepository;
    private final EngineerApplicationRepository engineerApplicationRepository;
    private final EngineerApplicationDocumentRepository engineerApplicationDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final RevokedTokenService revokedTokenService;
    private final AuthEventPublisher eventPublisher;
    private final EngineerDocumentStorageService engineerDocumentStorageService;
    private final AuthProperties authProperties;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            UserPreferenceRepository preferenceRepository,
            OtpVerificationRepository otpRepository,
            EngineerApplicationRepository engineerApplicationRepository,
            EngineerApplicationDocumentRepository engineerApplicationDocumentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenSessionService refreshTokenSessionService,
            RevokedTokenService revokedTokenService,
            AuthEventPublisher eventPublisher,
            EngineerDocumentStorageService engineerDocumentStorageService,
            AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.preferenceRepository = preferenceRepository;
        this.otpRepository = otpRepository;
        this.engineerApplicationRepository = engineerApplicationRepository;
        this.engineerApplicationDocumentRepository = engineerApplicationDocumentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.revokedTokenService = revokedTokenService;
        this.eventPublisher = eventPublisher;
        this.engineerDocumentStorageService = engineerDocumentStorageService;
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
    public void registerEngineer(EngineerRegistrationRequest request, List<MultipartFile> qualificationFiles) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                UserStatus.PENDING_VERIFICATION,
                UserRole.ENGINEER);
        userRepository.save(user);

        UserProfile profile = new UserProfile(
                user,
                request.fullName().trim(),
                normalizeNullable(request.phoneNumber()));
        profileRepository.save(profile);
        preferenceRepository.save(new UserPreference(user, "vi", true, true));

        EngineerApplication application = new EngineerApplication(
                user,
                request.workplace().trim(),
                request.specialization().trim(),
                request.yearsExperience(),
                request.biography().trim());
        engineerApplicationRepository.save(application);

        List<StoredEngineerDocument> storedDocuments = List.of();
        try {
            storedDocuments = engineerDocumentStorageService.upload(user.getId(), qualificationFiles);
            for (StoredEngineerDocument storedDocument : storedDocuments) {
                engineerApplicationDocumentRepository.save(new EngineerApplicationDocument(
                        application,
                        storedDocument.fileName(),
                        storedDocument.contentType(),
                        storedDocument.fileSize(),
                        storedDocument.objectKey(),
                        storedDocument.documentUrl()));
            }
        } catch (RuntimeException exception) {
            for (StoredEngineerDocument storedDocument : storedDocuments) {
                try {
                    engineerDocumentStorageService.delete(storedDocument.documentUrl());
                } catch (RuntimeException ignored) {
                    // best-effort cleanup
                }
            }
            throw exception;
        }

        try {
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
        } catch (RuntimeException exception) {
            for (StoredEngineerDocument storedDocument : storedDocuments) {
                try {
                    engineerDocumentStorageService.delete(storedDocument.documentUrl());
                } catch (RuntimeException ignored) {
                    // best-effort cleanup
                }
            }
            throw exception;
        }
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
        LocalDateTime nextAllowedAt = verification.getLastSentAt()
                .plus(authProperties.otpResendCooldown());
        if (nextAllowedAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidRequestException("Please wait before requesting another OTP");
        }
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
    @Transactional(noRollbackFor = InvalidRequestException.class)
    public UserStatus verifyRegistrationOtp(VerifyOtpRequest request) {
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
        if (verification.getFailedAttempts() >= authProperties.otpMaxAttempts()) {
            throw new InvalidRequestException("Too many incorrect OTP attempts. Request a new code.");
        }
        if (!passwordEncoder.matches(request.otpCode(), verification.getOtpCode())) {
            verification.recordFailedAttempt();
            otpRepository.save(verification);
            int attemptsRemaining = authProperties.otpMaxAttempts() - verification.getFailedAttempts();
            throw new InvalidRequestException(
                    attemptsRemaining > 0
                            ? "OTP is incorrect. Attempts remaining: " + attemptsRemaining
                            : "Too many incorrect OTP attempts. Request a new code.");
        }

        UserStatus verifiedStatus = user.getRole() == UserRole.EXPERT
                || user.getRole() == UserRole.ENGINEER
                ? UserStatus.PENDING_APPROVAL
                : UserStatus.ACTIVE;
        user.setStatus(verifiedStatus);
        verification.markVerified();
        ensureProfileExists(user, verification.getPendingFullName(), verification.getPendingPhoneNumber());
        ensurePreferenceExists(user);
        return verifiedStatus;
    }

    @Override
    @Transactional
    public void approveExpert(UUID userId) {
        approveEngineerByUserId(userId, null);
    }

    @Override
    @Transactional
    public void approveExpert(UUID userId, UUID reviewerUserId) {
        approveEngineerByUserId(userId, reviewerUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EngineerApplicationSummaryResponse> listEngineerApplications(EngineerApplicationStatus status) {
        List<EngineerApplication> applications = status == null
                ? engineerApplicationRepository.findAll()
                : engineerApplicationRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return applications.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EngineerApplicationDetailResponse getEngineerApplication(UUID applicationId) {
        EngineerApplication application = loadApplication(applicationId);
        List<EngineerApplicationDocumentResponse> documents = engineerApplicationDocumentRepository
                .findAllByApplication_IdOrderByCreatedAtAsc(applicationId)
                .stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());
        return toDetailResponse(application, documents);
    }

    @Override
    @Transactional
    public void approveEngineerApplication(UUID applicationId) {
        approveEngineerApplication(applicationId, null);
    }

    @Override
    @Transactional
    public void approveEngineerApplication(UUID applicationId, UUID reviewerUserId) {
        EngineerApplication application = engineerApplicationRepository.findByIdAndStatus(
                applicationId,
                EngineerApplicationStatus.PENDING_REVIEW)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer application was not found"));
        approveApplication(application, reviewerUserId);
    }

    @Override
    @Transactional
    public void rejectEngineerApplication(UUID applicationId, ReviewEngineerApplicationRequest request) {
        rejectEngineerApplication(applicationId, request, null);
    }

    @Override
    @Transactional
    public void rejectEngineerApplication(UUID applicationId, ReviewEngineerApplicationRequest request, UUID reviewerUserId) {
        EngineerApplication application = engineerApplicationRepository.findByIdAndStatus(
                applicationId,
                EngineerApplicationStatus.PENDING_REVIEW)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer application was not found"));
        rejectApplication(application, request == null ? null : request.rejectionReason(), reviewerUserId);
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
        refreshTokenSessionService.register(user.getId(), refreshToken);
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
                user.getStatus(),
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
        refreshTokenSessionService.revokeAll(parseUserId(claims));
    }

    @Override
    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtService.parseRefreshToken(request.refreshToken());
        UUID userId = parseUserId(claims);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Refresh token user was not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException("Account is not active");
        }
        refreshTokenSessionService.consume(userId, claims.getId());
        IssuedToken accessToken = jwtService.generateAccessToken(user);
        IssuedToken refreshToken = jwtService.generateRefreshToken(user);
        refreshTokenSessionService.register(userId, refreshToken);
        return new AccessTokenResponse(
                accessToken.value(),
                refreshToken.value(),
                "Bearer",
                secondsUntil(accessToken.expiresAt()),
                secondsUntil(refreshToken.expiresAt()));
    }

    private void approveEngineerByUserId(UUID userId, UUID reviewerUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        if (user.getRole() != UserRole.EXPERT && user.getRole() != UserRole.ENGINEER) {
            throw new InvalidRequestException("User is not an engineer awaiting approval");
        }
        engineerApplicationRepository.findByUser_Id(userId)
                .ifPresentOrElse(
                        application -> approveApplication(application, reviewerUserId),
                        () -> user.setStatus(UserStatus.ACTIVE));
    }

    private void approveApplication(EngineerApplication application, UUID reviewerUserId) {
        application.setStatus(EngineerApplicationStatus.APPROVED);
        application.setRejectionReason(null);
        application.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (reviewerUserId != null) {
            User reviewer = userRepository.findById(reviewerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reviewer was not found"));
            application.setReviewedBy(reviewer);
        }
        application.getUser().setStatus(UserStatus.ACTIVE);
    }

    private void rejectApplication(EngineerApplication application, String rejectionReason, UUID reviewerUserId) {
        application.setStatus(EngineerApplicationStatus.REJECTED);
        application.setRejectionReason(normalizeNullable(rejectionReason));
        application.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (reviewerUserId != null) {
            User reviewer = userRepository.findById(reviewerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reviewer was not found"));
            application.setReviewedBy(reviewer);
        }
        application.getUser().setStatus(UserStatus.BLOCKED);
    }

    private EngineerApplication loadApplication(UUID applicationId) {
        return engineerApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer application was not found"));
    }

    private EngineerApplicationSummaryResponse toSummaryResponse(EngineerApplication application) {
        return new EngineerApplicationSummaryResponse(
                application.getId(),
                application.getUser().getId(),
                application.getUser().getEmail(),
                application.getUser().getProfile() == null
                        ? application.getUser().getEmail()
                        : application.getUser().getProfile().getFullName(),
                application.getWorkplace(),
                application.getSpecialization(),
                application.getYearsExperience(),
                application.getStatus(),
                application.getReviewedAt(),
                application.getCreatedAt());
    }

    private EngineerApplicationDetailResponse toDetailResponse(
            EngineerApplication application,
            List<EngineerApplicationDocumentResponse> documents) {
        return new EngineerApplicationDetailResponse(
                application.getId(),
                application.getUser().getId(),
                application.getUser().getEmail(),
                application.getUser().getProfile() == null
                        ? application.getUser().getEmail()
                        : application.getUser().getProfile().getFullName(),
                application.getWorkplace(),
                application.getSpecialization(),
                application.getYearsExperience(),
                application.getBiography(),
                application.getStatus(),
                application.getRejectionReason(),
                application.getReviewedBy() == null ? null : application.getReviewedBy().getId(),
                application.getReviewedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                documents);
    }

    private EngineerApplicationDocumentResponse toDocumentResponse(EngineerApplicationDocument document) {
        return new EngineerApplicationDocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getDocumentUrl(),
                document.getCreatedAt());
    }

    private void ensureProfileExists(User user, String fullName, String phoneNumber) {
        if (profileRepository.findByUser_Id(user.getId()).isEmpty()) {
            profileRepository.save(new UserProfile(user, fullName, phoneNumber));
        }
    }

    private void ensurePreferenceExists(User user) {
        if (preferenceRepository.findByUser_Id(user.getId()).isEmpty()) {
            preferenceRepository.save(new UserPreference(user, "vi", true, true));
        }
    }

    private UUID parseUserId(Claims claims) {
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthenticationFailedException("Token subject is invalid");
        }
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
