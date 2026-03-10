package com.oms.repository;

import com.oms.entity.Opportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findAllByOrderByCreateTimeDesc();
    Page<Opportunity> findAllByOrderByCreateTimeDesc(Pageable pageable);
}
