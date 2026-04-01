package com.oms.repository;

import com.oms.entity.PurchaseSettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PurchaseSettlementRecordRepository extends JpaRepository<PurchaseSettlementRecord, Long>, JpaSpecificationExecutor<PurchaseSettlementRecord> {
    Optional<PurchaseSettlementRecord> findByBillNo(String billNo);
}
