package com.oms.repository;

import com.oms.entity.PartyAPaymentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartyAPaymentRuleRepository extends JpaRepository<PartyAPaymentRule, Long>, JpaSpecificationExecutor<PartyAPaymentRule> {
    @Query(value = "SELECT * FROM party_a_payment_rules WHERE TRIM(party_a_title) = TRIM(:title) ORDER BY update_time DESC LIMIT 1", nativeQuery = true)
    Optional<PartyAPaymentRule> findLatestByPartyATitleTrimmed(@Param("title") String title);

    @Query(value = "SELECT * FROM party_a_payment_rules WHERE enabled = 1 ORDER BY update_time DESC", nativeQuery = true)
    List<PartyAPaymentRule> findAllEnabledOrderByUpdateTimeDesc();

    @Query(value = "SELECT * FROM party_a_payment_rules WHERE TRIM(party_a_title) = TRIM(:title)", nativeQuery = true)
    List<PartyAPaymentRule> findAllByPartyATitleTrimmed(@Param("title") String title);
}
