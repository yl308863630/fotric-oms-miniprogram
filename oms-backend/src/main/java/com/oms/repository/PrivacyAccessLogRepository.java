package com.oms.repository;

import com.oms.entity.PrivacyAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PrivacyAccessLogRepository extends JpaRepository<PrivacyAccessLog, Long>, JpaSpecificationExecutor<PrivacyAccessLog> {
}
