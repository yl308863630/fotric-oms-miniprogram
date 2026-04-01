package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_inbound_invoices")
public class PurchaseInboundInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_request_id", nullable = false)
    private Long paymentRequestId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_amount")
    private BigDecimal invoiceAmount;

    /** 待开票 / 已开票 / 已收票 / 退票重开 / 异常 */
    private String status;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        updateTime = now;
        if (status == null || status.isBlank()) status = "待开票";
    }

    @PreUpdate
    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}

