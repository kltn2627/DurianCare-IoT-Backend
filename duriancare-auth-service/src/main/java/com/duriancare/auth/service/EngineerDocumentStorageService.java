package com.duriancare.auth.service;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface EngineerDocumentStorageService {

    List<StoredEngineerDocument> upload(UUID userId, List<MultipartFile> documents);

    void delete(String documentUrl);
}
