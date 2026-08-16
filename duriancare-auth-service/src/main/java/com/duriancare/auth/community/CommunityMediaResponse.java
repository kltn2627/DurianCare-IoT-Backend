package com.duriancare.auth.community;

import java.util.UUID;

public record CommunityMediaResponse(
        UUID id,
        CommunityMediaType type,
        String url,
        String contentType) {
}
