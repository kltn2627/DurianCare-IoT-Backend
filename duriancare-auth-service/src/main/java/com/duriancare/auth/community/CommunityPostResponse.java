package com.duriancare.auth.community;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CommunityPostResponse(
        UUID id,
        CommunityAuthorResponse author,
        String topic,
        String content,
        CommunityPostVisibility visibility,
        CommunityPostStatus status,
        List<CommunityMediaResponse> media,
        List<String> tags,
        long reactionCount,
        long commentCount,
        long shareCount,
        CommunityReactionType myReaction,
        List<CommunityCommentResponse> comments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
