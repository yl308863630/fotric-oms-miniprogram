package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sales_order_allocations", indexes = {
        @Index(name = "idx_sales_order_allocation_master", columnList = "master_id"),
        @Index(name = "idx_sales_order_allocation_order", columnList = "sales_order_id"),
        @Index(name = "idx_sales_order_allocation_root", columnList = "root_allocation_id"),
        @Index(name = "idx_sales_order_allocation_parent", columnList = "parent_allocation_id"),
        @Index(name = "idx_sales_order_allocation_assigned_user", columnList = "assigned_user_id"),
        @Index(name = "idx_sales_order_allocation_status", columnList = "allocation_status")
})
public class SalesOrderAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "master_id", nullable = false)
    private Long masterId;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "allocation_no", nullable = false, unique = true)
    private String allocationNo;

    @Column(name = "root_allocation_id")
    private Long rootAllocationId;

    @Column(name = "parent_allocation_id")
    private Long parentAllocationId;

    @Column(name = "hop_no")
    private Integer hopNo;

    @Column(name = "assigned_company_title")
    private String assignedCompanyTitle;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "assigned_username")
    private String assignedUsername;

    @Column(name = "allocated_qty")
    private Integer allocatedQty;

    @Column(name = "planned_delivery_date")
    private LocalDate plannedDeliveryDate;

    @Column(name = "allocation_status")
    private String allocationStatus;

    @Column(name = "is_chain_tail")
    private Boolean isChainTail;

    @Column(name = "source_type")
    private String sourceType;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (hopNo == null) hopNo = 1;
        if (allocationStatus == null || allocationStatus.isBlank()) allocationStatus = "待指派";
        if (isChainTail == null) isChainTail = true;
        if (allocatedQty == null) allocatedQty = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (allocatedQty == null) allocatedQty = 0;
    }
}
