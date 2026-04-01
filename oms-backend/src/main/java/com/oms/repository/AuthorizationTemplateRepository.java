package com.oms.repository;

import com.oms.entity.AuthorizationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorizationTemplateRepository extends JpaRepository<AuthorizationTemplate, Long> {
    Page<AuthorizationTemplate> findByTemplateNameContaining(String templateName, Pageable pageable);
}
