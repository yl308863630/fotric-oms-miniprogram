package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "opportunities")
public class Opportunity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String customerCode; // 客户编码

    private String title; // 商机标题

    private String category; // 商机分类

    private String source; // 客户来源

    private String customerName; // 客户名称

    private String inquiryHeader; // 询价抬头

    private String orderHeader; // 下单抬头

    private String contact; // 客户联系人

    private String phone; // 客户电话

    private Long productId; // 商品ID

    private String productModel; // 商品型号

    private Integer quantity; // 商品数量

    private BigDecimal estimatedAmount; // 预计销售金额

    private String platform; // 电商平台

    private String serviceProvider; // 服务商抬头

    private String shippingChannel; // 出货渠道抬头

    private LocalDateTime expectedDate; // 预计签单日期

    private String region; // 所在地区

    private String industry; // 所属行业

    @Column(columnDefinition = "TEXT")
    private String problemSolved; // 解决哪些问题

    private String budget; // 采购预算

    private String competitor; // 竞争对手

    private String ecommerceSales; // 电商业务员

    private String offlineSales; // 线下配合销售

    private String stage; // 业务阶段

    private String deliveryPeriod; // 交货期（用于报价单，如 4-6周）

    private LocalDateTime followUpTime; // 跟进时间

    @Column(columnDefinition = "TEXT")
    private String latestFollowUpRecord; // 最新跟进记录

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
