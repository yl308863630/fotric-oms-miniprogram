package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.entity.SalesOrderAllocation;
import com.oms.entity.SalesSerialItem;
import com.oms.repository.SalesOrderAllocationRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.SalesSerialItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SalesSerialItemService {
    @Autowired
    private SalesSerialItemRepository salesSerialItemRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderAllocationRepository salesOrderAllocationRepository;

    public List<SalesSerialItem> listSerialItems(String snCode, Long masterId, Long salesOrderId, Long allocationId, Long batchId) {
        if (snCode != null && !snCode.isBlank()) {
            SalesSerialItem item = salesSerialItemRepository.findBySnCode(snCode.trim()).orElse(null);
            if (item == null) {
                return new ArrayList<>();
            }
            List<SalesSerialItem> result = new ArrayList<>();
            result.add(item);
            return result;
        }
        if (allocationId != null) {
            return salesSerialItemRepository.findByAllocationIdOrderByIdAsc(allocationId);
        }
        if (salesOrderId != null) {
            return salesSerialItemRepository.findBySalesOrderIdOrderByIdAsc(salesOrderId);
        }
        if (masterId != null) {
            return salesSerialItemRepository.findByMasterIdOrderByIdAsc(masterId);
        }
        if (batchId != null) {
            return salesSerialItemRepository.findByBatchIdOrderByIdAsc(batchId);
        }
        return new ArrayList<>();
    }

    @Transactional
    public List<SalesSerialItem> batchCreate(Long masterId, Long salesOrderId, Long allocationId,
                                             String productModel, List<String> snCodes) {
        if (salesOrderId == null) {
            throw new RuntimeException("salesOrderId 不能为空");
        }
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new RuntimeException("销售订单不存在"));
        SalesOrderAllocation allocation = null;
        if (allocationId != null) {
            allocation = salesOrderAllocationRepository.findById(allocationId)
                    .orElseThrow(() -> new RuntimeException("分配记录不存在"));
        } else if (order.getAllocationId() != null) {
            allocation = salesOrderAllocationRepository.findById(order.getAllocationId()).orElse(null);
            if (allocation != null) {
                allocationId = allocation.getId();
            }
        }
        if (masterId == null) {
            masterId = order.getMasterId();
        }
        if (productModel == null || productModel.isBlank()) {
            productModel = order.getModel();
        }
        LinkedHashSet<String> uniqueCodes = new LinkedHashSet<>();
        for (String code : snCodes == null ? List.<String>of() : snCodes) {
            if (code != null && !code.trim().isEmpty()) {
                uniqueCodes.add(code.trim());
            }
        }
        if (uniqueCodes.isEmpty()) {
            throw new RuntimeException("请至少提供一个有效的 SN");
        }
        int existingCount = allocationId != null
                ? salesSerialItemRepository.findByAllocationIdOrderByIdAsc(allocationId).size()
                : salesSerialItemRepository.findBySalesOrderIdOrderByIdAsc(salesOrderId).size();
        int targetQty = allocation != null && allocation.getAllocatedQty() != null
                ? allocation.getAllocatedQty()
                : (order.getQuantity() == null ? 0 : order.getQuantity());
        if (targetQty > 0 && existingCount + uniqueCodes.size() > targetQty) {
            throw new RuntimeException("SN 数量超出当前订单/分配数量，当前可新增 " + Math.max(targetQty - existingCount, 0) + " 个");
        }

        List<SalesSerialItem> created = new ArrayList<>();
        for (String snCode : uniqueCodes) {
            if (salesSerialItemRepository.existsBySnCode(snCode)) {
                throw new RuntimeException("SN 已存在: " + snCode);
            }
            SalesSerialItem item = new SalesSerialItem();
            item.setMasterId(masterId);
            item.setSalesOrderId(salesOrderId);
            item.setAllocationId(allocationId);
            item.setProductModel(productModel);
            item.setSnCode(snCode);
            item.setSerialStatus(allocationId != null ? "ASSIGNED" : "CREATED");
            if (allocationId != null) {
                item.setBindTime(LocalDateTime.now());
            }
            created.add(salesSerialItemRepository.save(item));
        }
        return created;
    }

    @Transactional
    public void deleteSerialItem(Long id) {
        SalesSerialItem item = salesSerialItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SN 记录不存在"));
        salesSerialItemRepository.delete(item);
    }
}
