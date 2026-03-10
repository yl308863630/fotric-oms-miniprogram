package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "settlements")
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", unique = true, nullable = false)
    private String billNo;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "pre_tax_amount")
    private BigDecimal preTaxAmount;

    @Column(nullable = false)
    private String status; // DRAFT, PENDING, SETTLED

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "apply_time")
    private LocalDateTime applyTime;

    @Column(name = "settle_time")
    private LocalDateTime settleTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
