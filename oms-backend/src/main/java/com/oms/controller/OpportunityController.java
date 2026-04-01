package com.oms.controller;

import com.oms.entity.Opportunity;
import com.oms.entity.OpportunityProduct;
import com.oms.entity.QuotationTemplate;
import com.oms.service.DocumentService;
import com.oms.service.OpportunityService;
import com.oms.service.QuotationTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/opportunities")
@CrossOrigin(origins = "*")
public class OpportunityController {

    @Autowired
    private OpportunityService service;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private QuotationTemplateService quotationTemplateService;

    @GetMapping
    public Page<Opportunity> getAllOpportunities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getOpportunitiesWithPagination(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Opportunity> getOpportunityById(@PathVariable Long id) {
        return service.getOpportunityById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Opportunity createOpportunity(@RequestBody Opportunity opportunity) {
        return service.createOpportunity(opportunity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Opportunity> updateOpportunity(@PathVariable Long id, @RequestBody Opportunity opportunityDetails) {
        try {
            return ResponseEntity.ok(service.updateOpportunity(id, opportunityDetails));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOpportunity(@PathVariable Long id) {
        service.deleteOpportunity(id);
        return ResponseEntity.noContent().build();
    }
    
    // 产品相关接口
    @GetMapping("/{id}/products")
    public List<OpportunityProduct> getOpportunityProducts(@PathVariable Long id) {
        return service.getOpportunityProducts(id);
    }
    
    @PostMapping("/{id}/products")
    public OpportunityProduct addOpportunityProduct(@PathVariable Long id, @RequestBody OpportunityProduct product) {
        product.setOpportunity(service.getOpportunityById(id)
                .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + id)));
        return service.addOpportunityProduct(product);
    }
    
    @PutMapping("/{id}/products")
    public ResponseEntity<Void> updateOpportunityProducts(@PathVariable Long id, @RequestBody List<OpportunityProduct> products) {
        service.updateOpportunityProducts(id, products);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteOpportunityProduct(@PathVariable Long productId) {
        service.deleteOpportunityProduct(productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 根据商机生成报价单文档。请求体或参数传 templateId（推荐）或 templateUrl。
     */
    @PostMapping("/{id}/generate-quotation")
    public ResponseEntity<?> generateQuotation(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                               @RequestParam(required = false) Long templateId,
                                               @RequestParam(required = false) String templateUrl) {
        Opportunity opportunity = service.getOpportunityById(id).orElse(null);
        if (opportunity == null) {
            return ResponseEntity.notFound().build();
        }
        String templateUrlToUse = null;
        if (templateId != null) {
            QuotationTemplate t = quotationTemplateService.getTemplateById(templateId).orElse(null);
            if (t != null) {
                templateUrlToUse = t.getTemplateUrl();
            }
        }
        if (templateUrlToUse == null && body != null && body.get("templateId") != null) {
            Object tid = body.get("templateId");
            Long idFromBody = tid instanceof Number ? ((Number) tid).longValue() : null;
            if (idFromBody == null && tid != null) {
                try {
                    idFromBody = Long.parseLong(tid.toString());
                } catch (NumberFormatException ignored) { }
            }
            if (idFromBody != null) {
                QuotationTemplate t = quotationTemplateService.getTemplateById(idFromBody).orElse(null);
                if (t != null) {
                    templateUrlToUse = t.getTemplateUrl();
                }
            }
        }
        if (templateUrlToUse == null && templateUrl != null && !templateUrl.isBlank()) {
            templateUrlToUse = templateUrl;
        }
        if (templateUrlToUse == null || templateUrlToUse.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请指定报价单模板（templateId 或 templateUrl）"));
        }
        try {
            String fileUrl = documentService.generateQuotationDocument(opportunity, templateUrlToUse);
            return ResponseEntity.ok(Map.of("url", fileUrl, "success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage() != null ? e.getMessage() : "生成报价单失败"));
        }
    }
}
