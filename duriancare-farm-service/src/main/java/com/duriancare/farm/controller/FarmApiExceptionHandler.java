package com.duriancare.farm.controller;

import com.duriancare.farm.service.FarmAccessDeniedException;
import com.duriancare.farm.service.FarmAuthenticationException;
import com.duriancare.farm.service.FarmConflictException;
import com.duriancare.farm.service.FarmInvalidRequestException;
import com.duriancare.farm.service.FarmNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FarmApiExceptionHandler {

    @ExceptionHandler(FarmAuthenticationException.class)
    ResponseEntity<Map<String, Object>> handleUnauthorized(FarmAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(FarmAccessDeniedException.class)
    ResponseEntity<Map<String, Object>> handleForbidden(FarmAccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(FarmNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(FarmNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(FarmConflictException.class)
    ResponseEntity<Map<String, Object>> handleConflict(FarmConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, Object>> handleDuplicateKey(DuplicateKeyException exception) {
        return response(HttpStatus.CONFLICT, "A conflicting farm authorization record already exists");
    }

    @ExceptionHandler({FarmInvalidRequestException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validationException
                ? validationException.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "))
                : exception.getMessage();
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
