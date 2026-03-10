package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", unique = true, nullable = false)
    private String billNo;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "pre_tax_amount", nullable = false)
    private BigDecimal preTaxAmount;

    @Column(nullable = false)
    private String status; // DRAFT, PENDING (Wait Confirm), CONFIRMED, APPLIED (Wait Invoice), INVOICED, SETTLED (Receipted)

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    @Column(name = "seller_name", nullable = false)
    private String sellerName;

    @Column(name = "seller_code")
    private String sellerCode;

    private String period;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "platform_apply_no")
    private String platformApplyNo;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "apply_time")
    private LocalDateTime applyTime;

    @Column(name = "invoice_time")
    private LocalDateTime invoiceTime;

    @Column(name = "receipt_time")
    private LocalDateTime receiptTime;

    @Column(name = "receipt_file_url")
    private String receiptFileUrl;

    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;

    public LocalDateTime getReceiptTime() { return receiptTime; }
    public void setReceiptTime(LocalDateTime receiptTime) { this.receiptTime = receiptTime; }

    public String getReceiptFileUrl() { return receiptFileUrl; }
    public void setReceiptFileUrl(String receiptFileUrl) { this.receiptFileUrl = receiptFileUrl; }

    public LocalDateTime getConfirmTime() { return confirmTime; }

    @Column(name = "create_time")
    private LocalDateTime createTime;

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
