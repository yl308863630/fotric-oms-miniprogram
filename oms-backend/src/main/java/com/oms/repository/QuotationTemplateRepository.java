package com.oms.repository;

import com.oms.entity.QuotationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuotationTemplateRepository extends JpaRepository<QuotationTemplate, Long> {
    Page<QuotationTemplate> findByTemplateNameContaining(String templateName, Pageable pageable);
}
