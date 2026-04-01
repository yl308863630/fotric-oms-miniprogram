package com.oms.repository;

import com.oms.entity.PurchaseInputInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseInputInvoiceItemRepository extends JpaRepository<PurchaseInputInvoiceItem, Long> {
    List<PurchaseInputInvoiceItem> findByInvoiceIdOrderByIdAsc(Long invoiceId);
}
