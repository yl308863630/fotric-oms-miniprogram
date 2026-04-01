package com.oms.repository;

import com.oms.entity.SalesOrderAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderAllocationRepository extends JpaRepository<SalesOrderAllocation, Long> {
    Optional<SalesOrderAllocation> findByAllocationNo(String allocationNo);

    List<SalesOrderAllocation> findByMasterIdOrderByIdAsc(Long masterId);

    List<SalesOrderAllocation> findBySalesOrderIdOrderByIdAsc(Long salesOrderId);

    List<SalesOrderAllocation> findByRootAllocationIdOrderByIdAsc(Long rootAllocationId);

    List<SalesOrderAllocation> findByParentAllocationIdOrderByIdAsc(Long parentAllocationId);

    List<SalesOrderAllocation> findByAssignedUserIdOrderByIdDesc(Long assignedUserId);
}
