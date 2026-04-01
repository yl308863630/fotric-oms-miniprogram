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

    /** 同一工业电商销售订单号可能有多条（多商品、链式单等），按 id 排序便于取主单 */
    List<SalesOrder> findAllByOmsOrderNoOrderByIdAsc(String omsOrderNo);
    List<SalesOrder> findByPlatformOrderNo(String platformOrderNo);
    List<SalesOrder> findByOrderNo(String orderNo);
    List<SalesOrder> findByTrackingNumber(String trackingNumber);

    List<SalesOrder> findByInvoiceNumber(String invoiceNumber);

    List<SalesOrder> findByMasterIdOrderByIdAsc(Long masterId);

    List<SalesOrder> findByAllocationIdOrderByIdAsc(Long allocationId);

    /** 按回单物流单号查订单（顺丰兜底：未传 checkPhoneNo 时从库中取回单收件手机） */
    List<SalesOrder> findByReturnReceiptTrackingNumber(String returnReceiptTrackingNumber);
    
    // 查找指定前缀的最新订单，用于生成流水号
    Optional<SalesOrder> findFirstByOmsOrderNoStartingWithOrderByOmsOrderNoDesc(String prefix);

    // 链路单幂等：同一来源单 + 同一创建人，取最新
    Optional<SalesOrder> findFirstByPurchaseOrderNoAndCreatedByOrderByIdDesc(String purchaseOrderNo, Long createdBy);

    /** 按采购单号精确查询（用于主单同步到链式单：CHAIN_FROM:主单id） */
    java.util.List<SalesOrder> findByPurchaseOrderNo(String purchaseOrderNo);

    /** 按状态查询（OrderAssignReminderService 定时任务用） */
    List<SalesOrder> findByStatus(String status);

    /** 按状态与是否需签收单查询（ReceiptReminderService 定时任务用） */
    List<SalesOrder> findByStatusAndNeedReceiptSlip(String status, Boolean needReceiptSlip);

    /** 合同文件路径精确匹配（用于预览鉴权） */
    List<SalesOrder> findByContractUrl(String contractUrl);

    /** 合同 URL 以某后缀结尾（兼容库存储是否带前导 /、或带域名前缀） */
    List<SalesOrder> findByContractUrlEndingWith(String suffix);
}
