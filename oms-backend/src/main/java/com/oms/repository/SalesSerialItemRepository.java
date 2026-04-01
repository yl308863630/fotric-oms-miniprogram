package com.oms.repository;

import com.oms.entity.SalesSerialItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesSerialItemRepository extends JpaRepository<SalesSerialItem, Long> {
    Optional<SalesSerialItem> findBySnCode(String snCode);

    boolean existsBySnCode(String snCode);

    List<SalesSerialItem> findByMasterIdOrderByIdAsc(Long masterId);

    List<SalesSerialItem> findBySalesOrderIdOrderByIdAsc(Long salesOrderId);

    List<SalesSerialItem> findByAllocationIdOrderByIdAsc(Long allocationId);

    List<SalesSerialItem> findByBatchIdOrderByIdAsc(Long batchId);
}
