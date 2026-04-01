package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "authorization_scan_logs")
public class AuthorizationScanLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_record_id", nullable = false)
    private Long authorizationRecordId;

    @Column(name = "authorization_code", length = 64)
    private String authorizationCode;

    @Column(name = "verify_result", length = 32)
    private String verifyResult;

    @Column(name = "token_fingerprint", length = 128)
    private String tokenFingerprint;

    @Column(name = "channel", length = 64)
    private String channel;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "geo_country", length = 64)
    private String geoCountry;

    @Column(name = "geo_region", length = 64)
    private String geoRegion;

    @Column(name = "geo_city", length = 64)
    private String geoCity;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    @Column(name = "operator_username", length = 64)
    private String operatorUsername;

    @Column(name = "operator_real_name", length = 64)
    private String operatorRealName;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
    }
}
