package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_logs")
public class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType; // INVOICE, SETTLEMENT, CONTRACT, USER, etc.

    @Column(name = "target_id")
    private String targetId;

    @Column(length = 1000)
    private String details;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
