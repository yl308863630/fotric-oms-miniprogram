package com.oms.service;

import com.oms.entity.AuthorizationTemplate;
import com.oms.repository.AuthorizationTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthorizationTemplateService {
    private final AuthorizationTemplateRepository authorizationTemplateRepository;

    public AuthorizationTemplateService(AuthorizationTemplateRepository authorizationTemplateRepository) {
        this.authorizationTemplateRepository = authorizationTemplateRepository;
    }

    public Page<AuthorizationTemplate> getAllTemplates(Pageable pageable) {
        return authorizationTemplateRepository.findAll(pageable);
    }

    public Page<AuthorizationTemplate> searchTemplates(String templateName, Pageable pageable) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return authorizationTemplateRepository.findAll(pageable);
        }
        return authorizationTemplateRepository.findByTemplateNameContaining(templateName.trim(), pageable);
    }

    public Optional<AuthorizationTemplate> getTemplateById(Long id) {
        return authorizationTemplateRepository.findById(id);
    }

    @Transactional
    public AuthorizationTemplate createTemplate(AuthorizationTemplate template) {
        return authorizationTemplateRepository.save(template);
    }

    @Transactional
    public AuthorizationTemplate updateTemplate(Long id, AuthorizationTemplate template) {
        return authorizationTemplateRepository.findById(id)
                .map(existingTemplate -> {
                    existingTemplate.setTemplateName(template.getTemplateName());
                    existingTemplate.setDescription(template.getDescription());
                    if (template.getTemplateUrl() != null) {
                        existingTemplate.setTemplateUrl(template.getTemplateUrl());
                    }
                    if (template.getFileName() != null) {
                        existingTemplate.setFileName(template.getFileName());
                    }
                    if (template.getFileSize() != null) {
                        existingTemplate.setFileSize(template.getFileSize());
                    }
                    return authorizationTemplateRepository.save(existingTemplate);
                })
                .orElseThrow(() -> new RuntimeException("授权模板不存在"));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        authorizationTemplateRepository.deleteById(id);
    }
}
