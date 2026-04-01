package com.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.JdLogisticsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class JdTokenService {

    private final JdLogisticsConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Object lock = new Object();

    private volatile String accessToken;
    private volatile Instant expireAt = Instant.EPOCH;

    public JdTokenService(JdLogisticsConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /** 获取可用 token（按需自动刷新）。 */
    public String getAccessToken() {
        if ((accessToken == null || accessToken.isBlank()) && !isBlank(config.getAccessToken())) {
            // OOB 页面已直接发放 access token：优先使用配置中的 token，避免重复走换 token 接口。
            this.accessToken = config.getAccessToken().trim();
            // 兜底设置较长有效期，具体过期由京东实际校验。
            this.expireAt = Instant.now().plusSeconds(20L * 24 * 3600);
            log.info("使用配置中的京东 access token 进行请求");
        }
        if (shouldRefresh()) {
            synchronized (lock) {
                if (shouldRefresh()) {
                    refreshNow();
                }
            }
        }
        return accessToken;
    }

    /** 统一 Authorization 头值：bearer xxx */
    public String getAuthorizationHeaderValue() {
        return "Bearer " + getAccessToken();
    }

    /** 统一构造带 Authorization 的请求头。 */
    public HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue());
        return headers;
    }

    /** 生成一次性授权链接（浏览器打开并登录授权后，可拿到 code）。 */
    public String buildAuthorizeUrl(String state) {
        validateConfig();
        if (isBlank(config.getAuthorizeUrl())) {
            throw new IllegalStateException("jd-logistics.authorize-url 未配置");
        }
        if (isBlank(config.getRedirectUri())) {
            throw new IllegalStateException("jd-logistics.redirect-uri 未配置");
        }
        String s = isBlank(state) ? "oms-jd-auth" : state.trim();
        return UriComponentsBuilder.fromHttpUrl(config.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", config.getAppKey())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("state", s)
                .build()
                .toUriString();
    }

    private boolean shouldRefresh() {
        if (accessToken == null || accessToken.isBlank()) return true;
        int refreshDays = config.getRefreshBeforeDays() == null ? 7 : Math.max(config.getRefreshBeforeDays(), 0);
        Instant refreshAt = expireAt.minusSeconds(refreshDays * 24L * 3600L);
        return Instant.now().isAfter(refreshAt);
    }

    private void refreshNow() {
        validateConfig();

        try {
            if (!isBlank(config.getAccessToken())) {
                this.accessToken = config.getAccessToken().trim();
                this.expireAt = Instant.now().plusSeconds(20L * 24 * 3600);
                return;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", config.getAppKey());
            form.add("client_secret", config.getAppSecret());
            if (!isBlank(config.getRefreshToken())) {
                form.add("grant_type", "refresh_token");
                form.add("refresh_token", config.getRefreshToken().trim());
            } else if (!isBlank(config.getAuthorizationCode())) {
                form.add("grant_type", "authorization_code");
                form.add("code", config.getAuthorizationCode().trim());
                if (!isBlank(config.getRedirectUri())) {
                    form.add("redirect_uri", config.getRedirectUri().trim());
                }
            } else {
                throw new IllegalStateException("缺少授权信息：请先配置 jd-logistics.refresh-token 或 jd-logistics.authorization-code");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
            String body = restTemplate.postForObject(config.getOauthUrl(), entity, String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);

            String token = root.path("access_token").asText("");
            long expiresIn = root.path("expires_in").asLong(0L);
            if (token.isBlank() || expiresIn <= 0) {
                String err = extractErrorMessage(root);
                throw new IllegalStateException("京东 token 获取失败: " + err + "；原始响应: " + body);
            }

            this.accessToken = token;
            this.expireAt = Instant.now().plusSeconds(expiresIn);
            String newRefreshToken = root.path("refresh_token").asText("");
            if (!newRefreshToken.isBlank()) {
                config.setRefreshToken(newRefreshToken);
            }
            if (!isBlank(config.getAuthorizationCode())) {
                // 授权码为一次性，首次成功后清空避免重复使用报错
                config.setAuthorizationCode(null);
            }
            log.info("京东token刷新成功，有效期{}秒，过期时间{}", expiresIn, expireAt);
            if (!isBlank(config.getRefreshToken())) {
                log.info("已获取/更新 refresh_token（建议同步写回配置中心，避免服务重启后丢失）");
            }
        } catch (Exception e) {
            throw new RuntimeException("京东token获取失败: " + e.getMessage(), e);
        }
    }

    private void validateConfig() {
        if (isBlank(config.getAppKey())) {
            throw new IllegalStateException("jd-logistics.app-key 未配置");
        }
        if (isBlank(config.getAppSecret())) {
            throw new IllegalStateException("jd-logistics.app-secret 未配置");
        }
        if (isBlank(config.getAccessToken()) && isBlank(config.getOauthUrl())) {
            throw new IllegalStateException("jd-logistics.oauth-url 未配置");
        }
    }

    private String extractErrorMessage(JsonNode root) {
        List<String> cands = new ArrayList<>();
        cands.add(root.path("msg").asText(""));
        cands.add(root.path("error_description").asText(""));
        cands.add(root.path("error").asText(""));
        cands.add(root.path("zh_desc").asText(""));
        cands.add(root.path("en_desc").asText(""));
        cands.add(root.path("model").path("msg").asText(""));
        cands.add(root.path("model").path("zh_desc").asText(""));
        cands.add(root.path("error_response").path("zh_desc").asText(""));
        cands.add(root.path("error_response").path("en_desc").asText(""));
        cands.add(root.path("code").asText(""));
        cands.add(root.path("model").path("code").asText(""));
        for (String c : cands) {
            if (!isBlank(c)) return c.trim();
        }
        return "未知错误";
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

