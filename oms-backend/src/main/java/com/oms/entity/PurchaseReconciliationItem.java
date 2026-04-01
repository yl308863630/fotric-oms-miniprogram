package com.oms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_reconciliation_items")
public class PurchaseReconciliationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private PurchaseReconciliation reconciliation;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "purchase_order_no")
    private String purchaseOrderNo;

    @Column(name = "oms_order_no")
    private String omsOrderNo;

    private String supplier;

    @Column(name = "order_status")
    private String orderStatus;

    private Integer quantity;

    private String model;

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
