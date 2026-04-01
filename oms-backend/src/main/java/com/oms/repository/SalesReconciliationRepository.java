package com.oms.repository;

import com.oms.entity.SalesReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SalesReconciliationRepository extends JpaRepository<SalesReconciliation, Long>, JpaSpecificationExecutor<SalesReconciliation> {
    Optional<SalesReconciliation> findByBillNo(String billNo);
}
