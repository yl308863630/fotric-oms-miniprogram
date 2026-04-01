package com.oms.controller;

import com.oms.entity.PurchaseInboundInvoice;
import com.oms.entity.PurchasePaymentRequest;
import com.oms.service.PurchasePaymentRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-payment-requests")
@CrossOrigin
public class PurchasePaymentRequestController {
    @Autowired
    private PurchasePaymentRequestService paymentRequestService;

    @GetMapping
    public Page<PurchasePaymentRequest> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return paymentRequestService.search(keyword, status, PageRequest.of(page, size));
    }

    @GetMapping("/by-purchase-order/{purchaseOrderId}")
    public List<PurchasePaymentRequest> byPurchaseOrder(@PathVariable Long purchaseOrderId) {
        return paymentRequestService.listByPurchaseOrderId(purchaseOrderId);
    }

    @GetMapping("/{id}")
    public PurchasePaymentRequest getById(@PathVariable Long id) {
        return paymentRequestService.getById(id);
    }

    @PostMapping
    public PurchasePaymentRequest create(@RequestBody Map<String, Object> body) {
        return paymentRequestService.create(body);
    }

    @PostMapping("/{id}/status")
    public PurchasePaymentRequest updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        String remark = body != null ? body.get("remark") : null;
        return paymentRequestService.updateStatus(id, status, remark);
    }

    @GetMapping("/{id}/inbound-invoices")
    public List<PurchaseInboundInvoice> listInboundInvoices(@PathVariable Long id) {
        return paymentRequestService.listInboundInvoices(id);
    }

    @PostMapping("/{id}/inbound-invoices")
    public PurchaseInboundInvoice addInboundInvoice(@PathVariable Long id, @RequestBody PurchaseInboundInvoice invoice) {
        return paymentRequestService.addInboundInvoice(id, invoice);
    }

    @PostMapping("/{id}/voucher")
    public PurchasePaymentRequest uploadVoucher(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String voucherUrl = body != null ? String.valueOf(body.getOrDefault("voucherUrl", "")) : "";
        String bankFlowNo = body != null ? String.valueOf(body.getOrDefault("bankFlowNo", "")) : "";
        BigDecimal paidAmount = null;
        if (body != null && body.get("paidAmount") != null && !String.valueOf(body.get("paidAmount")).isBlank()) {
            paidAmount = new BigDecimal(String.valueOf(body.get("paidAmount")));
        }
        return paymentRequestService.uploadVoucher(id, voucherUrl, bankFlowNo, paidAmount);
    }
}

