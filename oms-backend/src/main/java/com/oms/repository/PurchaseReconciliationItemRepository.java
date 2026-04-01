package com.oms.repository;

import com.oms.entity.PurchaseReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReconciliationItemRepository extends JpaRepository<PurchaseReconciliationItem, Long> {
    List<PurchaseReconciliationItem> findByReconciliationIdOrderByIdAsc(Long reconciliationId);
}
