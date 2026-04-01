package com.oms.repository;

import com.oms.entity.SalesSettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesSettlementItemRepository extends JpaRepository<SalesSettlementItem, Long> {
    List<SalesSettlementItem> findBySettlementIdOrderByIdAsc(Long settlementId);
}
