package com.oms.repository;

import com.oms.entity.OpportunityProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityProductRepository extends JpaRepository<OpportunityProduct, Long> {
    List<OpportunityProduct> findByOpportunityId(Long opportunityId);
    void deleteByOpportunityId(Long opportunityId);
}