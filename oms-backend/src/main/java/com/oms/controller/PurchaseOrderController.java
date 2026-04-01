package com.oms.controller;

import com.oms.entity.OperationLog;
import com.oms.entity.PurchaseOrder;
import com.oms.repository.UserRepository;
import com.oms.service.OperationLogService;
import com.oms.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public Page<PurchaseOrder> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String erpEntryStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return purchaseOrderService.searchOrders(keyword, status, erpEntryStatus, pageRequest);
    }

    @GetMapping("/{id}")
    public PurchaseOrder getById(@PathVariable Long id) {
        return purchaseOrderService.getOrderById(id);
    }

    @PostMapping
    public PurchaseOrder save(@RequestBody PurchaseOrder order) {
        PurchaseOrder saved = purchaseOrderService.saveOrder(order);
        String operatorName = getCurrentOperatorName();
        String detailStr = "新建，采购单号 " + (saved.getPurchaseOrderNo() != null ? saved.getPurchaseOrderNo() : "-");
        operationLogService.log(operatorName, "新建采购订单", "PURCHASE_ORDER", String.valueOf(saved.getId()), detailStr);
        return saved;
    }

    @PutMapping("/{id}")
    public PurchaseOrder update(@PathVariable Long id, @RequestBody PurchaseOrder order) {
        order.setId(id);
        PurchaseOrder saved = purchaseOrderService.saveOrder(order);
        String operatorName = getCurrentOperatorName();
        operationLogService.log(operatorName, "编辑采购订单", "PURCHASE_ORDER", String.valueOf(id), "编辑采购订单");
        return saved;
    }

    @PostMapping("/backfill-anchors")
    public Map<String, Object> backfillAnchors(@RequestBody(required = false) Map<String, Object> request) {
        List<Long> purchaseOrderIds = toLongList(request != null ? request.get("purchaseOrderIds") : null);
        Integer limit = toInteger(request != null ? request.get("limit") : null);
        boolean onlyMissing = request == null || request.get("onlyMissing") == null || Boolean.parseBoolean(String.valueOf(request.get("onlyMissing")));
        return purchaseOrderService.backfillAnchors(purchaseOrderIds, limit, onlyMissing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purchaseOrderService.deleteOrder(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/payment-status")
    public PurchaseOrder updatePaymentStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String paymentStatus = request.get("status");
        return purchaseOrderService.updatePaymentStatus(id, paymentStatus);
    }

    @PatchMapping("/{id}/reconciliation-status")
    public PurchaseOrder updateReconciliationStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String reconciliationStatus = request.get("status");
        String invoiceNumber = request.get("invoiceNumber");
        return purchaseOrderService.updateReconciliationStatus(id, reconciliationStatus, invoiceNumber);
    }

    @PatchMapping("/{id}/erp-entry")
    public PurchaseOrder updateErpEntry(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String screenshotUrl = body != null ? String.valueOf(body.getOrDefault("erpEntryScreenshotUrl", "")) : "";
        String operator = body != null ? String.valueOf(body.getOrDefault("erpEntryOperator", "")) : "";
        LocalDateTime entryTime = null;
        if (body != null && body.get("erpEntryTime") != null) {
            entryTime = parseFlexibleDateTime(String.valueOf(body.get("erpEntryTime")));
        }
        return purchaseOrderService.updateErpEntry(id, screenshotUrl, operator, entryTime);
    }

    private LocalDateTime parseFlexibleDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String getCurrentOperatorName() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null)
            return "system";
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) return "system";
        return userRepository.findByUsername(username)
                .map(u -> (u.getRealName() != null && !u.getRealName().isBlank()) ? u.getRealName() : u.getUsername())
                .orElse(username);
    }

    @GetMapping("/{id}/operation-logs")
    public List<Map<String, Object>> getOperationLogs(@PathVariable Long id) {
        String operatorName = getCurrentOperatorName();
        List<OperationLog> logs = operationLogService.getLogsForOperator("PURCHASE_ORDER", String.valueOf(id), operatorName);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return logs.stream().map(log -> {
            Map<String, Object> m = new HashMap<>();
            m.put("operationType", log.getAction());
            m.put("description", log.getDetails() != null ? log.getDetails() : "");
            m.put("operator", log.getOperatorName() != null ? log.getOperatorName() : "");
            m.put("operationTime", log.getCreateTime() != null ? log.getCreateTime().format(fmt) : "");
            return m;
        }).collect(Collectors.toList());
    }

    private List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return java.util.Collections.emptyList();
        }
        List<Long> result = new java.util.ArrayList<>();
        for (Object item : list) {
            Long value = toLong(item);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer toInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
