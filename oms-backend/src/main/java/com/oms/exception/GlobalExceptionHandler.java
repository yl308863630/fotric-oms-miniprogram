package com.oms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        System.err.println("=== Exception caught ===");
        System.err.println("Exception type: " + ex.getClass().getName());
        System.err.println("Exception message: " + ex.getMessage());
        System.err.println("Stack trace:");
        ex.printStackTrace();
        System.err.println("=======================");

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now());
        errorDetails.put("message", ex.getMessage());
        errorDetails.put("error", ex.getClass().getSimpleName());

        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
