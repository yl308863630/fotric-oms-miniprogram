package com.oms.service;

import com.oms.entity.ContractTemplate;
import com.oms.repository.ContractTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ContractTemplateService {

    @Autowired
    private ContractTemplateRepository contractTemplateRepository;

    public Page<ContractTemplate> getAllTemplates(Pageable pageable) {
        return contractTemplateRepository.findAll(pageable);
    }

    public Page<ContractTemplate> searchTemplates(String templateName, Pageable pageable) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return contractTemplateRepository.findAll(pageable);
        }
        return contractTemplateRepository.findByTemplateNameContaining(templateName, pageable);
    }

    public Optional<ContractTemplate> getTemplateById(Long id) {
        return contractTemplateRepository.findById(id);
    }

    @Transactional
    public ContractTemplate createTemplate(ContractTemplate template) {
        return contractTemplateRepository.save(template);
    }

    @Transactional
    public ContractTemplate updateTemplate(Long id, ContractTemplate template) {
        return contractTemplateRepository.findById(id)
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
                    return contractTemplateRepository.save(existingTemplate);
                })
                .orElseThrow(() -> new RuntimeException("合同模板不存在"));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        contractTemplateRepository.deleteById(id);
    }
}
