package com.oms.controller;

import com.oms.entity.CaptchaIpAllowlist;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.CaptchaWhitelistService;
import com.oms.service.OperationLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/security/captcha-ip-allowlist")
@CrossOrigin(origins = "*")
public class CaptchaIpAllowlistController {

    private final CaptchaWhitelistService captchaWhitelistService;
    private final UserRepository userRepository;
    private final OperationLogService operationLogService;

    public CaptchaIpAllowlistController(CaptchaWhitelistService captchaWhitelistService,
                                        UserRepository userRepository,
                                        OperationLogService operationLogService) {
        this.captchaWhitelistService = captchaWhitelistService;
        this.userRepository = userRepository;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可维护验证码 IP 白名单"));
        }
        List<CaptchaIpAllowlist> data = captchaWhitelistService.listAll();
        return ResponseEntity.ok(data);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CaptchaIpAllowlist payload) {
        return saveInternal(payload, true);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CaptchaIpAllowlist payload) {
        if (payload != null) {
            payload.setId(id);
        }
        return saveInternal(payload, false);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可维护验证码 IP 白名单"));
        }
        try {
            captchaWhitelistService.delete(id);
            operationLogService.log(currentUsername(), "删除验证码IP白名单", "CAPTCHA_IP_ALLOWLIST", String.valueOf(id), "删除规则");
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }

    private ResponseEntity<?> saveInternal(CaptchaIpAllowlist payload, boolean create) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可维护验证码 IP 白名单"));
        }
        try {
            CaptchaIpAllowlist saved = captchaWhitelistService.save(payload, currentUsername());
            String action = create ? "新增验证码IP白名单" : "更新验证码IP白名单";
            String details = "规则=" + saved.getIpCidr() + ", enabled=" + saved.getEnabled();
            operationLogService.log(currentUsername(), action, "CAPTCHA_IP_ALLOWLIST", String.valueOf(saved.getId()), details);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }

    private boolean isAdmin() {
        String username = currentUsername();
        if (username == null || username.isBlank()) {
            return false;
        }
        return userRepository.findByUsername(username)
                .map(User::getRole)
                .map("ROLE_ADMIN"::equals)
                .orElse(false);
    }

    private String currentUsername() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
