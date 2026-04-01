package com.oms.controller;

import com.oms.entity.SalesOutputInvoice;
import com.oms.entity.SalesReconciliation;
import com.oms.entity.SalesSettlementRecord;
import com.oms.service.SalesAggregationService;
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
public class SalesAggregationController {
    @Autowired
    private SalesAggregationService salesAggregationService;

    @GetMapping("/sales-reconciliations")
    public Page<SalesReconciliation> listSalesReconciliations(@RequestParam(required = false) String billNo,
                                                              @RequestParam(required = false) String omsOrderNo,
                                                              @RequestParam(required = false) String platformName,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return salesAggregationService.searchReconciliations(
                billNo,
                omsOrderNo,
                platformName,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    @GetMapping("/sales-reconciliations/{id}")
    public SalesReconciliation getSalesReconciliation(@PathVariable Long id) {
        return salesAggregationService.getReconciliation(id);
    }

    @PostMapping("/sales-reconciliations/generate")
    public SalesReconciliation generateSalesReconciliation(@RequestBody SalesReconciliationGenerateRequest request) {
        return salesAggregationService.createReconciliation(
                request.getOrderIds(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                request.getReconciliationDate(),
                request.getPlatformReconciliationNo(),
                request.getRemark()
        );
    }

    @PatchMapping("/sales-reconciliations/{id}")
    public SalesReconciliation updateSalesReconciliation(@PathVariable Long id,
                                                         @RequestBody SalesReconciliationUpdateRequest request) {
        return salesAggregationService.updateReconciliation(
                id,
                request.getPlatformReconciliationNo(),
                request.getAttachmentUrl(),
                request.getRemark()
        );
    }

    @PostMapping("/sales-reconciliations/{id}/void")
    public SalesReconciliation voidSalesReconciliation(@PathVariable Long id) {
        return salesAggregationService.voidReconciliation(id);
    }

    @GetMapping("/sales-output-invoices")
    public Page<SalesOutputInvoice> listSalesOutputInvoices(@RequestParam(required = false) String billNo,
                                                            @RequestParam(required = false) String platformName,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return salesAggregationService.searchOutputInvoices(
                billNo,
                platformName,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    @GetMapping("/sales-output-invoices/{id}")
    public SalesOutputInvoice getSalesOutputInvoice(@PathVariable Long id) {
        return salesAggregationService.getOutputInvoice(id);
    }

    @PostMapping("/sales-output-invoices/generate")
    public SalesOutputInvoice generateSalesOutputInvoice(@RequestBody SalesOutputInvoiceGenerateRequest request) {
        return salesAggregationService.createOutputInvoice(
                request.getReconciliationIds(),
                request.getInvoiceDate(),
                request.getInvoiceNumber(),
                request.getAttachmentUrl(),
                request.getRemark()
        );
    }

    @PostMapping("/sales-output-invoices/{id}/void")
    public SalesOutputInvoice voidSalesOutputInvoice(@PathVariable Long id) {
        return salesAggregationService.voidOutputInvoice(id);
    }

    @GetMapping("/sales-settlements")
    public Page<SalesSettlementRecord> listSalesSettlements(@RequestParam(required = false) String billNo,
                                                            @RequestParam(required = false) String platformName,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return salesAggregationService.searchSettlements(
                billNo,
                platformName,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    @GetMapping("/sales-settlements/{id}")
    public SalesSettlementRecord getSalesSettlement(@PathVariable Long id) {
        return salesAggregationService.getSettlement(id);
    }

    @PostMapping("/sales-settlements/generate")
    public SalesSettlementRecord generateSalesSettlement(@RequestBody SalesSettlementGenerateRequest request) {
        return salesAggregationService.createSettlement(
                request.getInvoiceIds(),
                request.getReconciliationIds(),
                request.getSettlementDate(),
                request.getRemark()
        );
    }

    @PostMapping("/sales-settlements/{id}/refund-status")
    public SalesSettlementRecord updateSalesSettlementRefund(@PathVariable Long id,
                                                             @RequestBody SalesSettlementRefundRequest request) {
        return salesAggregationService.updateSettlementRefund(
                id,
                request.getRefundStatus(),
                request.getRefundDate(),
                request.getAttachmentUrl(),
                request.getRemark()
        );
    }

    @PostMapping("/sales-settlements/{id}/void")
    public SalesSettlementRecord voidSalesSettlement(@PathVariable Long id) {
        return salesAggregationService.voidSettlement(id);
    }

    @Data
    public static class SalesReconciliationGenerateRequest {
        private List<Long> orderIds;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private LocalDate reconciliationDate;
        private String platformReconciliationNo;
        private String remark;
    }

    @Data
    public static class SalesReconciliationUpdateRequest {
        private String platformReconciliationNo;
        private String attachmentUrl;
        private String remark;
    }

    @Data
    public static class SalesOutputInvoiceGenerateRequest {
        private List<Long> reconciliationIds;
        private LocalDate invoiceDate;
        private String invoiceNumber;
        private String attachmentUrl;
        private String remark;
    }

    @Data
    public static class SalesSettlementGenerateRequest {
        private List<Long> invoiceIds;
        private List<Long> reconciliationIds;
        private LocalDate settlementDate;
        private String remark;
    }

    @Data
    public static class SalesSettlementRefundRequest {
        private String refundStatus;
        private LocalDate refundDate;
        private String attachmentUrl;
        private String remark;
    }
}
