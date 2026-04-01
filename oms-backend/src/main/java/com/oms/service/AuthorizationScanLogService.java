package com.oms.service;

import com.oms.entity.AuthorizationRecord;
import com.oms.entity.AuthorizationScanLog;
import com.oms.entity.User;
import com.oms.repository.AuthorizationScanLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AuthorizationScanLogService {
    private final AuthorizationScanLogRepository scanLogRepository;
    private final com.oms.repository.UserRepository userRepository;
    private final IpGeoResolutionService ipGeoResolutionService;

    public AuthorizationScanLogService(AuthorizationScanLogRepository scanLogRepository,
                                       com.oms.repository.UserRepository userRepository,
                                       IpGeoResolutionService ipGeoResolutionService) {
        this.scanLogRepository = scanLogRepository;
        this.userRepository = userRepository;
        this.ipGeoResolutionService = ipGeoResolutionService;
    }

    public AuthorizationScanLog log(AuthorizationRecord record,
                                    String verifyResult,
                                    String tokenFingerprint,
                                    String channel,
                                    HttpServletRequest request) {
        AuthorizationScanLog log = new AuthorizationScanLog();
        if (record != null) {
            log.setAuthorizationRecordId(record.getId());
            log.setAuthorizationCode(record.getAuthorizationCode());
        } else {
            log.setAuthorizationRecordId(0L);
            log.setAuthorizationCode("-");
        }
        log.setVerifyResult(verifyResult);
        log.setTokenFingerprint(tokenFingerprint);
        log.setChannel(channel != null && !channel.isBlank() ? channel.trim() : "scan");
        String clientIp = extractIp(request);
        log.setClientIp(clientIp);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        // 优先 CDN/网关注入的头；否则用 ip2region 离线库按客户端 IP 解析（非精确门牌，仅国家/省/市）
        String hCountry = getHeaderOrNullable(request, "X-Geo-Country");
        String hRegion = getHeaderOrNullable(request, "X-Geo-Region");
        String hCity = getHeaderOrNullable(request, "X-Geo-City");
        boolean headerGeo = looksLikeRealGeo(hCountry) || looksLikeRealGeo(hRegion) || looksLikeRealGeo(hCity);
        if (headerGeo) {
            log.setGeoCountry(looksLikeRealGeo(hCountry) ? hCountry.trim() : "UNKNOWN");
            log.setGeoRegion(looksLikeRealGeo(hRegion) ? hRegion.trim() : "UNKNOWN");
            log.setGeoCity(looksLikeRealGeo(hCity) ? hCity.trim() : "UNKNOWN");
        } else {
            IpGeoResolutionService.GeoParts g = ipGeoResolutionService.lookup(clientIp);
            if (g != null) {
                log.setGeoCountry(g.country());
                log.setGeoRegion(g.region());
                log.setGeoCity(g.city());
            } else {
                log.setGeoCountry("UNKNOWN");
                log.setGeoRegion("UNKNOWN");
                log.setGeoCity("UNKNOWN");
            }
        }

        String username = SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        if (username != null && !username.isBlank() && !"anonymousUser".equalsIgnoreCase(username)) {
            User u = userRepository.findByUsername(username.trim()).orElse(null);
            if (u != null) {
                log.setOperatorUserId(u.getId());
                log.setOperatorUsername(u.getUsername());
                log.setOperatorRealName(u.getRealName());
            }
        }
        return scanLogRepository.save(log);
    }

    public Page<AuthorizationScanLog> search(Long authorizationRecordId, String result, String channel, Pageable pageable) {
        Specification<AuthorizationScanLog> spec = buildSpec(authorizationRecordId, result, channel);
        return scanLogRepository.findAll(spec, pageable);
    }

    public List<AuthorizationScanLog> searchAll(Long authorizationRecordId, String result, String channel) {
        Specification<AuthorizationScanLog> spec = buildSpec(authorizationRecordId, result, channel);
        return scanLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime"));
    }

    private Specification<AuthorizationScanLog> buildSpec(Long authorizationRecordId, String result, String channel) {
        Specification<AuthorizationScanLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (authorizationRecordId != null) {
                predicates.add(cb.equal(root.get("authorizationRecordId"), authorizationRecordId));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(cb.equal(root.get("verifyResult"), result.trim()));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel.trim()));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return spec;
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.contains(",") ? xff.split(",")[0].trim() : xff.trim();
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) return xrip.trim();
        return request.getRemoteAddr();
    }

    private String getHeaderOrNullable(HttpServletRequest request, String header) {
        if (request == null) return null;
        String v = request.getHeader(header);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static boolean looksLikeRealGeo(String v) {
        return v != null && !v.isBlank() && !"UNKNOWN".equalsIgnoreCase(v.trim());
    }
}
