package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "order_shipments")
public class OrderShipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "delivery_note_url")
    private String deliveryNoteUrl;

    @Column(name = "need_receipt_return")
    private Boolean needReceiptReturn;

    @Column(name = "print_quantity")
    private Integer printQuantity;

    @Column(name = "forbidden_couriers", columnDefinition = "TEXT")
    private String forbiddenCouriers;

    @Column(name = "print_box_label")
    private Boolean printBoxLabel;

    @Column(name = "print_barcode_128")
    private Boolean printBarcode128;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "shipping_contact")
    private String shippingContact;

    @Column(name = "shipping_phone")
    private String shippingPhone;

    @Column(name = "delivery_method")
    private String deliveryMethod;

    @Column(name = "logistics_company")
    private String logisticsCompany;

    @Column(name = "tracking_number")
    private String trackingNumber;

    /** 回单物流单号（与 sales_orders.return_receipt_tracking_number 同步） */
    @Column(name = "return_receipt_tracking_number")
    private String returnReceiptTrackingNumber;

    /** 回单收件人手机（查轨迹用后四位） */
    @Column(name = "return_receipt_receiver_phone")
    private String returnReceiptReceiverPhone;

    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    @Column(name = "logistics_contact")
    private String logisticsContact;

    @Column(name = "logistics_phone")
    private String logisticsPhone;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "receipt_time")
    private LocalDateTime receiptTime;

    @Column(name = "box_label_urls", columnDefinition = "TEXT")
    private String boxLabelUrls;

    // SN编码
    @Column(name = "sn_code")
    private String snCode;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (needReceiptReturn == null) needReceiptReturn = false;
        if (printQuantity == null) printQuantity = 1;
        if (printBoxLabel == null) printBoxLabel = false;
        if (printBarcode128 == null) printBarcode128 = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
