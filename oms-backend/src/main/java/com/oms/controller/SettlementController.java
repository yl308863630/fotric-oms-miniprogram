package com.oms.controller;

import com.oms.entity.Settlement;
import com.oms.service.SettlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import com.oms.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.IOException;

@RestController
@RequestMapping("/api/settlements")
@CrossOrigin
public class SettlementController {
    @Autowired
    private SettlementService settlementService;

    @Autowired
    private PdfService pdfService;

    @GetMapping
    public Page<Settlement> list(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return settlementService.searchSettlements(billNo, status, projectName, PageRequest.of(page, size));
    }

    @GetMapping("/{billNo}")
    public Settlement getSettlement(@PathVariable String billNo) {
        return settlementService.getSettlementByBillNo(billNo);
    }

    @PostMapping("/{billNo}/apply")
    public Settlement apply(@PathVariable String billNo) {
        return settlementService.apply(billNo);
    }

    @PostMapping("/{billNo}/settle")
    public Settlement settle(@PathVariable String billNo) {
        return settlementService.settle(billNo);
    }

    @PostMapping("/generate")
    public Settlement generate(@RequestParam String invoiceBillNo) {
        return settlementService.createSettlementFromInvoice(invoiceBillNo);
    }

//    @GetMapping("/{billNo}/pdf")
//    public ResponseEntity<byte[]> downloadPdf(@PathVariable String billNo) throws IOException {
//        Settlement settlement = settlementService.getSettlementByBillNo(billNo);
//        if (settlement == null) {
//            return ResponseEntity.notFound().build();
//        }
//        byte[] pdf = pdfService.generateSettlementPdf(settlement);
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + billNo + ".pdf")
//                .contentType(MediaType.parseMediaType("application/pdf"))
//                .body(pdf);
//    }
}
