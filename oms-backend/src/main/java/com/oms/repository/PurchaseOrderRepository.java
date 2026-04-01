package com.oms.repository;

import com.oms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
    Optional<PurchaseOrder> findFirstByPurchaseOrderNoOrderByIdDesc(String purchaseOrderNo);
    Optional<PurchaseOrder> findFirstByOmsOrderNoAndCreatedByOrderByIdDesc(String omsOrderNo, Long createdBy);
    /** 转派时按 订单号+制单人+供应商 唯一，避免覆盖「指派方→甲方」的采购单 */
    Optional<PurchaseOrder> findFirstByOmsOrderNoAndCreatedByAndSupplierOrderByIdDesc(String omsOrderNo, Long createdBy, String supplier);
    /** 按订单号+供应商查采购单（用于合同双章时同步更新指派方的采购单状态为已盖章） */
    List<PurchaseOrder> findByOmsOrderNoAndSupplier(String omsOrderNo, String supplier);
    List<PurchaseOrder> findByOmsOrderNo(String omsOrderNo);
    List<PurchaseOrder> findByMasterIdOrderByIdDesc(Long masterId);
}
