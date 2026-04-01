package com.oms.service;

import com.oms.config.CaptchaWhitelistConfig;
import com.oms.entity.CaptchaIpAllowlist;
import com.oms.repository.CaptchaIpAllowlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CaptchaWhitelistService {

    private final CaptchaIpAllowlistRepository repository;
    private final CaptchaWhitelistConfig config;

    public List<CaptchaIpAllowlist> listAll() {
        return repository.findAllByOrderByEnabledDescUpdateTimeDescIdDesc();
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] headerCandidates = new String[] {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_REAL_IP"
        };
        for (String header : headerCandidates) {
            String value = request.getHeader(header);
            String ip = firstValidIp(value);
            if (!ip.isEmpty()) {
                return normalizeIp(ip);
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    public boolean shouldSkipCaptcha(HttpServletRequest request) {
        if (Boolean.FALSE.equals(config.getWhitelistEnabled())) {
            return false;
        }
        String clientIp = extractClientIp(request);
        if (clientIp.isBlank()) {
            return false;
        }
        for (String rule : loadEffectiveRules()) {
            if (ipMatchesRule(clientIp, rule)) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidRule(String ipOrCidr) {
        if (ipOrCidr == null || ipOrCidr.trim().isEmpty()) {
            return false;
        }
        String rule = ipOrCidr.trim();
        if (!rule.contains("/")) {
            return parseInetAddress(rule) != null;
        }
        String[] parts = rule.split("/", 2);
        if (parts.length != 2) {
            return false;
        }
        InetAddress address = parseInetAddress(parts[0].trim());
        if (address == null) {
            return false;
        }
        try {
            int prefixLength = Integer.parseInt(parts[1].trim());
            int totalBits = address.getAddress().length * 8;
            return prefixLength >= 0 && prefixLength <= totalBits;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Transactional
    public CaptchaIpAllowlist save(CaptchaIpAllowlist payload, String operatorName) {
        if (payload == null) {
            throw new IllegalArgumentException("白名单规则不能为空");
        }
        String rule = normalizeRule(payload.getIpCidr());
        if (!isValidRule(rule)) {
            throw new IllegalArgumentException("IP 或 CIDR 格式不正确");
        }

        CaptchaIpAllowlist target = payload.getId() == null
                ? new CaptchaIpAllowlist()
                : repository.findById(payload.getId()).orElseThrow(() -> new IllegalArgumentException("白名单规则不存在"));

        repository.findByIpCidr(rule).ifPresent(existing -> {
            if (target.getId() == null || !existing.getId().equals(target.getId())) {
                throw new IllegalArgumentException("该 IP/CIDR 规则已存在");
            }
        });

        target.setIpCidr(rule);
        target.setEnabled(payload.getEnabled() == null ? Boolean.TRUE : payload.getEnabled());
        target.setRemark(trimToNull(payload.getRemark()));
        if (target.getId() == null) {
            target.setCreatedBy(trimToNull(operatorName));
        }
        target.setUpdatedBy(trimToNull(operatorName));
        return repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || !repository.existsById(id)) {
            throw new IllegalArgumentException("白名单规则不存在");
        }
        repository.deleteById(id);
    }

    private List<String> loadEffectiveRules() {
        Set<String> rules = new LinkedHashSet<>();
        if (config.getWhitelistIps() != null) {
            for (String item : config.getWhitelistIps()) {
                String normalized = normalizeRule(item);
                if (!normalized.isEmpty() && isValidRule(normalized)) {
                    rules.add(normalized);
                }
            }
        }
        for (CaptchaIpAllowlist item : repository.findAllByOrderByEnabledDescUpdateTimeDescIdDesc()) {
            if (!Boolean.TRUE.equals(item.getEnabled())) {
                continue;
            }
            String normalized = normalizeRule(item.getIpCidr());
            if (!normalized.isEmpty() && isValidRule(normalized)) {
                rules.add(normalized);
            }
        }
        return new ArrayList<>(rules);
    }

    private boolean ipMatchesRule(String clientIp, String rule) {
        if (clientIp == null || clientIp.isBlank() || rule == null || rule.isBlank()) {
            return false;
        }
        String normalizedClientIp = normalizeIp(clientIp);
        String normalizedRule = normalizeRule(rule);
        if (!normalizedRule.contains("/")) {
            return normalizedClientIp.equalsIgnoreCase(normalizedRule);
        }

        String[] parts = normalizedRule.split("/", 2);
        InetAddress ipAddress = parseInetAddress(normalizedClientIp);
        InetAddress ruleAddress = parseInetAddress(parts[0]);
        if (ipAddress == null || ruleAddress == null) {
            return false;
        }

        byte[] ipBytes = ipAddress.getAddress();
        byte[] ruleBytes = ruleAddress.getAddress();
        if (ipBytes.length != ruleBytes.length) {
            return false;
        }

        int prefixLength = Integer.parseInt(parts[1]);
        int fullBytes = prefixLength / 8;
        int remainderBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (ipBytes[i] != ruleBytes[i]) {
                return false;
            }
        }
        if (remainderBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainderBits);
        return (ipBytes[fullBytes] & mask) == (ruleBytes[fullBytes] & mask);
    }

    private InetAddress parseInetAddress(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstValidIp(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return "";
        }
        String[] parts = rawHeader.split(",");
        for (String part : parts) {
            String value = normalizeIp(part);
            if (!value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeRule(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeIp(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if ("0:0:0:0:0:0:0:1".equals(normalized)) {
            return "::1";
        }
        if (normalized.startsWith("::ffff:")) {
            return normalized.substring("::ffff:".length());
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
