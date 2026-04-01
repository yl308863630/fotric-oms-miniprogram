package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "sales_reconciliations")
public class SalesReconciliation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true)
    private String billNo;

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "party_a_title")
    private String partyATitle;

    @Column(name = "operation_entity_title")
    private String operationEntityTitle;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "platform_reconciliation_no")
    private String platformReconciliationNo;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Transient
    private List<SalesReconciliationItem> items;

    @Transient
    private String omsOrderNoSummary;

    @Transient
    private String orderNoSummary;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "已对账";
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        if (reconciliationDate == null) {
            reconciliationDate = LocalDate.now();
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
