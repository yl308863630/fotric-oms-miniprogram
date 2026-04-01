package com.oms.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 常见场景：users.permissions 仍为 VARCHAR(255)，多选权限后超长 → MySQL Data truncation。
     * 用户勾选「合同-平台合同全量查看」等只是压垮长度的最后一根稻草，并非该权限码本身异常。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String raw = ex.getMostSpecificCause() != null ? String.valueOf(ex.getMostSpecificCause().getMessage()) : ex.getMessage();
        if (raw == null) {
            raw = "";
        }
        String lower = raw.toLowerCase();
        String message = "数据无法保存：可能违反唯一约束，或某字段超出数据库长度限制。";
        if (raw.contains("permissions") || lower.contains("data too long") || lower.contains("truncated")
                || lower.contains("too long for column")) {
            message = "权限勾选过多，超出当前库中 permissions 字段长度。请在 MySQL 执行：ALTER TABLE users MODIFY COLUMN permissions TEXT NULL;（脚本见 oms-backend/sql/alter-users-permissions-to-text.sql），执行后无需改代码即可保存。";
        }
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", message);
        body.put("error", "Bad Request");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getMessage());
        body.put("error", "Bad Request");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

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
