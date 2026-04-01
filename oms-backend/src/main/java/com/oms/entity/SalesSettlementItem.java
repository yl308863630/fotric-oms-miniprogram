package com.oms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sales_settlement_items")
public class SalesSettlementItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private SalesSettlementRecord settlement;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_bill_no")
    private String invoiceBillNo;

    @Column(name = "reconciliation_id")
    private Long reconciliationId;

    @Column(name = "reconciliation_bill_no")
    private String reconciliationBillNo;

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
