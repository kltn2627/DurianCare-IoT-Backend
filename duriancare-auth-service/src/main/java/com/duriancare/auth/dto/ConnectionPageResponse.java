package com.duriancare.auth.dto;

import java.util.List;

public record ConnectionPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
