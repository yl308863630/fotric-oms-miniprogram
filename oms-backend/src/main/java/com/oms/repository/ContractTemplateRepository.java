package com.oms.repository;

import com.oms.entity.ContractTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
    Page<ContractTemplate> findByTemplateNameContaining(String templateName, Pageable pageable);
}
