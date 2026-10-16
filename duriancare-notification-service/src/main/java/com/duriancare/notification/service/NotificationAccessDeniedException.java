package com.duriancare.notification.service;

public class NotificationAccessDeniedException extends RuntimeException {

    public NotificationAccessDeniedException(String message) {
        super(message);
    }
}
