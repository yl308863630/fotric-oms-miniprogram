package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "opportunity_products")
public class OpportunityProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "opportunity_id", nullable = false)
    private Opportunity opportunity;

    private Long productId;
    private String productName;
    private String productModel;
    private String productConfig;
    private String productCategory;
    private String productCode;
    private String unit;
    private BigDecimal standardPrice;
    private BigDecimal sellingPrice;
    private Integer quantity;
    private BigDecimal discount = BigDecimal.valueOf(100);
    private BigDecimal totalPrice;
    private String deliveryPeriod;
    private String warrantyPeriod;
    private String remarks;

    @PrePersist
    @PreUpdate
    public void calculateTotal() {
        if (sellingPrice != null && quantity != null) {
            totalPrice = sellingPrice.multiply(new BigDecimal(quantity));
            if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
                totalPrice = totalPrice.multiply(discount).divide(BigDecimal.valueOf(100));
            }
        }
    }
}