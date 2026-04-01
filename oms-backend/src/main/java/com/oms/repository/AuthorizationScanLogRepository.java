package com.oms.repository;

import com.oms.entity.AuthorizationScanLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuthorizationScanLogRepository extends JpaRepository<AuthorizationScanLog, Long>, JpaSpecificationExecutor<AuthorizationScanLog> {
    Page<AuthorizationScanLog> findByAuthorizationRecordIdOrderByCreateTimeDesc(Long authorizationRecordId, Pageable pageable);
}
