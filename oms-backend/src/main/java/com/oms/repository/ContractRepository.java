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
    Optional<Contract> findFirstByContractNoStartingWithOrderByContractNoDesc(String prefix);
    Optional<Contract> findBySalesOrderId(Long salesOrderId);
    List<Contract> findAllBySalesOrderId(Long salesOrderId);
    List<Contract> findAllBySalesOrderIdInOrderByIdDesc(List<Long> salesOrderIds);
    List<Contract> findByMasterIdOrderByIdDesc(Long masterId);
    List<Contract> findAllByMergeSelectionKeyInOrderByIdDesc(List<String> mergeSelectionKeys);
    Optional<Contract> findFirstByMasterIdAndSalesIdAndMergeSelectionKeyOrderByIdDesc(Long masterId, Long salesId, String mergeSelectionKey);
    List<Contract> findByStatusAndPartyBSignedFalse(String status);
    Page<Contract> findByStatus(String status, Pageable pageable);
}
