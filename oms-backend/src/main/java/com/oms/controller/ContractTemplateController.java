package com.oms.controller;

import com.oms.entity.ContractTemplate;
import com.oms.service.ContractTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contract-templates")
public class ContractTemplateController {

    @Autowired
    private ContractTemplateService contractTemplateService;

    @GetMapping
    public ResponseEntity<Page<ContractTemplate>> getAllTemplates(
            @RequestParam(required = false) String templateName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ContractTemplate> templates;
        if (templateName != null && !templateName.trim().isEmpty()) {
            templates = contractTemplateService.searchTemplates(templateName, pageable);
        } else {
            templates = contractTemplateService.getAllTemplates(pageable);
        }
        
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractTemplate> getTemplateById(@PathVariable Long id) {
        return contractTemplateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ContractTemplate> createTemplate(@RequestBody ContractTemplate template) {
        ContractTemplate created = contractTemplateService.createTemplate(template);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContractTemplate> updateTemplate(@PathVariable Long id, @RequestBody ContractTemplate template) {
        try {
            ContractTemplate updated = contractTemplateService.updateTemplate(id, template);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        contractTemplateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }
}
