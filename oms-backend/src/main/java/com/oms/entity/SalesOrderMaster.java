package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sales_order_masters", indexes = {
        @Index(name = "idx_sales_order_master_root_oms", columnList = "root_oms_order_no"),
        @Index(name = "idx_sales_order_master_platform_order", columnList = "platform_order_no"),
        @Index(name = "idx_sales_order_master_party_a", columnList = "party_a_title"),
        @Index(name = "idx_sales_order_master_status", columnList = "master_status")
})
public class SalesOrderMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "master_no", nullable = false, unique = true)
    private String masterNo;

    @Column(name = "root_oms_order_no")
    private String rootOmsOrderNo;

    @Column(name = "platform_order_no")
    private String platformOrderNo;

    @Column(name = "party_a_title")
    private String partyATitle;

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "master_status")
    private String masterStatus;

    @Column(name = "assign_status")
    private String assignStatus;

    @Column(name = "contract_status")
    private String contractStatus;

    @Column(name = "finance_status")
    private String financeStatus;

    @Column(name = "total_line_count")
    private Integer totalLineCount;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "created_by")
    private Long createdBy;

    private String creator;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (masterStatus == null || masterStatus.isBlank()) masterStatus = "待执行";
        if (assignStatus == null || assignStatus.isBlank()) assignStatus = "未指派";
        if (contractStatus == null || contractStatus.isBlank()) contractStatus = "未签约";
        if (financeStatus == null || financeStatus.isBlank()) financeStatus = "未开始";
        if (totalLineCount == null) totalLineCount = 0;
        if (totalQuantity == null) totalQuantity = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (totalLineCount == null) totalLineCount = 0;
        if (totalQuantity == null) totalQuantity = 0;
    }
}
