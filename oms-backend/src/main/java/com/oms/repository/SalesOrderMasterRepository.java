package com.oms.repository;

import com.oms.entity.SalesOrderMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderMasterRepository extends JpaRepository<SalesOrderMaster, Long>, JpaSpecificationExecutor<SalesOrderMaster> {
    Optional<SalesOrderMaster> findByMasterNo(String masterNo);

    Optional<SalesOrderMaster> findFirstByRootOmsOrderNoOrderByIdDesc(String rootOmsOrderNo);

    List<SalesOrderMaster> findByPlatformOrderNoOrderByIdDesc(String platformOrderNo);

    List<SalesOrderMaster> findByPartyATitleOrderByIdDesc(String partyATitle);

    Optional<SalesOrderMaster> findFirstByPlatformOrderNoAndPartyATitleAndCreatedByOrderByIdDesc(
            String platformOrderNo, String partyATitle, Long createdBy);

    Optional<SalesOrderMaster> findFirstByPlatformOrderNoAndPlatformNameAndCreatedByOrderByIdDesc(
            String platformOrderNo, String platformName, Long createdBy);
}
