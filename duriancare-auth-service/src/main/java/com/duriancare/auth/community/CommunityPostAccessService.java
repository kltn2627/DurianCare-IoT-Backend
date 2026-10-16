package com.duriancare.auth.community;

import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.UserConnectionRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CommunityPostAccessService {

    private final UserConnectionRepository connectionRepository;

    public CommunityPostAccessService(UserConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public Set<UUID> acceptedConnectionUserIds(User actor) {
        return connectionRepository.findAcceptedForUser(actor.getId())
                .stream()
                .map(connection -> connection.getOtherUserId(actor.getId()))
                .collect(Collectors.toSet());
    }

    public boolean canViewPost(User actor, CommunityPost post) {
        if (actor == null || post.getStatus() == CommunityPostStatus.HIDDEN) {
            return false;
        }
        if (post.getAuthor().getId().equals(actor.getId())) {
            return true;
        }
        if (post.getVisibility() == CommunityPostVisibility.PUBLIC) {
            return true;
        }
        if (post.getVisibility() != CommunityPostVisibility.CONNECTIONS) {
            return false;
        }
        UUID lowId = lowId(actor.getId(), post.getAuthor().getId());
        UUID highId = highId(actor.getId(), post.getAuthor().getId());
        return connectionRepository.findPair(lowId, highId)
                .filter(connection -> connection.getStatus() == UserConnectionStatus.ACCEPTED)
                .isPresent();
    }

    public void requireCanViewPost(User actor, CommunityPost post) {
        if (!canViewPost(actor, post)) {
            throw new ResourceNotFoundException("Community post was not found");
        }
    }

    private UUID lowId(UUID firstUserId, UUID secondUserId) {
        return firstUserId.compareTo(secondUserId) <= 0 ? firstUserId : secondUserId;
    }

    private UUID highId(UUID firstUserId, UUID secondUserId) {
        return firstUserId.compareTo(secondUserId) <= 0 ? secondUserId : firstUserId;
    }
}
