package com.oms.repository;

import com.oms.entity.PurchasePaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchasePaymentRequestRepository extends JpaRepository<PurchasePaymentRequest, Long>, JpaSpecificationExecutor<PurchasePaymentRequest> {
    List<PurchasePaymentRequest> findByPurchaseOrderIdOrderByIdDesc(Long purchaseOrderId);
    Optional<PurchasePaymentRequest> findByRequestNo(String requestNo);
}

