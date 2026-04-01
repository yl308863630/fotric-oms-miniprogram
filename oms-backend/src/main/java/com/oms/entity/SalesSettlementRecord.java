package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "sales_settlements_new")
public class SalesSettlementRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true)
    private String billNo;

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "party_a_title")
    private String partyATitle;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "refund_status")
    private String refundStatus;

    @Column(name = "refund_date")
    private LocalDate refundDate;

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
    private List<SalesSettlementItem> items;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "待回款";
        }
        if (refundStatus == null || refundStatus.isBlank()) {
            refundStatus = "未回款";
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
