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

    /** 顶层客户名（仅工业电商侧需要，结算方式及期限显示；链式订单不继承，其他用户合同不显示） */
    @Column(name = "top_level_customer_name")
    private String topLevelCustomerName;

    // 平台订单号
    @Column(name = "platform_order_no")
    private String platformOrderNo;

    // 工业电商销售订单号 (YYMMDD+真实姓名首字母+01)
    @Column(name = "oms_order_no")
    private String omsOrderNo;

    // 订单时间 (YY-MM-DD)
    @Column(name = "order_date")
    private LocalDate orderDate;

    // 物料号
    private String materialNo;

    // 平台SKU
    private String platformSku;

    // 型号
    private String model;

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

    // 平台对账单号
    private String platformReconciliationNo;

    // 支付状态
    private String paymentStatus;

    // 平台回款状态
    private String platformRefundStatus;

    // 送货单URL (上传平台送货单)
    private String deliveryNoteUrl;

    // 签收单URL
    private String receiptUrl;

    // 箱唛URLs (多个箱唛文件，用逗号分隔)
    private String boxLabelUrls;

    // SN编码
    private String snCode;

    // 签收状态
    private String receiptStatus;

    // 物流公司
    private String logisticsCompany;

    // 物流单号
    private String trackingNumber;

    // 采购订单号
    private String purchaseOrderNo;

    // 银行流水号
    private String bankFlowNo;

    // 发票号码
    private String invoiceNumber;

    // 发票文件URL
    private String invoiceUrl;

    // 指派时间
    private LocalDateTime assignTime;

    // 被指派的用户名（交付方对应的用户名）
    private String assignedUsername;

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

    // 状态 (待指派, 待确认订单, 待合同盖章, 已发货, 已到货, 已开票, 已支付, 平台已支付, 已取消)
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
}
