package com.oms.service;

import com.oms.entity.OrderShipment;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.OrderShipmentRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderShipmentService {

    @Autowired
    private OrderShipmentRepository orderShipmentRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private UserRepository userRepository;

    public Page<OrderShipment> getAllShipments(Pageable pageable) {
        return orderShipmentRepository.findAll(pageable);
    }

    public Optional<OrderShipment> getShipmentById(Long id) {
        return orderShipmentRepository.findById(id);
    }

    public Optional<OrderShipment> getShipmentBySalesOrderId(Long salesOrderId) {
        return orderShipmentRepository.findBySalesOrderId(salesOrderId);
    }

    @Transactional
    public OrderShipment saveShipment(OrderShipment shipment) {
        // 检查是否已存在该订单的发货信息
        Optional<OrderShipment> existingShipment = orderShipmentRepository.findBySalesOrderId(shipment.getSalesOrderId());
        if (existingShipment.isPresent()) {
            // 如果存在，更新现有记录
            OrderShipment existing = existingShipment.get();
            shipment.setId(existing.getId());
            System.out.println("Updating existing shipment with ID: " + existing.getId());
        }
        
        OrderShipment savedShipment = orderShipmentRepository.save(shipment);
        
        Optional<SalesOrder> salesOrderOpt = salesOrderRepository.findById(shipment.getSalesOrderId());
        if (salesOrderOpt.isPresent()) {
            SalesOrder salesOrder = salesOrderOpt.get();
            salesOrder.setDeliveryNoteUrl(shipment.getDeliveryNoteUrl());
            salesOrder.setReceiptUrl(shipment.getReceiptUrl());
            salesOrder.setReceiptTime(shipment.getReceiptTime());
            salesOrder.setBoxLabelUrls(shipment.getBoxLabelUrls());
            salesOrder.setLogisticsCompany(shipment.getLogisticsCompany());
            salesOrder.setTrackingNumber(shipment.getTrackingNumber());
            salesOrder.setSnCode(shipment.getSnCode());
            // 不自动设置状态为已发货，让前端决定
            salesOrderRepository.save(salesOrder);
        }

        String operatorName = getCurrentOperatorName();
        String details = buildShipmentDetails(savedShipment);
        operationLogService.log(operatorName, "发货操作", "SALES_ORDER", String.valueOf(shipment.getSalesOrderId()), details);
        
        return savedShipment;
    }

    @Transactional
    public void deleteShipment(Long id) {
        if (id != null) {
            orderShipmentRepository.deleteById(id);
        }
    }

    private String getCurrentOperatorName() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "system";
        return userRepository.findByUsername(username)
                .map(u -> (u.getRealName() != null && !u.getRealName().isBlank()) ? u.getRealName() : u.getUsername())
                .orElse(username);
    }

    private String buildShipmentDetails(OrderShipment s) {
        StringBuilder sb = new StringBuilder("发货");
        if (s.getLogisticsCompany() != null && !s.getLogisticsCompany().isBlank()) {
            sb.append("，物流：").append(s.getLogisticsCompany());
        }
        if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) {
            sb.append("，单号：").append(s.getTrackingNumber());
        }
        if (s.getSnCode() != null && !s.getSnCode().isBlank()) {
            sb.append("，SN：").append(s.getSnCode());
        }
        return sb.toString();
    }
}
