package com.oms.repository;

import com.oms.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {
    Optional<Contract> findByContractNo(String contractNo);
    Optional<Contract> findBySalesOrderId(Long salesOrderId);
    List<Contract> findAllBySalesOrderId(Long salesOrderId);
    Page<Contract> findByStatus(String status, Pageable pageable);
}
