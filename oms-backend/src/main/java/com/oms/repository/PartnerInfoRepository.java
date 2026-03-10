package com.oms.repository;

import com.oms.entity.PartnerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerInfoRepository extends JpaRepository<PartnerInfo, Long>, JpaSpecificationExecutor<PartnerInfo> {
    PartnerInfo findByTitle(String title);

    /** 按 title 去除首尾空格匹配，避免「合作管理」中录入有空格导致乙方资料查不到 */
    @Query(value = "SELECT * FROM partner_info WHERE TRIM(title) = TRIM(:title) LIMIT 1", nativeQuery = true)
    Optional<PartnerInfo> findOneByTitleTrimmed(@Param("title") String title);
}