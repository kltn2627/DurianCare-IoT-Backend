package com.duriancare.auth.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommunityCommentRequest(
        @NotBlank @Size(max = 1000) String content,
        UUID parentId) {
}
