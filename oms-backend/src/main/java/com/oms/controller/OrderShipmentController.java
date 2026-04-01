package com.oms.controller;

import com.oms.entity.OrderShipment;
import com.oms.service.OrderShipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/order-shipments")
public class OrderShipmentController {

    @Autowired
    private OrderShipmentService orderShipmentService;

    @GetMapping
    public Page<OrderShipment> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderShipmentService.getAllShipments(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderShipment> getById(@PathVariable Long id) {
        return orderShipmentService.getShipmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 按销售订单查发货单：无记录时返回 200 + null，避免前端 404 报错 */
    @GetMapping("/sales-order/{salesOrderId}")
    public ResponseEntity<OrderShipment> getBySalesOrderId(@PathVariable Long salesOrderId) {
        return orderShipmentService.getShipmentBySalesOrderId(salesOrderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok().body(null));
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody OrderShipment shipment) {
        try {
            OrderShipment saved = orderShipmentService.saveShipment(shipment);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "发货保存失败: " + msg));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<?> saveBatch(@RequestBody(required = false) BatchShipmentRequest request) {
        try {
            if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "批量发货保存失败: 缺少商品行"));
            }
            OrderShipment sharedShipment = new OrderShipment();
            sharedShipment.setDeliveryNoteUrl(request.getDeliveryNoteUrl());
            sharedShipment.setNeedReceiptReturn(request.getNeedReceiptReturn());
            sharedShipment.setPrintQuantity(request.getPrintQuantity());
            sharedShipment.setForbiddenCouriers(request.getForbiddenCouriers());
            sharedShipment.setPrintBoxLabel(request.getPrintBoxLabel());
            sharedShipment.setPrintBarcode128(request.getPrintBarcode128());
            sharedShipment.setShippingAddress(request.getShippingAddress());
            sharedShipment.setShippingContact(request.getShippingContact());
            sharedShipment.setShippingPhone(request.getShippingPhone());
            sharedShipment.setDeliveryMethod(request.getDeliveryMethod());
            sharedShipment.setLogisticsCompany(request.getLogisticsCompany());
            sharedShipment.setTrackingNumber(request.getTrackingNumber());
            sharedShipment.setReturnReceiptTrackingNumber(request.getReturnReceiptTrackingNumber());
            sharedShipment.setReturnReceiptReceiverPhone(request.getReturnReceiptReceiverPhone());
            sharedShipment.setVehiclePlate(request.getVehiclePlate());
            sharedShipment.setLogisticsContact(request.getLogisticsContact());
            sharedShipment.setLogisticsPhone(request.getLogisticsPhone());
            sharedShipment.setBoxLabelUrls(request.getBoxLabelUrls());

            List<Long> orderIds = new ArrayList<>();
            Map<Long, List<String>> serialCodesByOrderId = new LinkedHashMap<>();
            for (BatchShipmentItem item : request.getItems()) {
                if (item == null || item.getSalesOrderId() == null) {
                    continue;
                }
                orderIds.add(item.getSalesOrderId());
                serialCodesByOrderId.put(item.getSalesOrderId(), item.getSnCodes() == null ? List.of() : item.getSnCodes());
            }
            return ResponseEntity.ok(orderShipmentService.saveBatchShipments(sharedShipment, serialCodesByOrderId, orderIds));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "批量发货保存失败: " + msg));
        }
    }

    @PutMapping("/{id}")
    public OrderShipment update(@PathVariable Long id, @RequestBody OrderShipment shipment) {
        shipment.setId(id);
        return orderShipmentService.saveShipment(shipment);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderShipmentService.deleteShipment(id);
        return ResponseEntity.ok().build();
    }

    public static class BatchShipmentRequest {
        private String deliveryNoteUrl;
        private Boolean needReceiptReturn;
        private Integer printQuantity;
        private String forbiddenCouriers;
        private Boolean printBoxLabel;
        private Boolean printBarcode128;
        private String shippingAddress;
        private String shippingContact;
        private String shippingPhone;
        private String deliveryMethod;
        private String logisticsCompany;
        private String trackingNumber;
        private String returnReceiptTrackingNumber;
        private String returnReceiptReceiverPhone;
        private String vehiclePlate;
        private String logisticsContact;
        private String logisticsPhone;
        private String boxLabelUrls;
        private List<BatchShipmentItem> items;

        public String getDeliveryNoteUrl() { return deliveryNoteUrl; }
        public void setDeliveryNoteUrl(String deliveryNoteUrl) { this.deliveryNoteUrl = deliveryNoteUrl; }
        public Boolean getNeedReceiptReturn() { return needReceiptReturn; }
        public void setNeedReceiptReturn(Boolean needReceiptReturn) { this.needReceiptReturn = needReceiptReturn; }
        public Integer getPrintQuantity() { return printQuantity; }
        public void setPrintQuantity(Integer printQuantity) { this.printQuantity = printQuantity; }
        public String getForbiddenCouriers() { return forbiddenCouriers; }
        public void setForbiddenCouriers(String forbiddenCouriers) { this.forbiddenCouriers = forbiddenCouriers; }
        public Boolean getPrintBoxLabel() { return printBoxLabel; }
        public void setPrintBoxLabel(Boolean printBoxLabel) { this.printBoxLabel = printBoxLabel; }
        public Boolean getPrintBarcode128() { return printBarcode128; }
        public void setPrintBarcode128(Boolean printBarcode128) { this.printBarcode128 = printBarcode128; }
        public String getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
        public String getShippingContact() { return shippingContact; }
        public void setShippingContact(String shippingContact) { this.shippingContact = shippingContact; }
        public String getShippingPhone() { return shippingPhone; }
        public void setShippingPhone(String shippingPhone) { this.shippingPhone = shippingPhone; }
        public String getDeliveryMethod() { return deliveryMethod; }
        public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
        public String getLogisticsCompany() { return logisticsCompany; }
        public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
        public String getReturnReceiptTrackingNumber() { return returnReceiptTrackingNumber; }
        public void setReturnReceiptTrackingNumber(String returnReceiptTrackingNumber) { this.returnReceiptTrackingNumber = returnReceiptTrackingNumber; }
        public String getReturnReceiptReceiverPhone() { return returnReceiptReceiverPhone; }
        public void setReturnReceiptReceiverPhone(String returnReceiptReceiverPhone) { this.returnReceiptReceiverPhone = returnReceiptReceiverPhone; }
        public String getVehiclePlate() { return vehiclePlate; }
        public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }
        public String getLogisticsContact() { return logisticsContact; }
        public void setLogisticsContact(String logisticsContact) { this.logisticsContact = logisticsContact; }
        public String getLogisticsPhone() { return logisticsPhone; }
        public void setLogisticsPhone(String logisticsPhone) { this.logisticsPhone = logisticsPhone; }
        public String getBoxLabelUrls() { return boxLabelUrls; }
        public void setBoxLabelUrls(String boxLabelUrls) { this.boxLabelUrls = boxLabelUrls; }
        public List<BatchShipmentItem> getItems() { return items; }
        public void setItems(List<BatchShipmentItem> items) { this.items = items; }
    }

    public static class BatchShipmentItem {
        private Long salesOrderId;
        private List<String> snCodes;

        public Long getSalesOrderId() { return salesOrderId; }
        public void setSalesOrderId(Long salesOrderId) { this.salesOrderId = salesOrderId; }
        public List<String> getSnCodes() { return snCodes; }
        public void setSnCodes(List<String> snCodes) { this.snCodes = snCodes; }
    }
}
