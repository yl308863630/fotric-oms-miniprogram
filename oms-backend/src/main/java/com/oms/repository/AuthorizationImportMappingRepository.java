package com.oms.repository;

import com.oms.entity.AuthorizationImportMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthorizationImportMappingRepository extends JpaRepository<AuthorizationImportMapping, Long> {
    List<AuthorizationImportMapping> findByCreatedByOrderByUpdateTimeDesc(Long createdBy);
    Optional<AuthorizationImportMapping> findByIdAndCreatedBy(Long id, Long createdBy);
}
