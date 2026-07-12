package com.duriancare.auth.bootstrap;

import com.duriancare.auth.config.AdminBootstrapProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserPreference;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.repository.UserPreferenceRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapInitializer(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            UserPreferenceRepository preferenceRepository,
            PasswordEncoder passwordEncoder,
            AdminBootstrapProperties properties) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.preferenceRepository = preferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        String email = normalize(properties.email());
        User admin = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createAdmin(email));
        ensureProfile(admin);
        ensurePreference(admin);
    }

    private User createAdmin(String email) {
        User admin = new User(
                email,
                passwordEncoder.encode(properties.password()),
                UserStatus.ACTIVE,
                UserRole.ADMIN);
        User savedAdmin = userRepository.save(admin);
        LOGGER.info("Seeded default admin account for {}", email);
        return savedAdmin;
    }

    private void ensureProfile(User admin) {
        profileRepository.findByUser_Id(admin.getId()).orElseGet(() ->
                profileRepository.save(new UserProfile(
                        admin,
                        properties.fullName().trim(),
                        normalizeNullable(properties.phoneNumber()))));
    }

    private void ensurePreference(User admin) {
        preferenceRepository.findByUser_Id(admin.getId()).orElseGet(() ->
                preferenceRepository.save(new UserPreference(admin, "vi", true, true)));
    }

    private String normalize(String value) {
        return StringUtils.trimWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
