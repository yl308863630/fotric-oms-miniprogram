package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "privacy_access_logs")
public class PrivacyAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(nullable = false, length = 32)
    private String action; // COPY, DOWNLOAD, EXPORT

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType; // SALES_ORDER, PARTNER, CONTRACT, USER, DELIVERY_NOTE, OPPORTUNITY

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    @Column(name = "field_or_description", length = 500)
    private String fieldOrDescription;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
