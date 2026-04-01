package com.oms.repository;

import com.oms.entity.SalesSettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SalesSettlementRecordRepository extends JpaRepository<SalesSettlementRecord, Long>, JpaSpecificationExecutor<SalesSettlementRecord> {
    Optional<SalesSettlementRecord> findByBillNo(String billNo);
}
