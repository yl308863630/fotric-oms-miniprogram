package com.oms.repository;

import com.oms.entity.SalesOutputInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SalesOutputInvoiceRepository extends JpaRepository<SalesOutputInvoice, Long>, JpaSpecificationExecutor<SalesOutputInvoice> {
    Optional<SalesOutputInvoice> findByBillNo(String billNo);
}
