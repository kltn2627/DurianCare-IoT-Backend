package com.duriancare.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.domain.UserConnectionSource;
import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.CreateConnectionRequest;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserConnection;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.event.publisher.ConnectionNotificationPublisher;
import com.duriancare.auth.exception.ConflictException;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.repository.UserConnectionRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserConnectionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private UserConnectionRepository connectionRepository;
    @Mock
    private ConnectionNotificationPublisher notificationPublisher;

    private UserConnectionService service;

    @BeforeEach
    void setUp() {
        service = new UserConnectionService(
                userRepository,
                profileRepository,
                connectionRepository,
                notificationPublisher);
    }

    @Test
    void farmerCanSendRequestToEngineer() {
        UUID farmerId = UUID.randomUUID();
        UUID engineerId = UUID.randomUUID();
        User farmer = user(farmerId, "farmer@example.com", UserRole.FARMER, UserStatus.ACTIVE);
        User engineer = user(engineerId, "engineer@example.com", UserRole.ENGINEER, UserStatus.ACTIVE);
        when(userRepository.findById(farmerId)).thenReturn(Optional.of(farmer));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
        when(connectionRepository.findByUserLowIdAndUserHighId(low(farmerId, engineerId), high(farmerId, engineerId)))
                .thenReturn(Optional.empty());
        when(connectionRepository.saveAndFlush(any(UserConnection.class))).thenAnswer(invocation -> {
            UserConnection connection = invocation.getArgument(0);
            ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
            return connection;
        });
        when(profileRepository.findByUser_Id(farmerId)).thenReturn(Optional.of(profile(farmer, "Nguyen Van A", "0901234567")));
        when(profileRepository.findByUser_Id(engineerId)).thenReturn(Optional.of(profile(engineer, "Ky su B", "0907654321")));
        when(connectionRepository.findPair(low(farmerId, engineerId), high(farmerId, engineerId))).thenReturn(Optional.empty());

        var response = service.createRequest(
                farmerId,
                new CreateConnectionRequest(engineerId, UserConnectionSource.PHONE_SEARCH));

        assertThat(response.status()).isEqualTo(UserConnectionStatus.PENDING);
        assertThat(response.user().id()).isEqualTo(engineerId);
        verify(notificationPublisher).publish(any());
    }

    @Test
    void notificationFailureDoesNotRollbackConnectionRequest() {
        UUID farmerId = UUID.randomUUID();
        UUID engineerId = UUID.randomUUID();
        User farmer = user(farmerId, "farmer@example.com", UserRole.FARMER, UserStatus.ACTIVE);
        User engineer = user(engineerId, "engineer@example.com", UserRole.ENGINEER, UserStatus.ACTIVE);
        when(userRepository.findById(farmerId)).thenReturn(Optional.of(farmer));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
        when(connectionRepository.findByUserLowIdAndUserHighId(low(farmerId, engineerId), high(farmerId, engineerId)))
                .thenReturn(Optional.empty());
        when(connectionRepository.saveAndFlush(any(UserConnection.class))).thenAnswer(invocation -> {
            UserConnection connection = invocation.getArgument(0);
            ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
            return connection;
        });
        when(profileRepository.findByUser_Id(farmerId)).thenReturn(Optional.of(profile(farmer, "Nguyen Van A", "0901234567")));
        when(profileRepository.findByUser_Id(engineerId)).thenReturn(Optional.of(profile(engineer, "Ky su B", "0907654321")));
        when(connectionRepository.findPair(low(farmerId, engineerId), high(farmerId, engineerId))).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("notification broker unavailable"))
                .when(notificationPublisher)
                .publish(any());

        var response = service.createRequest(
                farmerId,
                new CreateConnectionRequest(engineerId, UserConnectionSource.PHONE_SEARCH));

        assertThat(response.status()).isEqualTo(UserConnectionStatus.PENDING);
        assertThat(response.user().id()).isEqualTo(engineerId);
    }

    @Test
    void communityUsersUsesEmptyQueryWhenQueryIsBlank() {
        UUID engineerId = UUID.randomUUID();
        User engineer = user(engineerId, "engineer@example.com", UserRole.ENGINEER, UserStatus.ACTIVE);
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
        when(profileRepository.findCommunityUsers(
                eq(engineerId),
                eq(UserRole.FARMER),
                eq(UserStatus.ACTIVE),
                eq(""),
                any()))
                .thenReturn(new PageImpl<>(List.of()));

        var response = service.communityUsers(engineerId, null, 0, 24);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        UUID farmerId = UUID.randomUUID();
        UUID engineerId = UUID.randomUUID();
        User farmer = user(farmerId, "farmer@example.com", UserRole.FARMER, UserStatus.ACTIVE);
        User engineer = user(engineerId, "engineer@example.com", UserRole.ENGINEER, UserStatus.ACTIVE);
        UserConnection existing = new UserConnection(farmerId, engineerId, UserRole.FARMER, UserRole.ENGINEER, UserConnectionSource.COMMUNITY);
        when(userRepository.findById(farmerId)).thenReturn(Optional.of(farmer));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
        when(connectionRepository.findByUserLowIdAndUserHighId(low(farmerId, engineerId), high(farmerId, engineerId)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createRequest(
                farmerId,
                new CreateConnectionRequest(engineerId, UserConnectionSource.COMMUNITY)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void reciprocalPendingRequestIsAcceptedWhenReceiverConnectsBack() {
        UUID farmerId = UUID.randomUUID();
        UUID engineerId = UUID.randomUUID();
        User farmer = user(farmerId, "farmer@example.com", UserRole.FARMER, UserStatus.ACTIVE);
        User engineer = user(engineerId, "engineer@example.com", UserRole.ENGINEER, UserStatus.ACTIVE);
        UserConnection existing = new UserConnection(farmerId, engineerId, UserRole.FARMER, UserRole.ENGINEER, UserConnectionSource.PHONE_SEARCH);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));
        when(userRepository.findById(farmerId)).thenReturn(Optional.of(farmer));
        when(connectionRepository.findByUserLowIdAndUserHighId(low(farmerId, engineerId), high(farmerId, engineerId)))
                .thenReturn(Optional.of(existing));
        when(connectionRepository.saveAndFlush(existing)).thenReturn(existing);
        when(profileRepository.findByUser_Id(engineerId)).thenReturn(Optional.of(profile(engineer, "Ky su B", "0907654321")));
        when(profileRepository.findByUser_Id(farmerId)).thenReturn(Optional.of(profile(farmer, "Nguyen Van A", "0901234567")));
        when(connectionRepository.findPair(low(farmerId, engineerId), high(farmerId, engineerId))).thenReturn(Optional.of(existing));

        var response = service.createRequest(
                engineerId,
                new CreateConnectionRequest(farmerId, UserConnectionSource.PHONE_SEARCH));

        assertThat(response.status()).isEqualTo(UserConnectionStatus.ACCEPTED);
        assertThat(response.user().id()).isEqualTo(farmerId);
        verify(notificationPublisher).publish(any());
    }

    @Test
    void sameRoleConnectionIsRejected() {
        UUID firstFarmerId = UUID.randomUUID();
        UUID secondFarmerId = UUID.randomUUID();
        when(userRepository.findById(firstFarmerId))
                .thenReturn(Optional.of(user(firstFarmerId, "a@example.com", UserRole.FARMER, UserStatus.ACTIVE)));
        when(userRepository.findById(secondFarmerId))
                .thenReturn(Optional.of(user(secondFarmerId, "b@example.com", UserRole.FARMER, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.createRequest(
                firstFarmerId,
                new CreateConnectionRequest(secondFarmerId, UserConnectionSource.COMMUNITY)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("farmer and engineer");
    }

    @Test
    void onlyReceiverCanAcceptRequest() {
        UUID farmerId = UUID.randomUUID();
        UUID engineerId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserConnection connection = new UserConnection(farmerId, engineerId, UserRole.FARMER, UserRole.ENGINEER, UserConnectionSource.COMMUNITY);
        ReflectionTestUtils.setField(connection, "id", connectionId);
        when(userRepository.findById(farmerId))
                .thenReturn(Optional.of(user(farmerId, "farmer@example.com", UserRole.FARMER, UserStatus.ACTIVE)));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.accept(farmerId, connectionId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only the receiver");
    }

    private User user(UUID id, String email, UserRole role, UserStatus status) {
        User user = new User(email, "hash", status, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfile profile(User user, String fullName, String phoneNumber) {
        return new UserProfile(user, fullName, phoneNumber);
    }

    private UUID low(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private UUID high(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? second : first;
    }
}
