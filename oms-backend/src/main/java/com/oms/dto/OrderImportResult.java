package com.oms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 PDF/图片解析并模糊匹配后的订单导入结果，供前端预填「新建销售订单」。
 * 随 oms-backend 打在同一 JAR 内，部署 ECS 即可使用。
 */
@Data
public class OrderImportResult {
    /** 是否解析成功（若仅支持 PDF 而上传了图片，可能为 false） */
    private boolean success;
    /** 提示信息（如：仅支持 PDF；或解析到的摘要） */
    private String message;
    /** 原始文本长度（便于排查） */
    private int rawTextLength;

    /** 建议 甲方订单号（平台订单号） */
    private String platformOrderNo;
    /** 建议 订单日期（签约日期/下单日期） */
    private String orderDate;
    /** 建议 甲方/客户名称（已模糊匹配到合作方时为其 title/name） */
    private String partyATitle;
    /** 匹配到的合作方 ID（若有） */
    private Long matchedPartnerId;
    /** 客户匹配置信度 0~1 */
    private double partnerMatchScore;

    /** 建议 型号 */
    private String model;
    /** 建议 SKU（甲方SKU） */
    private String platformSku;
    /** 建议 商品名称（来自我的商品） */
    private String productName;
    /** 匹配到的商品 ID（若有） */
    private Long matchedProductId;
    /** 商品匹配置信度 0~1 */
    private double productMatchScore;

    /** 建议 数量 */
    private Integer quantity;
    /** 建议 含税单价 */
    private BigDecimal taxIncludedPrice;
    /** 建议 含税总价 */
    private BigDecimal taxIncludedTotal;
    /** 建议 交货日期（送达日期/应到货日期） */
    private String deliveryDate;
    /** 建议 发货要求 */
    private String shippingRequirement;
    /** 建议 支付方式 */
    private String paymentMethod;
    /** 建议 账期天数 */
    private Integer paymentTermDays;

    /** 建议 收货人 */
    private String receiverName;
    /** 建议 收货电话 */
    private String receiverPhone;
    /** 建议 收货地址 */
    private String receiverAddress;

    /** 用户信息维护可用：名称（简称/显示名） */
    private String partnerName;
    /** 用户信息维护可用：抬头信息 */
    private String title;
    /** 用户信息维护可用：税号 */
    private String taxNumber;
    /** 用户信息维护可用：开户行 */
    private String bankName;
    /** 用户信息维护可用：银行账号 */
    private String bankAccount;
    /** 用户信息维护可用：开户行地址 */
    private String bankAddress;
    /** 用户信息维护可用：联系人 */
    private String contactPerson;
    /** 用户信息维护可用：联系电话 */
    private String contactPhone;
    /** 用户信息维护可用：邮箱 */
    private String email;

    /** 多条商品时（一个 PDF 多行） */
    private List<OrderImportLine> lines = new ArrayList<>();

    @Data
    public static class OrderImportLine {
        private String platformOrderNo;
        private String orderDate;
        private String partyATitle;
        private String platformSku;
        private String model;
        private String productName;
        private Integer quantity;
        private BigDecimal taxIncludedPrice;
        private BigDecimal taxIncludedTotal;
        private String deliveryDate;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private Long matchedProductId;
        private Long matchedPartnerId;
        private double productMatchScore;
        private double partnerMatchScore;
    }
}
