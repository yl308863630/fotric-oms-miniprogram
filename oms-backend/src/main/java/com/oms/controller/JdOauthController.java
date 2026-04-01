package com.oms.controller;

import com.oms.config.JdLogisticsConfig;
import com.oms.service.JdTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jd/oauth")
public class JdOauthController {

    private final JdLogisticsConfig config;
    private final JdTokenService tokenService;

    public JdOauthController(JdLogisticsConfig config, JdTokenService tokenService) {
        this.config = config;
        this.tokenService = tokenService;
    }

    /** 获取京东授权链接（浏览器打开后登录授权）。 */
    @GetMapping("/authorize-url")
    public ResponseEntity<Map<String, Object>> authorizeUrl(
            @RequestParam(required = false, defaultValue = "oms-jd-auth") String state) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("authorizeUrl", tokenService.buildAuthorizeUrl(state));
        res.put("message", "请复制 authorizeUrl 到浏览器打开，登录授权后会回调 /api/jd/oauth/callback?code=...");
        return ResponseEntity.ok(res);
    }

    /** OAuth 回调：保存 code 并立即换取 access_token/refresh_token。 */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        config.setAuthorizationCode(code);
        String token = tokenService.getAccessToken();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("state", state);
        res.put("message", "授权成功，已获取 access_token 并缓存；后续将自动用 refresh_token 刷新。请把 refreshToken 保存到 application.yml 的 jd-logistics.refresh-token。");
        res.put("accessTokenPreview", mask(token));
        res.put("refreshTokenPreview", mask(config.getRefreshToken()));
        res.put("refreshToken", config.getRefreshToken());
        return ResponseEntity.ok(res);
    }

    private String mask(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.length() <= 12) return "****";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}

