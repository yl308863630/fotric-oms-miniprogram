package com.oms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_input_invoice_items")
public class PurchaseInputInvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private PurchaseInputInvoiceRecord invoice;

    @Column(name = "reconciliation_id", nullable = false)
    private Long reconciliationId;

    @Column(name = "reconciliation_bill_no")
    private String reconciliationBillNo;

    private String supplier;

    @Column(name = "line_amount", nullable = false)
    private BigDecimal lineAmount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (lineAmount == null) {
            lineAmount = BigDecimal.ZERO;
        }
    }
}
