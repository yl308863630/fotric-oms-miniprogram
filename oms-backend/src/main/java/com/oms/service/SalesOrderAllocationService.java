package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.entity.SalesOrderAllocation;
import com.oms.entity.SalesOrderMaster;
import com.oms.entity.SalesSerialItem;
import com.oms.repository.SalesOrderMasterRepository;
import com.oms.repository.SalesOrderAllocationRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.SalesSerialItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderAllocationService {
    @Autowired
    private SalesOrderAllocationRepository salesOrderAllocationRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderMasterRepository salesOrderMasterRepository;

    @Autowired
    private SalesSerialItemRepository salesSerialItemRepository;

    public List<SalesOrderAllocation> listAllocations(String allocationNo, Long masterId, Long salesOrderId, Long rootAllocationId) {
        if (allocationNo != null && !allocationNo.isBlank()) {
            SalesOrderAllocation allocation = salesOrderAllocationRepository.findByAllocationNo(allocationNo.trim()).orElse(null);
            if (allocation == null) {
                return new ArrayList<>();
            }
            List<SalesOrderAllocation> result = new ArrayList<>();
            result.add(allocation);
            return result;
        }
        if (masterId != null) {
            return salesOrderAllocationRepository.findByMasterIdOrderByIdAsc(masterId);
        }
        if (salesOrderId != null) {
            return salesOrderAllocationRepository.findBySalesOrderIdOrderByIdAsc(salesOrderId);
        }
        if (rootAllocationId != null) {
            return salesOrderAllocationRepository.findByRootAllocationIdOrderByIdAsc(rootAllocationId);
        }
        return new ArrayList<>();
    }

    public SalesOrderAllocation getAllocation(Long id) {
        return salesOrderAllocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分配记录不存在"));
    }

    public Map<String, Object> getAllocationDetail(Long id) {
        SalesOrderAllocation allocation = getAllocation(id);
        SalesOrder order = allocation.getSalesOrderId() == null ? null
                : salesOrderRepository.findById(allocation.getSalesOrderId()).orElse(null);
        SalesOrderMaster master = allocation.getMasterId() == null ? null
                : salesOrderMasterRepository.findById(allocation.getMasterId()).orElse(null);
        List<SalesOrderAllocation> children = salesOrderAllocationRepository.findByParentAllocationIdOrderByIdAsc(id);
        List<SalesSerialItem> serialItems = salesSerialItemRepository.findByAllocationIdOrderByIdAsc(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allocation", allocation);
        result.put("order", order);
        result.put("master", master);
        result.put("children", children);
        result.put("serialItems", serialItems);
        result.put("summary", buildSummary(allocation, children, serialItems));
        return result;
    }

    private Map<String, Object> buildSummary(SalesOrderAllocation allocation, List<SalesOrderAllocation> children,
                                             List<SalesSerialItem> serialItems) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("allocationId", allocation.getId());
        summary.put("allocationNo", allocation.getAllocationNo());
        summary.put("allocatedQty", allocation.getAllocatedQty());
        summary.put("childCount", children == null ? 0 : children.size());
        summary.put("serialCount", serialItems == null ? 0 : serialItems.size());
        summary.put("boundSerialCount", serialItems == null ? 0 : serialItems.stream()
                .filter(item -> item.getBatchId() != null)
                .count());
        return summary;
    }
}
