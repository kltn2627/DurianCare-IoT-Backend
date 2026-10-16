package com.duriancare.auth.service;

public record StoredEngineerDocument(
        String objectKey,
        String documentUrl,
        String fileName,
        String contentType,
        long fileSize) {
}
