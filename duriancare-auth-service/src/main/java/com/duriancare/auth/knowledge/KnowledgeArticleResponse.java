package com.duriancare.auth.knowledge;

import com.duriancare.auth.domain.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record KnowledgeArticleResponse(
        UUID id,
        String title,
        String slug,
        String category,
        String author,
        UUID authorUserId,
        UserRole authorRole,
        String excerpt,
        String content,
        KnowledgeArticleStatus status,
        boolean featured,
        String coverImage,
        String readingTime,
        List<String> tags,
        String publishedAt,
        String updatedAt,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        UUID reviewedBy,
        String rejectionReason,
        long views) {
}

