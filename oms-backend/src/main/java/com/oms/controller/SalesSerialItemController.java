package com.oms.controller;

import com.oms.entity.SalesSerialItem;
import com.oms.service.SalesSerialItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-serial-items")
public class SalesSerialItemController {
    @Autowired
    private SalesSerialItemService salesSerialItemService;

    @GetMapping
    public List<SalesSerialItem> list(@RequestParam(required = false) String snCode,
                                      @RequestParam(required = false) Long masterId,
                                      @RequestParam(required = false) Long salesOrderId,
                                      @RequestParam(required = false) Long allocationId,
                                      @RequestParam(required = false) Long batchId) {
        return salesSerialItemService.listSerialItems(snCode, masterId, salesOrderId, allocationId, batchId);
    }

    @PostMapping("/batch")
    public List<SalesSerialItem> batchCreate(@RequestBody Map<String, Object> request) {
        Long masterId = toLong(request.get("masterId"));
        Long salesOrderId = toLong(request.get("salesOrderId"));
        Long allocationId = toLong(request.get("allocationId"));
        String productModel = request.get("productModel") == null ? null : String.valueOf(request.get("productModel"));
        List<String> snCodes = new ArrayList<>();
        Object rawSnCodes = request.get("snCodes");
        if (rawSnCodes instanceof List<?>) {
            for (Object item : (List<?>) rawSnCodes) {
                snCodes.add(String.valueOf(item));
            }
        }
        return salesSerialItemService.batchCreate(masterId, salesOrderId, allocationId, productModel, snCodes);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        salesSerialItemService.deleteSerialItem(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        return result;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }
}
