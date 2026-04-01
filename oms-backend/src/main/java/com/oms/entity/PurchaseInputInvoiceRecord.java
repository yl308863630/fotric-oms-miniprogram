package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "purchase_input_invoices_new")
public class PurchaseInputInvoiceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_no", nullable = false, unique = true)
    private String billNo;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    private String supplier;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private String status;

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
    private List<PurchaseInputInvoiceItem> items;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "已收票";
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        if (invoiceDate == null) {
            invoiceDate = LocalDate.now();
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            invoiceNumber = billNo;
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
