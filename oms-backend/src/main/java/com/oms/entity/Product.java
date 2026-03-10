package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String code;

    private String materialNo;

    private String brand;

    private String model;

    private String specs;

    private String productConfig;

    private String warrantyPeriod;

    private String unit;

    private String category;

    private BigDecimal price;

    private Integer stock;

    private String image;

    private String remark;

    private Boolean isActive;

    private String barcode;

    private String competitorLink;

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
