package com.eneik.production.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * @file GlobalExceptionHandler.java
 * @agent TAG-07 (Second-Order Knowledge)
 * @description Global exception handler to prevent stack trace leakage and ensure contract compliance.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataComplianceException.class)
    public ResponseEntity<Map<String, Object>> handleComplianceViolation(DataComplianceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Compliance Violation",
                        "code", 400
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Log the exception internally (omitted for brevity)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal Error",
                        "code", 500
                ));
    }
}
