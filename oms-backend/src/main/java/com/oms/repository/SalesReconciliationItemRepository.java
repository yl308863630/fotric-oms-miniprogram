package com.oms.repository;

import com.oms.entity.SalesReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReconciliationItemRepository extends JpaRepository<SalesReconciliationItem, Long> {
    List<SalesReconciliationItem> findByReconciliationIdOrderByIdAsc(Long reconciliationId);
}
