package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contracts")
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_no", unique = true, nullable = false)
    private String contractNo;

    @Column(name = "name")
    private String name;

    @Column(name = "party_a")
    private String partyA;

    @Column(name = "party_b")
    private String partyB;

    @Column(name = "sales_order_id")
    private Long salesOrderId;

    @Column(name = "master_id")
    private Long masterId;

    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "contract_scope")
    private String contractScope;

    @Column(name = "merge_selection_key")
    private String mergeSelectionKey;

    @Column(name = "merged_sales_order_ids", columnDefinition = "TEXT")
    private String mergedSalesOrderIds;

    @Column(name = "sales_id")
    private Long salesId;

    @Column(name = "sales_name")
    private String salesName;

    @Column(name = "party_b_representative")
    private String partyBRepresentative;

    @Column(name = "party_a_name")
    private String partyAName;

    @Column(name = "party_a_address", columnDefinition = "TEXT")
    private String partyAAddress;

    @Column(name = "party_a_bank")
    private String partyABank;

    @Column(name = "party_a_account")
    private String partyAAccount;

    @Column(name = "party_a_tax_no")
    private String partyATaxNo;

    @Column(name = "party_a_phone")
    private String partyAPhone;

    @Column(name = "party_b_name")
    private String partyBName;

    @Column(name = "party_b_address", columnDefinition = "TEXT")
    private String partyBAddress;

    @Column(name = "party_b_bank")
    private String partyBBank;

    @Column(name = "party_b_account")
    private String partyBAccount;

    @Column(name = "party_b_tax_no")
    private String partyBTaxNo;

    @Column(name = "party_b_phone")
    private String partyBPhone;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_model")
    private String productModel;

    @Column(name = "material_no")
    private String materialNo;

    @Column(name = "product_config")
    private String productConfig;

    @Column(name = "warranty_period")
    private String warrantyPeriod;

    @Column(name = "platform_name")
    private String platformName;

    /** 结算方式及期限中显示的客户名（仅工业电商合同有值=顶层客户名；其他为空，占位符替换为空不显示） */
    @Column(name = "settlement_party_name")
    private String settlementPartyName;

    @Column(name = "party_a_order_no")
    private String partyAOrderNo;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "delivery_cycle")
    private Integer deliveryCycle;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = true)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = true)
    private BigDecimal totalAmount;

    @Column(name = "amount", precision = 15, scale = 2, nullable = true)
    private BigDecimal amount;

    @Column(name = "amount_cn", columnDefinition = "TEXT")
    private String amountCn;

    @Column(name = "delivery_method")
    private String deliveryMethod;

    @Column(name = "sign_date")
    private LocalDate signDate;

    @Column(name = "status")
    private String status;

    @Column(name = "template_url")
    private String templateUrl;

    @Column(name = "generated_url")
    private String generatedUrl;

    @Column(name = "signed_url")
    private String signedUrl;

    @Column(name = "protected_pdf_url")
    private String protectedPdfUrl;

    @Column(name = "protected_image_url")
    private String protectedImageUrl;

    @Column(name = "party_a_signed")
    private Boolean partyASigned;

    @Column(name = "party_b_signed")
    private Boolean partyBSigned;

    @Column(name = "party_a_signed_time")
    private LocalDateTime partyASignedTime;

    @Column(name = "party_b_signed_time")
    private LocalDateTime partyBSignedTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = "草稿";
        if (partyASigned == null) partyASigned = false;
        if (partyBSigned == null) partyBSigned = false;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (amount == null) amount = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (amount == null) amount = BigDecimal.ZERO;
    }
}
