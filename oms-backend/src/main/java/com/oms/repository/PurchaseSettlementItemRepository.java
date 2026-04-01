package com.oms.repository;

import com.oms.entity.PurchaseSettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseSettlementItemRepository extends JpaRepository<PurchaseSettlementItem, Long> {
    List<PurchaseSettlementItem> findBySettlementIdOrderByIdAsc(Long settlementId);
}
