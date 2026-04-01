package com.oms.repository;

import com.oms.entity.PurchaseInboundInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseInboundInvoiceRepository extends JpaRepository<PurchaseInboundInvoice, Long> {
    List<PurchaseInboundInvoice> findByPaymentRequestIdOrderByIdDesc(Long paymentRequestId);
}

