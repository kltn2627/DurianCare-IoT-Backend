package com.duriancare.auth.community;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CommunityCommentResponse(
        UUID id,
        CommunityAuthorResponse author,
        String content,
        UUID parentId,
        List<CommunityCommentResponse> replies,
        LocalDateTime createdAt) {
}
