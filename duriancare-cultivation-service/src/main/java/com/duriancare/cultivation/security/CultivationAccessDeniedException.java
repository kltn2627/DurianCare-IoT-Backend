package com.duriancare.cultivation.security;

public class CultivationAccessDeniedException extends RuntimeException {

    public CultivationAccessDeniedException(String message) {
        super(message);
    }
}
