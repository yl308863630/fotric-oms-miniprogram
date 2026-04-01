package com.oms.controller;

import com.oms.entity.SalesOrderAllocation;
import com.oms.service.SalesOrderAllocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-order-allocations")
public class SalesOrderAllocationController {
    @Autowired
    private SalesOrderAllocationService salesOrderAllocationService;

    @GetMapping
    public List<SalesOrderAllocation> list(@RequestParam(required = false) String allocationNo,
                                           @RequestParam(required = false) Long masterId,
                                           @RequestParam(required = false) Long salesOrderId,
                                           @RequestParam(required = false) Long rootAllocationId) {
        return salesOrderAllocationService.listAllocations(allocationNo, masterId, salesOrderId, rootAllocationId);
    }

    @GetMapping("/{id}")
    public SalesOrderAllocation detail(@PathVariable Long id) {
        return salesOrderAllocationService.getAllocation(id);
    }

    @GetMapping("/{id}/detail")
    public Map<String, Object> detailView(@PathVariable Long id) {
        return salesOrderAllocationService.getAllocationDetail(id);
    }
}
