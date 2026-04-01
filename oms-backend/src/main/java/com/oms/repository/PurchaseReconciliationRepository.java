package com.oms.repository;

import com.oms.entity.PurchaseReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PurchaseReconciliationRepository extends JpaRepository<PurchaseReconciliation, Long>, JpaSpecificationExecutor<PurchaseReconciliation> {
    Optional<PurchaseReconciliation> findByBillNo(String billNo);
}
