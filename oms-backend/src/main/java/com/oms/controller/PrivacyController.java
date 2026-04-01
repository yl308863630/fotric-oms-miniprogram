package com.oms.controller;

import com.oms.entity.User;
import com.oms.repository.*;
import com.oms.service.PrivacyAccessLogService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    @Autowired
    private PrivacyAccessLogService privacyAccessLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.oms.repository.SalesOrderRepository salesOrderRepository;

    @Autowired
    private PartnerInfoRepository partnerInfoRepository;

    @Autowired
    private ContractRepository contractRepository;

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "";
    }

    @PostMapping("/log")
    public ResponseEntity<Map<String, Object>> log(@RequestBody PrivacyLogRequest body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || "anonymousUser".equals(username)) {
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(401).body(result);
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }
        String targetType = body.getTargetType();
        String targetId = body.getTargetId();
        String action = body.getAction() != null && !body.getAction().isBlank() ? body.getAction() : "COPY";
        String field = body.getField();

        if (targetType == null || targetType.isBlank() || targetId == null || targetId.isBlank()) {
            result.put("success", false);
            result.put("message", "targetType 和 targetId 必填");
            return ResponseEntity.badRequest().body(result);
        }
        String targetIdTrimmed = targetId.trim();
        if ("undefined".equalsIgnoreCase(targetIdTrimmed) || "null".equalsIgnoreCase(targetIdTrimmed)) {
            result.put("success", false);
            result.put("message", "目标ID无效");
            return ResponseEntity.badRequest().body(result);
        }

        if (!validateTargetExists(targetType, targetIdTrimmed)) {
            result.put("success", false);
            result.put("message", "目标不存在或无权限");
            return ResponseEntity.badRequest().body(result);
        }

        String operatorName = user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : username;
        String clientIp = request != null ? getClientIp(request) : "";

        try {
            privacyAccessLogService.log(user.getId(), operatorName, clientIp, action, targetType, targetIdTrimmed, field);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "记录失败");
        }
        return ResponseEntity.ok(result);
    }

    private boolean validateTargetExists(String targetType, String targetId) {
        try {
            if (targetType == null || targetId == null || targetId.isBlank()) return false;
            switch (targetType.toUpperCase()) {
                case "SALES_ORDER":
                case "DELIVERY_NOTE":
                    long orderId = Long.parseLong(targetId.trim());
                    return salesOrderRepository != null && salesOrderRepository.findById(orderId).isPresent();
                case "PARTNER":
                    long partnerId = Long.parseLong(targetId.trim());
                    return partnerInfoRepository != null && partnerInfoRepository.findById(partnerId).isPresent();
                case "CONTRACT":
                    long contractId = Long.parseLong(targetId.trim());
                    return contractRepository != null && contractRepository.findById(contractId).isPresent();
                case "USER":
                    long userId = Long.parseLong(targetId.trim());
                    return userRepository != null && userRepository.findById(userId).isPresent();
                case "OPPORTUNITY":
                    return true;
                default:
                    return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Data
    public static class PrivacyLogRequest {
        private String targetType;
        private String targetId;
        private String action;
        private String field;
    }
}
