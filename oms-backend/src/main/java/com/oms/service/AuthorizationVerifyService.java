package com.oms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.AuthorizationRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthorizationVerifyService {
    @Value("${authorization.verify.secret:change-this-secret}")
    private String verifySecret;

    @Value("${authorization.verify.token-expire-seconds:31536000}")
    private long tokenExpireSeconds;

    private final ObjectMapper objectMapper;

    public AuthorizationVerifyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createVerifyToken(AuthorizationRecord record) {
        try {
            long now = Instant.now().getEpochSecond();
            // LinkedHashMap 保证 JSON 字段顺序稳定，避免不同 JDK/环境下序列化差异导致签名字符串不一致
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rid", record.getId());
            payload.put("code", record.getAuthorizationCode());
            payload.put("exp", now + tokenExpireSeconds);
            payload.put("iat", now);
            String payloadJson = objectMapper.writeValueAsString(payload);
            String encodedPayload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = hmacSha256(encodedPayload, verifySecret);
            return encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("生成验真令牌失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> parseAndValidate(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            throw new IllegalArgumentException("验真令牌格式无效");
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) throw new IllegalArgumentException("验真令牌格式无效");
            String payloadPart = parts[0];
            String signPart = parts[1];
            String expectedSign = hmacSha256(payloadPart, verifySecret);
            if (!constantTimeEquals(signPart, expectedSign)) {
                throw new IllegalArgumentException("验真签名校验失败");
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadPart);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, new TypeReference<>() {});
            Object expObj = payload.get("exp");
            if (expObj == null) throw new IllegalArgumentException("验真令牌缺少过期时间");
            long exp = Long.parseLong(String.valueOf(expObj));
            if (Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("验真令牌已过期");
            }
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("验真令牌解析失败: " + e.getMessage(), e);
        }
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sign = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64Url(sign);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int result = 0;
        for (int i = 0; i < x.length; i++) {
            result |= x[i] ^ y[i];
        }
        return result == 0;
    }
}
