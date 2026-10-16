package com.duriancare.notification.controller;

import com.duriancare.notification.service.EmailDeliveryException;
import com.duriancare.notification.service.NotificationAccessDeniedException;
import com.duriancare.notification.service.NotificationNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailDeliveryException.class)
    ResponseEntity<Map<String, Object>> handleEmailDelivery(EmailDeliveryException exception) {
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, messageOf(exception));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(NotificationNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(NotificationAccessDeniedException.class)
    ResponseEntity<Map<String, Object>> handleForbidden(NotificationAccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(errorBody(status, message));
    }

    private Map<String, Object> errorBody(HttpStatus status, String message) {
        return Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message);
    }

    private String messageOf(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            FieldError fieldError = validationException.getBindingResult().getFieldError();
            if (fieldError != null) {
                return fieldError.getDefaultMessage();
            }
            return "Request validation failed";
        }
        return exception.getMessage();
    }
}
