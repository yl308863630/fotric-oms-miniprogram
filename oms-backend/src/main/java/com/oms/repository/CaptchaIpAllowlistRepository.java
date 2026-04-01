package com.oms.repository;

import com.oms.entity.CaptchaIpAllowlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaptchaIpAllowlistRepository extends JpaRepository<CaptchaIpAllowlist, Long> {

    List<CaptchaIpAllowlist> findAllByOrderByEnabledDescUpdateTimeDescIdDesc();

    Optional<CaptchaIpAllowlist> findByIpCidr(String ipCidr);
}
