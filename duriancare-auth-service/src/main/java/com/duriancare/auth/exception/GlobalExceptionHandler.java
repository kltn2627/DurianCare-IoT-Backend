package com.duriancare.auth.exception;

import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiError> handleConflict(RuntimeException exception) {
        String message = exception instanceof ConflictException
                ? exception.getMessage()
                : "The requested data conflicts with an existing record";
        return response(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({InvalidRequestException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validationException
                ? validationException.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "))
                : exception.getMessage();
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({AuthenticationFailedException.class, InvalidTokenException.class})
    ResponseEntity<ApiError> handleUnauthorized(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(EventPublicationException.class)
    ResponseEntity<ApiError> handleEventPublication(EventPublicationException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message));
    }
}
