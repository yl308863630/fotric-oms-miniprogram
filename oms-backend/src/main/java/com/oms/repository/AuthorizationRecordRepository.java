package com.oms.repository;

import com.oms.entity.AuthorizationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AuthorizationRecordRepository extends JpaRepository<AuthorizationRecord, Long>, JpaSpecificationExecutor<AuthorizationRecord> {
    Optional<AuthorizationRecord> findByAuthorizationCode(String authorizationCode);
}
