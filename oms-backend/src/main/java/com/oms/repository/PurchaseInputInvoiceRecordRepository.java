package com.oms.repository;

import com.oms.entity.PurchaseInputInvoiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PurchaseInputInvoiceRecordRepository extends JpaRepository<PurchaseInputInvoiceRecord, Long>, JpaSpecificationExecutor<PurchaseInputInvoiceRecord> {
    Optional<PurchaseInputInvoiceRecord> findByBillNo(String billNo);
}
