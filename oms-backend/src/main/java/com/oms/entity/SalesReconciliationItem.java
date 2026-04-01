package com.oms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sales_reconciliation_items")
public class SalesReconciliationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private SalesReconciliation reconciliation;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "oms_order_no")
    private String omsOrderNo;

    @Column(name = "platform_order_no")
    private String platformOrderNo;

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "order_status")
    private String orderStatus;

    private Integer quantity;

    private String model;

    @Column(name = "product_name")
    private String productName;

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
