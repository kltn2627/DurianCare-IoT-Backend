package com.duriancare.auth.community;

import java.util.List;

public record CommunityPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
