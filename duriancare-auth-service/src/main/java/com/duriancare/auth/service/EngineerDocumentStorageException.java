package com.duriancare.auth.service;

public class EngineerDocumentStorageException extends RuntimeException {

    public EngineerDocumentStorageException(String message) {
        super(message);
    }

    public EngineerDocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
