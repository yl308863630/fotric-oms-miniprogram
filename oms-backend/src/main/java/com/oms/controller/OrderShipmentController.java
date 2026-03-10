package com.oms.controller;

import com.oms.entity.OrderShipment;
import com.oms.service.OrderShipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public OrderShipment save(@RequestBody OrderShipment shipment) {
        return orderShipmentService.saveShipment(shipment);
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
}
