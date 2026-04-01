package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sales_serial_items", indexes = {
        @Index(name = "idx_sales_serial_item_master", columnList = "master_id"),
        @Index(name = "idx_sales_serial_item_order", columnList = "sales_order_id"),
        @Index(name = "idx_sales_serial_item_allocation", columnList = "allocation_id"),
        @Index(name = "idx_sales_serial_item_batch", columnList = "batch_id"),
        @Index(name = "idx_sales_serial_item_status", columnList = "serial_status")
})
public class SalesSerialItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "master_id")
    private Long masterId;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "sn_code", nullable = false, unique = true)
    private String snCode;

    @Column(name = "product_model")
    private String productModel;

    @Column(name = "serial_status")
    private String serialStatus;

    @Column(name = "bind_time")
    private LocalDateTime bindTime;

    @Column(name = "unbind_time")
    private LocalDateTime unbindTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (serialStatus == null || serialStatus.isBlank()) serialStatus = "CREATED";
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
