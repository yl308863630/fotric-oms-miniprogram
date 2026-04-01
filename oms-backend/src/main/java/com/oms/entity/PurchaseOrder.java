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

    // 整单主单ID
    @Column(name = "master_id")
    private Long masterId;

    // 执行分配ID
    @Column(name = "allocation_id")
    private Long allocationId;

    // 来源销售订单ID
    @Column(name = "source_sales_order_id")
    private Long sourceSalesOrderId;

    // 订单流转跳点ID
    @Column(name = "order_flow_hop_id")
    private Long orderFlowHopId;

    // 财务流转跳点ID
    @Column(name = "finance_flow_hop_id")
    private Long financeFlowHopId;

    @Column(name = "merge_selection_key")
    private String mergeSelectionKey;

    @Column(name = "merged_sales_order_ids", columnDefinition = "TEXT")
    private String mergedSalesOrderIds;

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

    // 商务ERP录单状态
    @Column(name = "erp_entry_status")
    private String erpEntryStatus;

    // 商务ERP录单截图
    @Column(name = "erp_entry_screenshot_url")
    private String erpEntryScreenshotUrl;

    // 商务ERP录单人
    @Column(name = "erp_entry_operator")
    private String erpEntryOperator;

    // 商务ERP录单时间
    @Column(name = "erp_entry_time")
    private LocalDateTime erpEntryTime;

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

    @Transient
    private Boolean erpEntryCanEdit;

    @Transient
    private Boolean erpEntryCanPreviewScreenshot;

    @Transient
    private String contractNo;

    @Transient
    private java.util.List<java.util.Map<String, Object>> detailRows;
}
