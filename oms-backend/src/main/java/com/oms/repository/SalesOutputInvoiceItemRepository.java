package com.oms.repository;

import com.oms.entity.SalesOutputInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesOutputInvoiceItemRepository extends JpaRepository<SalesOutputInvoiceItem, Long> {
    List<SalesOutputInvoiceItem> findByInvoiceIdOrderByIdAsc(Long invoiceId);
}
