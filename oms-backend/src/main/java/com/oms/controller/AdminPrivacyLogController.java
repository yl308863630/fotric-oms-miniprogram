package com.oms.controller;

import com.oms.entity.PrivacyAccessLog;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.PrivacyAccessLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminPrivacyLogController {

    @Autowired
    private PrivacyAccessLogService privacyAccessLogService;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdmin() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null || "anonymousUser".equals(name)) return false;
        return userRepository.findByUsername(name)
                .map(u -> "ROLE_ADMIN".equals(u.getRole()))
                .orElse(false);
    }

    @GetMapping("/privacy-logs")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员可查看"));
        }
        Map<String, Object> body = new HashMap<>();
        try {
            LocalDateTime start = null;
            LocalDateTime end = null;
            if (startTime != null && !startTime.isBlank()) {
                try {
                    start = LocalDateTime.parse(startTime + "T00:00:00");
                } catch (Exception ignored) {}
            }
            if (endTime != null && !endTime.isBlank()) {
                try {
                    end = LocalDateTime.parse(endTime + "T23:59:59");
                } catch (Exception ignored) {}
            }
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<PrivacyAccessLog> result = privacyAccessLogService.findPage(
                    operatorName, targetType, action, start, end, pageable);
            body.put("content", result.getContent());
            body.put("totalElements", result.getTotalElements());
            body.put("totalPages", result.getTotalPages());
            body.put("number", result.getNumber());
            body.put("size", result.getSize());
        } catch (Exception e) {
            body.put("content", new ArrayList<>());
            body.put("totalElements", 0L);
            body.put("totalPages", 0);
            body.put("number", 0);
            body.put("size", size);
            body.put("message", "查询失败，请确认数据库表 privacy_access_logs 已创建");
        }
        return ResponseEntity.ok(body);
    }
}
