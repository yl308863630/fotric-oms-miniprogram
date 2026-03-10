package com.oms.repository;

import com.oms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
    Optional<PurchaseOrder> findFirstByOmsOrderNoAndCreatedByOrderByIdDesc(String omsOrderNo, Long createdBy);
    /** 转派时按 订单号+制单人+供应商 唯一，避免覆盖「指派方→甲方」的采购单 */
    Optional<PurchaseOrder> findFirstByOmsOrderNoAndCreatedByAndSupplierOrderByIdDesc(String omsOrderNo, Long createdBy, String supplier);
}
