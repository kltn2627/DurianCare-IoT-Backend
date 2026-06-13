package com.duriancare.cultivation.api;

import com.duriancare.cultivation.service.CultivationScheduleNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CultivationApiExceptionHandler {

    @ExceptionHandler(CultivationScheduleNotFoundException.class)
    ProblemDetail notFound(CultivationScheduleNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setType(URI.create("https://duriancare.local/problems/cultivation-schedule-not-found"));
        return detail;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, messageOf(exception));
        detail.setType(URI.create("https://duriancare.local/problems/invalid-cultivation-schedule"));
        return detail;
    }

    private String messageOf(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            return validationException.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .orElse("Invalid cultivation schedule request");
        }
        return exception.getMessage();
    }
}
