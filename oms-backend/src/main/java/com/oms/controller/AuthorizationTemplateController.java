package com.oms.controller;

import com.oms.entity.AuthorizationTemplate;
import com.oms.service.AuthorizationTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/authorization-templates")
public class AuthorizationTemplateController {
    private final AuthorizationTemplateService authorizationTemplateService;

    public AuthorizationTemplateController(AuthorizationTemplateService authorizationTemplateService) {
        this.authorizationTemplateService = authorizationTemplateService;
    }

    @GetMapping
    public ResponseEntity<Page<AuthorizationTemplate>> getAllTemplates(
            @RequestParam(required = false) String templateName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AuthorizationTemplate> templates = templateName != null && !templateName.trim().isEmpty()
                ? authorizationTemplateService.searchTemplates(templateName, pageable)
                : authorizationTemplateService.getAllTemplates(pageable);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthorizationTemplate> getTemplateById(@PathVariable Long id) {
        return authorizationTemplateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AuthorizationTemplate> createTemplate(@RequestBody AuthorizationTemplate template) {
        return ResponseEntity.ok(authorizationTemplateService.createTemplate(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuthorizationTemplate> updateTemplate(@PathVariable Long id,
                                                                @RequestBody AuthorizationTemplate template) {
        try {
            return ResponseEntity.ok(authorizationTemplateService.updateTemplate(id, template));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        authorizationTemplateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }
}
