package com.duriancare.auth.service;

import com.duriancare.auth.domain.UserConnectionSource;
import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.dto.ConnectionPageResponse;
import com.duriancare.auth.dto.ConnectionRelationStatus;
import com.duriancare.auth.dto.ConnectionUserSummary;
import com.duriancare.auth.dto.CreateConnectionRequest;
import com.duriancare.auth.dto.UserConnectionResponse;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserConnection;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.event.ConnectionNotificationEvent;
import com.duriancare.auth.event.publisher.ConnectionNotificationPublisher;
import com.duriancare.auth.exception.ConflictException;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.UserConnectionRepository;
import com.duriancare.auth.repository.UserProfileRepository;
import com.duriancare.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserConnectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserConnectionService.class);

    private static final Collection<UserConnectionStatus> CONNECTED_STATUSES =
            List.of(UserConnectionStatus.ACCEPTED);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserConnectionRepository connectionRepository;
    private final ConnectionNotificationPublisher notificationPublisher;

    public UserConnectionService(
            UserRepository userRepository,
            UserProfileRepository profileRepository,
            UserConnectionRepository connectionRepository,
            ConnectionNotificationPublisher notificationPublisher) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.connectionRepository = connectionRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional(readOnly = true)
    public ConnectionUserSummary searchByPhone(UUID currentUserId, String phoneNumber) {
        User currentUser = activeUser(currentUserId);
        UserRole counterpartRole = counterpartRole(currentUser.getRole());
        String normalizedPhone = normalizePhone(phoneNumber);
        if (!StringUtils.hasText(normalizedPhone)) {
            throw new InvalidRequestException("Phone number is required");
        }
        return profileRepository
                .findCounterpartsByNormalizedPhone(
                        currentUserId,
                        counterpartRole,
                        UserStatus.ACTIVE,
                        normalizedPhone.toLowerCase(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(profile -> toUserSummary(
                        profile,
                        relation(currentUserId, profile.getUserId()),
                        findConnectionId(currentUserId, profile.getUserId()),
                        false))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ConnectionPageResponse<ConnectionUserSummary> communityUsers(
            UUID currentUserId,
            String query,
            int page,
            int size) {
        User currentUser = activeUser(currentUserId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : "";
        Page<UserProfile> result = profileRepository
                .findCommunityUsers(
                        currentUserId,
                        counterpartRole(currentUser.getRole()),
                        UserStatus.ACTIVE,
                        normalizedQuery,
                        pageable);
        List<ConnectionUserSummary> items = result
                .stream()
                .map(profile -> toUserSummary(
                        profile,
                        relation(currentUserId, profile.getUserId()),
                        findConnectionId(currentUserId, profile.getUserId()),
                        true))
                .toList();
        return new ConnectionPageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public UserConnectionResponse createRequest(UUID currentUserId, CreateConnectionRequest request) {
        User requester = activeUser(currentUserId);
        User receiver = activeUser(request.receiverId());
        validateConnectablePair(requester, receiver);
        UUID lowId = lowId(requester.getId(), receiver.getId());
        UUID highId = highId(requester.getId(), receiver.getId());
        Optional<UserConnection> existingConnection =
                connectionRepository.findByUserLowIdAndUserHighId(lowId, highId);
        if (existingConnection.isPresent()
                && existingConnection.get().getStatus() == UserConnectionStatus.PENDING
                && existingConnection.get().getReceiverId().equals(requester.getId())) {
            UserConnection reciprocalRequest = existingConnection.get();
            reciprocalRequest.accept();
            UserConnection saved = connectionRepository.saveAndFlush(reciprocalRequest);
            publish(
                    "CONNECTION_REQUEST_ACCEPTED",
                    saved,
                    saved.getRequesterId(),
                    requester,
                    "Lời mời kết nối đã được chấp nhận",
                    displayName(requester) + " đã chấp nhận lời mời kết nối.");
            return toConnectionResponse(saved, currentUserId);
        }
        UserConnection connection = existingConnection
                .map(existing -> reuseOrReject(existing, requester, receiver, request.source()))
                .orElseGet(() -> new UserConnection(
                        requester.getId(),
                        receiver.getId(),
                        requester.getRole(),
                        receiver.getRole(),
                        request.source()));
        UserConnection saved = connectionRepository.saveAndFlush(connection);
        publish(
                "CONNECTION_REQUEST_RECEIVED",
                saved,
                receiver.getId(),
                requester,
                "Lời mời kết nối mới",
                displayName(requester) + " muốn kết nối với bạn.");
        return toConnectionResponse(saved, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<UserConnectionResponse> incoming(UUID currentUserId) {
        activeUser(currentUserId);
        return connectionRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(currentUserId, UserConnectionStatus.PENDING)
                .stream()
                .map(connection -> toConnectionResponse(connection, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserConnectionResponse> outgoing(UUID currentUserId) {
        activeUser(currentUserId);
        return connectionRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(currentUserId, UserConnectionStatus.PENDING)
                .stream()
                .map(connection -> toConnectionResponse(connection, currentUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConnectionPageResponse<UserConnectionResponse> connections(UUID currentUserId, int page, int size) {
        activeUser(currentUserId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<UserConnection> result = connectionRepository.findForUser(currentUserId, CONNECTED_STATUSES, pageable);
        return new ConnectionPageResponse<>(
                result.getContent().stream().map(connection -> toConnectionResponse(connection, currentUserId)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public UserConnectionResponse accept(UUID currentUserId, UUID connectionId) {
        User actor = activeUser(currentUserId);
        UserConnection connection = pendingConnection(connectionId);
        if (!connection.getReceiverId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the receiver can accept this request");
        }
        connection.accept();
        UserConnection saved = connectionRepository.save(connection);
        publish(
                "CONNECTION_REQUEST_ACCEPTED",
                saved,
                saved.getRequesterId(),
                actor,
                "Lời mời kết nối đã được chấp nhận",
                displayName(actor) + " đã chấp nhận lời mời kết nối.");
        return toConnectionResponse(saved, currentUserId);
    }

    @Transactional
    public UserConnectionResponse reject(UUID currentUserId, UUID connectionId) {
        User actor = activeUser(currentUserId);
        UserConnection connection = pendingConnection(connectionId);
        if (!connection.getReceiverId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the receiver can reject this request");
        }
        connection.reject();
        UserConnection saved = connectionRepository.save(connection);
        publish(
                "CONNECTION_REQUEST_REJECTED",
                saved,
                saved.getRequesterId(),
                actor,
                "Lời mời kết nối đã bị từ chối",
                displayName(actor) + " đã từ chối lời mời kết nối.");
        return toConnectionResponse(saved, currentUserId);
    }

    @Transactional
    public UserConnectionResponse cancel(UUID currentUserId, UUID connectionId) {
        User actor = activeUser(currentUserId);
        UserConnection connection = pendingConnection(connectionId);
        if (!connection.getRequesterId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can cancel this request");
        }
        connection.cancel();
        UserConnection saved = connectionRepository.save(connection);
        publish(
                "CONNECTION_REQUEST_CANCELLED",
                saved,
                saved.getReceiverId(),
                actor,
                "Lời mời kết nối đã bị hủy",
                displayName(actor) + " đã hủy lời mời kết nối.");
        return toConnectionResponse(saved, currentUserId);
    }

    @Transactional
    public UserConnectionResponse disconnect(UUID currentUserId, UUID connectionId) {
        User actor = activeUser(currentUserId);
        UserConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection was not found"));
        if (!connection.getRequesterId().equals(currentUserId) && !connection.getReceiverId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only connected users can disconnect");
        }
        if (connection.getStatus() != UserConnectionStatus.ACCEPTED) {
            throw new InvalidRequestException("Only accepted connections can be disconnected");
        }
        connection.disconnect();
        UserConnection saved = connectionRepository.save(connection);
        publish(
                "CONNECTION_DISCONNECTED",
                saved,
                saved.getOtherUserId(currentUserId),
                actor,
                "Kết nối đã bị hủy",
                displayName(actor) + " đã hủy kết nối.");
        return toConnectionResponse(saved, currentUserId);
    }

    private UserConnection pendingConnection(UUID connectionId) {
        UserConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection request was not found"));
        if (connection.getStatus() != UserConnectionStatus.PENDING) {
            throw new InvalidRequestException("Connection request is no longer pending");
        }
        return connection;
    }

    private UserConnection reuseOrReject(
            UserConnection existing,
            User requester,
            User receiver,
            UserConnectionSource source) {
        if (existing.getStatus() == UserConnectionStatus.PENDING) {
            throw new ConflictException("A connection request is already pending between these users");
        }
        if (existing.getStatus() == UserConnectionStatus.ACCEPTED) {
            throw new ConflictException("These users are already connected");
        }
        if (existing.getStatus() == UserConnectionStatus.BLOCKED) {
            throw new ConflictException("This connection is blocked");
        }
        existing.restart(requester.getId(), receiver.getId(), requester.getRole(), receiver.getRole(), source);
        return existing;
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRequestException("User account is not active");
        }
        return user;
    }

    private void validateConnectablePair(User requester, User receiver) {
        if (requester.getId().equals(receiver.getId())) {
            throw new InvalidRequestException("Users cannot connect with themselves");
        }
        if (counterpartRole(requester.getRole()) != receiver.getRole()) {
            throw new InvalidRequestException("Only farmer and engineer accounts can connect");
        }
    }

    private UserRole counterpartRole(UserRole role) {
        if (role == UserRole.FARMER) {
            return UserRole.ENGINEER;
        }
        if (role == UserRole.ENGINEER) {
            return UserRole.FARMER;
        }
        throw new InvalidRequestException("Only farmer and engineer accounts can use connections");
    }

    private ConnectionRelationStatus relation(UUID currentUserId, UUID otherUserId) {
        Optional<UserConnection> connection = connectionRepository.findPair(lowId(currentUserId, otherUserId), highId(currentUserId, otherUserId));
        if (connection.isEmpty()) {
            return ConnectionRelationStatus.NONE;
        }
        UserConnection value = connection.get();
        if (value.getStatus() == UserConnectionStatus.ACCEPTED) {
            return ConnectionRelationStatus.CONNECTED;
        }
        if (value.getStatus() == UserConnectionStatus.BLOCKED) {
            return ConnectionRelationStatus.BLOCKED;
        }
        if (value.getStatus() == UserConnectionStatus.PENDING) {
            return value.getRequesterId().equals(currentUserId)
                    ? ConnectionRelationStatus.REQUEST_SENT
                    : ConnectionRelationStatus.REQUEST_RECEIVED;
        }
        return ConnectionRelationStatus.NONE;
    }

    private UserConnectionResponse toConnectionResponse(UserConnection connection, UUID currentUserId) {
        UUID otherUserId = connection.getOtherUserId(currentUserId);
        UserProfile profile = profileRepository.findByUser_Id(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Connected user profile was not found"));
        return new UserConnectionResponse(
                connection.getId(),
                connection.getRequesterId(),
                connection.getReceiverId(),
                connection.getRequesterRole(),
                connection.getReceiverRole(),
                connection.getStatus(),
                connection.getSource(),
                toUserSummary(profile, relation(currentUserId, otherUserId), connection.getId(), false),
                connection.getCreatedAt(),
                connection.getUpdatedAt(),
                connection.getRespondedAt(),
                connection.getDisconnectedAt());
    }

    private ConnectionUserSummary toUserSummary(
            UserProfile profile,
            ConnectionRelationStatus relationStatus,
            UUID connectionId,
            boolean maskPhone) {
        User user = userRepository.findById(profile.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        return new ConnectionUserSummary(
                user.getId(),
                profile.getFullName(),
                maskPhone ? maskPhone(profile.getPhoneNumber()) : profile.getPhoneNumber(),
                profile.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                profile.getProvinceCity(),
                null,
                relationStatus,
                connectionId);
    }

    private UUID findConnectionId(UUID currentUserId, UUID otherUserId) {
        return connectionRepository.findPair(lowId(currentUserId, otherUserId), highId(currentUserId, otherUserId))
                .map(UserConnection::getId)
                .orElse(null);
    }

    private String displayName(User user) {
        return profileRepository.findByUser_Id(user.getId())
                .map(UserProfile::getFullName)
                .filter(StringUtils::hasText)
                .orElse(user.getEmail());
    }

    private void publish(
            String eventType,
            UserConnection connection,
            UUID receiverId,
            User actor,
            String title,
            String message) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("actorId", actor.getId().toString());
            metadata.put("actorName", displayName(actor));
            if (connection.getId() != null) {
                metadata.put("connectionId", connection.getId().toString());
            }
            metadata.put("deepLink", "/dashboard/community");

            notificationPublisher.publish(new ConnectionNotificationEvent(
                    UUID.randomUUID(),
                    eventType,
                    receiverId.toString(),
                    title,
                    message,
                    "EXPERT",
                    metadata,
                    Instant.now()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to prepare connection notification {} for receiver {}", eventType, receiverId, exception);
        }
    }

    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.replaceAll("[\\s().-]", "").trim();
    }

    private String maskPhone(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber) || phoneNumber.length() < 6) {
            return phoneNumber;
        }
        return phoneNumber.substring(0, 3) + "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }

    private UUID lowId(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private UUID highId(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? second : first;
    }
}
