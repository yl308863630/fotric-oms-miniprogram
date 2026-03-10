package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 采购订单号
    @Column(name = "purchase_order_no")
    private String purchaseOrderNo;

    // 状态
    private String status;

    // 采购类型 (特采采购订单/常规采购订单)
    @Column(name = "purchase_type")
    private String purchaseType;

    // 工业电商销售订单号
    @Column(name = "oms_order_no")
    private String omsOrderNo;

    // 快递/发货单号
    @Column(name = "tracking_number")
    private String trackingNumber;

    // 业务员
    @Column(name = "sales_person")
    private String salesPerson;

    // 型号
    private String model;

    // 数量
    private Integer quantity;

    // 含税采购单价
    @Column(name = "tax_included_purchase_price")
    private BigDecimal taxIncludedPurchasePrice;

    // 含税采购总额
    @Column(name = "tax_included_purchase_total")
    private BigDecimal taxIncludedPurchaseTotal;

    // 交货日期
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    // 付款状态
    @Column(name = "payment_status")
    private String paymentStatus;

    // 对账状态
    @Column(name = "reconciliation_status")
    private String reconciliationStatus;

    // 发票号
    @Column(name = "invoice_number")
    private String invoiceNumber;

    // 供应商
    private String supplier;

    // 创建用户ID
    @Column(name = "created_by")
    private Long createdBy;

    // 制单人
    private String creator;

    // 创建时间
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (status == null) status = "待确认";
    }
}
