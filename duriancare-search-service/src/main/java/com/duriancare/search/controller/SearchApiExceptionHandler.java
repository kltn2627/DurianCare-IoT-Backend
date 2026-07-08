package com.duriancare.search.controller;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class SearchApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchApiExceptionHandler.class);

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<SearchApiError> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<SearchApiError> handleUnexpected(Exception exception) {
        LOGGER.error("Unexpected search error", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<SearchApiError> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new SearchApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message));
    }
}
