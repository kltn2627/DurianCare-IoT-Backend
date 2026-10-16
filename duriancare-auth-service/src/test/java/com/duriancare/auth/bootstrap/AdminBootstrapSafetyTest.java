package com.duriancare.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.duriancare.auth.config.AdminBootstrapProperties;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserPreference;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.repository.UserPreferenceRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/** In-memory fixtures only: never starts bootstrap against an actual account database. */
class AdminBootstrapSafetyTest {
    final UserRepository users = mock(UserRepository.class);
    final UserProfileRepository profiles = mock(UserProfileRepository.class);
    final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test void disabledBootstrapDoesNotTouchAccounts() {
        new AdminBootstrapInitializer(users, profiles, preferences, encoder,
                new AdminBootstrapProperties(false, null, null, null, null)).run(null);
        verifyNoInteractions(users, profiles, preferences, encoder);
    }

    @Test void existingAccountIsNotRenamedResetOrRecreatedByBootstrap() {
        User existing = new User("fixture@@example.test", "fixture-existing-hash", UserStatus.ACTIVE, UserRole.ADMIN);
        when(users.findByEmailIgnoreCase("fixture@@example.test")).thenReturn(Optional.of(existing));
        when(profiles.findByUser_Id(null)).thenReturn(Optional.of(mock(UserProfile.class)));
        when(preferences.findByUser_Id(null)).thenReturn(Optional.of(mock(UserPreference.class)));
        new AdminBootstrapInitializer(users, profiles, preferences, encoder,
                new AdminBootstrapProperties(true, " FIXTURE@@EXAMPLE.TEST ", "fixture-only-config", "Fixture", null)).run(null);
        assertThat(existing.getEmail()).isEqualTo("fixture@@example.test");
        assertThat(existing.getPasswordHash()).isEqualTo("fixture-existing-hash");
        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
        verify(profiles, never()).save(any());
        verify(preferences, never()).save(any());
    }

    @Test void missingAccountCreationNormalizesValidEmailAndSetsAdminRoleInFixtureOnly() {
        when(users.findByEmailIgnoreCase("fixture@example.test")).thenReturn(Optional.empty());
        when(encoder.encode("fixture-only-config")).thenReturn("fixture-encoded-hash");
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        new AdminBootstrapInitializer(users, profiles, preferences, encoder,
                new AdminBootstrapProperties(true, " FIXTURE@EXAMPLE.TEST ", "fixture-only-config", "Fixture", null)).run(null);
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("fixture@example.test");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("fixture-encoded-hash");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
