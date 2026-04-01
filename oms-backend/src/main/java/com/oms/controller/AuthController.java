package com.oms.controller;

import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.security.JwtTokenUtil;
import com.oms.service.CaptchaService;
import com.oms.service.CaptchaWhitelistService;
import lombok.Data;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private CaptchaWhitelistService captchaWhitelistService;

    /** 图形验证码：返回 captchaKey 与 base64 图片，供登录时校验 */
    @GetMapping(value = "/captcha", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> captcha(HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        boolean skipCaptcha = captchaWhitelistService.shouldSkipCaptcha(request);
        body.put("skipCaptcha", skipCaptcha);
        if (skipCaptcha) {
            body.put("captchaKey", "");
            body.put("image", "");
            return body;
        }
        CaptchaService.CaptchaResult result = captchaService.generate();
        body.put("captchaKey", result.captchaKey);
        body.put("image", result.image);
        return body;
    }

    /** 避免 Safari 等对 GET /api/auth/login 误触发「下载 login」：返回 405 + JSON，明确 Content-Type */
    @GetMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> loginGet() {
        Map<String, String> body = new HashMap<>();
        body.put("error", "请使用 POST 方法登录");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        boolean captchaSkipped = captchaWhitelistService.shouldSkipCaptcha(httpServletRequest);
        if (!captchaSkipped && !captchaService.validate(request.getCaptchaKey(), request.getCaptchaValue())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "验证码错误"));
        }
        System.out.println("Login attempt for user: " + request.getUsername());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            System.out.println("Authentication successful for user: " + request.getUsername());
        } catch (Exception e) {
            System.out.println("Authentication failed for user: " + request.getUsername());
            System.out.println("Error: " + e.getMessage());
            throw new RuntimeException("用户名或密码错误");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        System.out.println("UserDetails loaded: " + userDetails.getUsername());
        final String jwt = jwtTokenUtil.generateToken(userDetails);
        System.out.println("JWT token generated successfully");

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("username", userDetails.getUsername());
        response.put("captchaSkipped", captchaSkipped);
        if (user != null) {
            response.put("id", user.getId());
            response.put("realName", user.getRealName());
            response.put("companyTitle", user.getCompanyTitle());
            response.put("role", user.getRole());
            response.put("permissions", user.getPermissions());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null) user.setRole("ROLE_USER");
        return userRepository.save(user);
    }

    /**
     * 验证当前登录用户密码（用于查看/编辑敏感信息前的二次确认）
     */
    @PostMapping("/verify-password")
    public Map<String, Object> verifyPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        String password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            result.put("ok", false);
            result.put("message", "请输入密码");
            return result;
        }
        String username = null;
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            username = SecurityContextHolder.getContext().getAuthentication().getName();
        }
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            result.put("ok", false);
            result.put("message", "请先登录");
            return result;
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getPassword() == null) {
            result.put("ok", false);
            result.put("message", "用户不存在");
            return result;
        }
        if (!passwordEncoder.matches(password.trim(), user.getPassword())) {
            result.put("ok", false);
            result.put("message", "密码错误");
            return result;
        }
        result.put("ok", true);
        return result;
    }

    /**
     * 当前用户修改密码（需已登录）
     */
    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        String username = null;
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            username = SecurityContextHolder.getContext().getAuthentication().getName();
        }
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录"));
        }
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入当前密码"));
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入新密码"));
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "用户不存在"));
        }
        if (!passwordEncoder.matches(request.getCurrentPassword().trim(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前密码错误"));
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String captchaKey;
        private String captchaValue;
    }

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }
}
