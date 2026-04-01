package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_payment_requests")
public class PurchasePaymentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", unique = true, nullable = false)
    private String requestNo;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "purchase_order_no")
    private String purchaseOrderNo;

    @Column(name = "oms_order_no")
    private String omsOrderNo;

    @Column(name = "payee_company")
    private String payeeCompany;

    @Column(name = "requested_amount")
    private BigDecimal requestedAmount;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    /** PENDING_REVIEW / INVOICE_TRACKING / READY_TO_PAY / PAID / REJECTED / CLOSED */
    private String status;

    @Column(name = "apply_remark", columnDefinition = "TEXT")
    private String applyRemark;

    @Column(name = "finance_remark", columnDefinition = "TEXT")
    private String financeRemark;

    @Column(name = "voucher_url")
    private String voucherUrl;

    @Column(name = "bank_flow_no")
    private String bankFlowNo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "paid_time")
    private LocalDateTime paidTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        updateTime = now;
        if (status == null || status.isBlank()) status = "PENDING_REVIEW";
    }

    @PreUpdate
    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}

