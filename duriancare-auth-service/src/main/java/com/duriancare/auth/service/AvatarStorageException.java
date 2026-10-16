package com.duriancare.auth.service;

public class AvatarStorageException extends RuntimeException {

    public AvatarStorageException(String message) {
        super(message);
    }

    public AvatarStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
