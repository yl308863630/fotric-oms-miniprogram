package com.oms.controller;

import com.oms.entity.PurchaseInputInvoiceRecord;
import com.oms.entity.PurchaseReconciliation;
import com.oms.entity.PurchaseSettlementRecord;
import com.oms.service.PurchaseAggregationService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class PurchaseAggregationController {
    @Autowired
    private PurchaseAggregationService purchaseAggregationService;

    @GetMapping("/purchase-reconciliations")
    public Page<PurchaseReconciliation> listPurchaseReconciliations(@RequestParam(required = false) String billNo,
                                                                    @RequestParam(required = false) String supplier,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return purchaseAggregationService.searchReconciliations(
                billNo,
                supplier,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    @GetMapping("/purchase-reconciliations/{id}")
    public PurchaseReconciliation getPurchaseReconciliation(@PathVariable Long id) {
        return purchaseAggregationService.getReconciliation(id);
    }

    @PostMapping("/purchase-reconciliations/generate")
    public PurchaseReconciliation generatePurchaseReconciliation(@RequestBody PurchaseReconciliationGenerateRequest request) {
        return purchaseAggregationService.createReconciliation(
                request.getPurchaseOrderIds(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                request.getReconciliationDate(),
                request.getRemark()
        );
    }

    @PostMapping("/purchase-reconciliations/{id}/void")
    public PurchaseReconciliation voidPurchaseReconciliation(@PathVariable Long id) {
        return purchaseAggregationService.voidReconciliation(id);
    }

    @GetMapping("/purchase-input-invoices")
    public Page<PurchaseInputInvoiceRecord> listPurchaseInputInvoices(@RequestParam(required = false) String billNo,
                                                                      @RequestParam(required = false) String supplier,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        return purchaseAggregationService.searchInputInvoices(
                billNo,
                supplier,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    @GetMapping("/purchase-input-invoices/{id}")
    public PurchaseInputInvoiceRecord getPurchaseInputInvoice(@PathVariable Long id) {
        return purchaseAggregationService.getInputInvoice(id);
    }

    @PostMapping("/purchase-input-invoices/generate")
    public PurchaseInputInvoiceRecord generatePurchaseInputInvoice(@RequestBody PurchaseInputInvoiceGenerateRequest request) {
        return purchaseAggregationService.createInputInvoice(
                request.getReconciliationIds(),
                request.getInvoiceDate(),
                request.getInvoiceNumber(),
                request.getAttachmentUrl(),
                request.getRemark()
        );
    }

    @PostMapping("/purchase-input-invoices/{id}/void")
    public PurchaseInputInvoiceRecord voidPurchaseInputInvoice(@PathVariable Long id) {
        return purchaseAggregationService.voidInputInvoice(id);
    }

    @GetMapping("/purchase-settlements")
    public Page<PurchaseSettlementRecord> listPurchaseSettlements(@RequestParam(required = false) String billNo,
                                                                  @RequestParam(required = false) String supplier,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
        return purchaseAggregationService.searchSettlements(
                billNo,
                supplier,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    @GetMapping("/purchase-settlements/{id}")
    public PurchaseSettlementRecord getPurchaseSettlement(@PathVariable Long id) {
        return purchaseAggregationService.getSettlement(id);
    }

    @PostMapping("/purchase-settlements/generate")
    public PurchaseSettlementRecord generatePurchaseSettlement(@RequestBody PurchaseSettlementGenerateRequest request) {
        return purchaseAggregationService.createSettlement(
                request.getInvoiceIds(),
                request.getReconciliationIds(),
                request.getSettlementDate(),
                request.getRemark()
        );
    }

    @PostMapping("/purchase-settlements/{id}/payment-status")
    public PurchaseSettlementRecord updatePurchaseSettlementPayment(@PathVariable Long id,
                                                                   @RequestBody PurchaseSettlementPaymentRequest request) {
        return purchaseAggregationService.updateSettlementPayment(
                id,
                request.getPaymentStatus(),
                request.getPaymentDate(),
                request.getAttachmentUrl(),
                request.getRemark()
        );
    }

    @PostMapping("/purchase-settlements/{id}/void")
    public PurchaseSettlementRecord voidPurchaseSettlement(@PathVariable Long id) {
        return purchaseAggregationService.voidSettlement(id);
    }

    @Data
    public static class PurchaseReconciliationGenerateRequest {
        private List<Long> purchaseOrderIds;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private LocalDate reconciliationDate;
        private String remark;
    }

    @Data
    public static class PurchaseInputInvoiceGenerateRequest {
        private List<Long> reconciliationIds;
        private LocalDate invoiceDate;
        private String invoiceNumber;
        private String attachmentUrl;
        private String remark;
    }

    @Data
    public static class PurchaseSettlementGenerateRequest {
        private List<Long> invoiceIds;
        private List<Long> reconciliationIds;
        private LocalDate settlementDate;
        private String remark;
    }

    @Data
    public static class PurchaseSettlementPaymentRequest {
        private String paymentStatus;
        private LocalDate paymentDate;
        private String attachmentUrl;
        private String remark;
    }
}
