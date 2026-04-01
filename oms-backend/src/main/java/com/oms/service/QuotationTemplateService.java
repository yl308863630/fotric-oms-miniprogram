package com.oms.service;

import com.oms.entity.QuotationTemplate;
import com.oms.repository.QuotationTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class QuotationTemplateService {

    @Autowired
    private QuotationTemplateRepository quotationTemplateRepository;

    public Page<QuotationTemplate> getAllTemplates(Pageable pageable) {
        return quotationTemplateRepository.findAll(pageable);
    }

    public Page<QuotationTemplate> searchTemplates(String templateName, Pageable pageable) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return quotationTemplateRepository.findAll(pageable);
        }
        return quotationTemplateRepository.findByTemplateNameContaining(templateName, pageable);
    }

    public Optional<QuotationTemplate> getTemplateById(Long id) {
        return quotationTemplateRepository.findById(id);
    }

    @Transactional
    public QuotationTemplate createTemplate(QuotationTemplate template) {
        return quotationTemplateRepository.save(template);
    }

    @Transactional
    public QuotationTemplate updateTemplate(Long id, QuotationTemplate template) {
        return quotationTemplateRepository.findById(id)
                .map(existing -> {
                    existing.setTemplateName(template.getTemplateName());
                    existing.setDescription(template.getDescription());
                    if (template.getTemplateUrl() != null) {
                        existing.setTemplateUrl(template.getTemplateUrl());
                    }
                    if (template.getFileName() != null) {
                        existing.setFileName(template.getFileName());
                    }
                    if (template.getFileSize() != null) {
                        existing.setFileSize(template.getFileSize());
                    }
                    return quotationTemplateRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("报价单模板不存在"));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        quotationTemplateRepository.deleteById(id);
    }
}
