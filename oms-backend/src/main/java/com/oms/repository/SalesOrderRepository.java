package com.oms.repository;

import com.oms.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {
    Optional<SalesOrder> findByOmsOrderNo(String omsOrderNo);
    List<SalesOrder> findByTrackingNumber(String trackingNumber);
    
    // 查找指定前缀的最新订单，用于生成流水号
    Optional<SalesOrder> findFirstByOmsOrderNoStartingWithOrderByOmsOrderNoDesc(String prefix);

    // 链路单幂等：同一来源单 + 同一创建人，取最新
    Optional<SalesOrder> findFirstByPurchaseOrderNoAndCreatedByOrderByIdDesc(String purchaseOrderNo, Long createdBy);
}
