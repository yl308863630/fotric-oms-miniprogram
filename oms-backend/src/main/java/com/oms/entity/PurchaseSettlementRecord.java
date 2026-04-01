package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "purchase_settlements")
public class PurchaseSettlementRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true)
    private String billNo;

    private String supplier;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Transient
    private List<PurchaseSettlementItem> items;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "待付款";
        }
        if (paymentStatus == null || paymentStatus.isBlank()) {
            paymentStatus = "待付款";
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        if (settlementDate == null) {
            settlementDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
    }
}
