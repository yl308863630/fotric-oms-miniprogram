package com.oms.controller;

import com.oms.entity.QuotationTemplate;
import com.oms.service.QuotationTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotation-templates")
public class QuotationTemplateController {

    @Autowired
    private QuotationTemplateService quotationTemplateService;

    @GetMapping
    public ResponseEntity<Page<QuotationTemplate>> getAllTemplates(
            @RequestParam(required = false) String templateName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<QuotationTemplate> templates = templateName != null && !templateName.trim().isEmpty()
                ? quotationTemplateService.searchTemplates(templateName, pageable)
                : quotationTemplateService.getAllTemplates(pageable);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuotationTemplate> getTemplateById(@PathVariable Long id) {
        return quotationTemplateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<QuotationTemplate> createTemplate(@RequestBody QuotationTemplate template) {
        QuotationTemplate created = quotationTemplateService.createTemplate(template);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuotationTemplate> updateTemplate(@PathVariable Long id, @RequestBody QuotationTemplate template) {
        try {
            return ResponseEntity.ok(quotationTemplateService.updateTemplate(id, template));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        quotationTemplateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }
}
