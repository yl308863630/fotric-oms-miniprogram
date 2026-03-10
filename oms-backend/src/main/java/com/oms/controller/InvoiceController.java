package com.oms.controller;

import com.oms.entity.Invoice;
import com.oms.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.oms.util.ExcelExportUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.IOException;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin
public class InvoiceController {
    @Autowired
    private InvoiceService invoiceService;

    @GetMapping
    public Page<Invoice> list(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return invoiceService.searchInvoices(billNo, status, projectName, PageRequest.of(page, size));
    }

    @GetMapping("/{billNo}")
    public Invoice get(@PathVariable String billNo) {
        return invoiceService.getInvoiceByBillNo(billNo);
    }

    @PostMapping
    public Invoice create(@RequestBody Invoice invoice) {
        return invoiceService.createInvoice(invoice);
    }

    @PostMapping("/{billNo}/status")
    public Invoice updateStatus(@PathVariable String billNo, @RequestParam String status) {
        return invoiceService.updateStatus(billNo, status);
    }

    @PostMapping("/{billNo}/receipt")
    public Invoice saveReceipt(@PathVariable String billNo, @RequestParam String fileUrl) {
        return invoiceService.saveReceipt(billNo, fileUrl);
    }

    @PostMapping("/generate")
    public Invoice generate(@RequestBody GenerateRequest request) {
        return invoiceService.createInvoiceFromOrders(request.getOrderNos(), request.getProjectName());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectName) throws IOException {
        List<Invoice> data = invoiceService.searchInvoices(billNo, status, projectName, PageRequest.of(0, 10000)).getContent();
        String[] headers = {"对账单号", "项目名称", "状态", "金额", "创建时间"};
        String[] fields = {"billNo", "projectName", "status", "amount", "createTime"};
        byte[] bytes = ExcelExportUtil.exportToExcel(data, headers, fields);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices.xlsx")
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(bytes);
    }

    public static class GenerateRequest {
        private List<String> orderNos;
        private String projectName;

        public List<String> getOrderNos() { return orderNos; }
        public void setOrderNos(List<String> orderNos) { this.orderNos = orderNos; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
    }
}
