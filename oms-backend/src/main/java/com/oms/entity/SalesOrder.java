package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sales_orders_new")
public class SalesOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 平台名字（当前层甲方/客户名）
    @Column(name = "platform_name")
    private String platformName;

    // 真实业务甲方抬头（与平台/结算方字段分离，避免跨账号指派时串值）
    @Column(name = "party_a_title")
    private String partyATitle;

    /** 顶层客户名（仅工业电商侧需要，结算方式及期限显示；链式订单不继承，其他用户合同不显示） */
    @Column(name = "top_level_customer_name")
    private String topLevelCustomerName;

    // 平台订单号
    @Column(name = "platform_order_no")
    private String platformOrderNo;

    // 工业电商销售订单号 (YYMMDD+真实姓名首字母+01)
    @Column(name = "oms_order_no")
    private String omsOrderNo;

    // 整单主单ID
    @Column(name = "master_id")
    private Long masterId;

    // 执行分配ID
    @Column(name = "allocation_id")
    private Long allocationId;

    // 行号
    @Column(name = "line_no")
    private Integer lineNo;

    // 行状态
    @Column(name = "line_status")
    private String lineStatus;

    // 是否主单主行
    @Column(name = "is_master_primary_line")
    private Boolean isMasterPrimaryLine;

    // 订单时间 (YY-MM-DD)
    @Column(name = "order_date")
    private LocalDate orderDate;

    // 物料号
    private String materialNo;

    // 平台SKU
    private String platformSku;

    // 型号
    private String model;

    // 商品名称（允许手工录入，避免型号未命中商品库时合同名称为空）
    private String productName;

    // 产品配置
    private String productConfig;

    // 产品保修期
    private String warrantyPeriod;

    // 数量
    private Integer quantity;

    // 含税订单金额
    private BigDecimal taxIncludedPrice;

    // 含税总价
    private BigDecimal taxIncludedTotal;
    
    // 订单金额 (兼容旧字段)
    @Column(columnDefinition = "DECIMAL(19,2) DEFAULT 0.00", nullable = false)
    private BigDecimal amount;
    
    // 购买方名称
    private String buyerName;
    
    // 订单号
    private String orderNo;

    // 平台发票抬头
    private String invoiceTitle;

    // 平台最终客户抬头
    private String finalCustomerTitle;

    // 代运营主体抬头
    private String operationEntityTitle;

    // 工业电商业务员 (User ID)
    private Long ecommerceSalesId;
    
    // 工业电商业务员姓名 (冗余存储方便查询)
    private String ecommerceSalesName;

    // 支付方式 (账期、背靠背或者全款)
    private String paymentMethod;

    // 订单类型 (自营或者第三方订单)
    private String orderType;

    // 收货人
    private String receiverName;

    // 收货人电话
    private String receiverPhone;

    // 收货人地址
    private String receiverAddress;

    // 交付方
    private String deliveryParty;

    // 出货方
    private String shippingParty;

    // 结算单号
    private String settlementNo;

    // 结算单附件URL
    private String settlementUrl;

    // 平台对账单号
    private String platformReconciliationNo;

    // 平台对账单附件URL
    private String platformReconciliationUrl;

    // 支付状态
    private String paymentStatus;

    // 平台回款状态
    private String platformRefundStatus;

    // 平台回款附件URL
    private String platformRefundUrl;

    // 预计回款时间
    @Column(name = "expected_refund_date")
    private LocalDate expectedRefundDate;

    // 送货单URL (上传平台送货单)
    private String deliveryNoteUrl;

    // 签收单URL
    private String receiptUrl;

    /** 是否需要签收单回传（由 sonmin 等在发货时勾选，rxkj-ck/rxkj-sw 可在「需签收单（待上传）」中查看并上传） */
    @Column(name = "need_receipt_slip")
    private Boolean needReceiptSlip;

    // 箱唛URLs (多个箱唛文件，用逗号分隔)
    private String boxLabelUrls;

    /** 送货单打印数量（发货要求） */
    @Column(name = "delivery_note_print_quantity")
    private Integer deliveryNotePrintQuantity;
    /** 哪些快递不让用（发货要求） */
    @Column(name = "forbidden_couriers", columnDefinition = "TEXT")
    private String forbiddenCouriers;
    /** 是否打印箱唛 */
    @Column(name = "print_box_label")
    private Boolean printBoxLabel;
    /** 是否打印128条码 */
    @Column(name = "print_barcode_128")
    private Boolean printBarcode128;

    // SN编码
    private String snCode;

    // 签收状态
    private String receiptStatus;

    /** 签收/回单流程阶段：PENDING_MOTHER / MOTHER_DELIVERED / RETURN_DELIVERED / SELF_VEHICLE_PENDING_RECEIPT / FLOW_COMPLETED */
    @Column(name = "receipt_flow_stage")
    private String receiptFlowStage;

    /** 母单送达时间 */
    @Column(name = "mother_delivered_at")
    private LocalDateTime motherDeliveredAt;

    /** 回单送达时间 */
    @Column(name = "return_delivered_at")
    private LocalDateTime returnDeliveredAt;

    /** 冗余配送方式，便于销售列表与 Dashboard 直接筛选 */
    @Column(name = "delivery_method")
    private String deliveryMethod;

    // 物流公司
    private String logisticsCompany;

    // 物流单号
    private String trackingNumber;

    /** 回单/签收返单物流单号（母单为 trackingNumber；回单无需在发货里再选物流公司，仅填单号） */
    @Column(name = "return_receipt_tracking_number")
    private String returnReceiptTrackingNumber;

    /** 回单物流查轨迹用手机号（顺丰等需后四位，可与订单收货电话不同） */
    @Column(name = "return_receipt_receiver_phone")
    private String returnReceiptReceiverPhone;

    // 送货车牌号（自主车辆配送时）
    private String vehiclePlate;

    // 采购订单号
    private String purchaseOrderNo;

    // 银行流水号
    private String bankFlowNo;

    // 发票号码
    private String invoiceNumber;

    // 发票文件URL
    private String invoiceUrl;

    // 首次填写发票号码的日期
    @Column(name = "invoice_issued_date")
    private LocalDate invoiceIssuedDate;

    // 首次填写甲方对账单号的日期
    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate;

    // 指派时间
    private LocalDateTime assignTime;

    // 被指派的用户名（交付方对应的用户名）
    private String assignedUsername;

    /** 上一任被指派方（指派时写入，退回时用于恢复：assignedUsername 改回此值，便于原指派方在销售列表看到已退回订单） */
    @Column(name = "previous_assigned_username")
    private String previousAssignedUsername;

    // 退回原因
    @Column(columnDefinition = "TEXT")
    private String returnReason;

    // 退回时间
    private LocalDateTime returnTime;

    // 退回人用户名
    private String returnedBy;

    // 线下销售
    private String offlineSales;

    // 线下销售合同号
    private String offlineContractNo;

    // 合同文件URL
    private String contractUrl;

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

    // 线下销售出货价
    private BigDecimal offlineShippingPrice;

    // 扣点（百分比）
    private BigDecimal deductionRate;

    // 交付方采购价
    private BigDecimal deliveryPartyPurchasePrice;

    // 签收时间
    private LocalDateTime receiptTime;

    // 交货日期
    private LocalDate deliveryDate;

    // 创建用户ID
    private Long createdBy;

    // 状态 (待指派, 待确认订单, 待合同盖章, 已发货, 已对账未开票, 已开票待结算, 已结算, 已到货, 已支付, 平台已支付, 已取消)
    @Column(nullable = false)
    private String status; 

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = "待指派";
        if (orderDate == null) orderDate = LocalDate.now();
        if (amount == null) amount = BigDecimal.ZERO;
        if (buyerName == null) buyerName = "";
        if (orderNo == null) orderNo = "";
        if (omsOrderNo == null) omsOrderNo = "";
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (taxIncludedPrice == null) {
            taxIncludedPrice = BigDecimal.ZERO;
        }
        if (taxIncludedTotal == null) {
            taxIncludedTotal = BigDecimal.ZERO;
        }
        if (deliveryPartyPurchasePrice == null) {
            deliveryPartyPurchasePrice = BigDecimal.ZERO;
        }
    }

    @Column(columnDefinition = "TEXT")
    private String orderDetails;

    @Transient
    private Boolean erpEntryCanEdit;

    @Transient
    private Boolean erpEntryCanPreviewScreenshot;

    @Transient
    private Boolean expectedRefundOverdue;

    @Transient
    private String expectedRefundRuleDescription;

    @Transient
    private String expectedRefundPendingReason;
}
