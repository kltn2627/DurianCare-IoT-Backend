package com.duriancare.notification.controller;

import com.duriancare.notification.service.EmailDeliveryException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailDeliveryException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    Map<String, String> handleEmailDelivery(EmailDeliveryException exception) {
        return Map.of("status", "error", "message", exception.getMessage());
    }
}
