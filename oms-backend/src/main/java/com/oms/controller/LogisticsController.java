package com.oms.controller;

import com.oms.entity.LogisticsTrace;
import com.oms.entity.SalesOrder;
import com.oms.repository.SalesOrderRepository;
import com.oms.service.DeliveryReceiptFlowService;
import com.oms.service.LogisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/logistics")
@CrossOrigin(origins = "*")
public class LogisticsController {

    @Autowired
    private LogisticsService logisticsService;

    @Autowired
    private DeliveryReceiptFlowService deliveryReceiptFlowService;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @GetMapping("/query")
    public ResponseEntity<LogisticsTrace> queryLogistics(
            @RequestParam String company,
            @RequestParam String trackingNumber,
            @RequestParam(required = false) String checkPhoneNo) {
        LogisticsTrace result = logisticsService.queryLogistics(company, trackingNumber, checkPhoneNo);
        tryRefreshReceiptFlow(trackingNumber, result);
        return ResponseEntity.ok(result);
    }

    private void tryRefreshReceiptFlow(String trackingNumber, LogisticsTrace trace) {
        if (trackingNumber == null || trackingNumber.isBlank() || trace == null) {
            return;
        }
        if (!deliveryReceiptFlowService.traceIndicatesDelivered(trace)) {
            return;
        }
        Set<Long> orderIds = new LinkedHashSet<>();
        List<SalesOrder> motherOrders = salesOrderRepository.findByTrackingNumber(trackingNumber.trim());
        for (SalesOrder order : motherOrders) {
            if (order != null && order.getId() != null) {
                orderIds.add(order.getId());
            }
        }
        List<SalesOrder> returnOrders = salesOrderRepository.findByReturnReceiptTrackingNumber(trackingNumber.trim());
        for (SalesOrder order : returnOrders) {
            if (order != null && order.getId() != null) {
                orderIds.add(order.getId());
            }
        }
        for (Long orderId : orderIds) {
            deliveryReceiptFlowService.refreshOrderFlowById(orderId);
        }
    }
}
