package com.duriancare.notification.service;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
