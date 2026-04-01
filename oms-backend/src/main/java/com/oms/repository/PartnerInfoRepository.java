package com.oms.repository;

import com.oms.entity.PartnerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerInfoRepository extends JpaRepository<PartnerInfo, Long>, JpaSpecificationExecutor<PartnerInfo> {
    PartnerInfo findByTitle(String title);

    /** 按 title 去除首尾空格匹配，避免「合作管理」中录入有空格导致乙方资料查不到 */
    @Query(value = "SELECT * FROM partner_info WHERE TRIM(title) = TRIM(:title) LIMIT 1", nativeQuery = true)
    Optional<PartnerInfo> findOneByTitleTrimmed(@Param("title") String title);

    /** 按 name（名称）去除首尾空格匹配，与抬头信息二选一便于匹配 */
    @Query(value = "SELECT * FROM partner_info WHERE TRIM(name) = TRIM(:name) LIMIT 1", nativeQuery = true)
    Optional<PartnerInfo> findOneByNameTrimmed(@Param("name") String name);

    /** 交付方简称匹配：title 或 name 包含传入字符串，便于「上海热像科技股份」匹配到「上海热像科技股份有限公司」 */
    @Query(value = "SELECT * FROM partner_info WHERE (title IS NOT NULL AND TRIM(title) LIKE CONCAT('%', TRIM(:prefix), '%')) OR (name IS NOT NULL AND TRIM(name) LIKE CONCAT('%', TRIM(:prefix), '%')) LIMIT 1", nativeQuery = true)
    Optional<PartnerInfo> findFirstByTitleOrNameContaining(@Param("prefix") String prefix);

    /** 按抬头/名称精确匹配（去空格）返回该抬头下全部记录，用于同一公司多用户时指派选唯一用户名 */
    @Query(value = "SELECT * FROM partner_info WHERE TRIM(COALESCE(title, '')) = TRIM(:t) OR TRIM(COALESCE(name, '')) = TRIM(:t)", nativeQuery = true)
    List<PartnerInfo> findAllByTitleOrNameTrimmed(@Param("t") String t);

    /** 按用户名查全部记录，用于可见性：当前用户作为某交付方乙方业务员时可见该交付方的订单 */
    List<PartnerInfo> findByUsername(String username);

    /** 按用户名（去空格后）查全部记录，用于唯一性校验：合作管理-用户信息维护中用户名全局唯一 */
    @Query(value = "SELECT * FROM partner_info WHERE username IS NOT NULL AND TRIM(username) = TRIM(:username)", nativeQuery = true)
    List<PartnerInfo> findAllByUsernameTrimmed(@Param("username") String username);

    @Query(value = """
            SELECT * FROM partner_info
            WHERE TRIM(COALESCE(title, '')) = TRIM(:title)
              AND TRIM(COALESCE(contact_person, '')) = TRIM(:contactPerson)
            """, nativeQuery = true)
    List<PartnerInfo> findAllByTitleAndContactPerson(@Param("title") String title, @Param("contactPerson") String contactPerson);

    @Query(value = """
            SELECT * FROM partner_info
            WHERE TRIM(COALESCE(title, '')) = TRIM(:title)
              AND TRIM(COALESCE(contact_phone, '')) = TRIM(:contactPhone)
            """, nativeQuery = true)
    List<PartnerInfo> findAllByTitleAndContactPhone(@Param("title") String title, @Param("contactPhone") String contactPhone);

    /** 按银行账号（忽略空格）匹配，用于 OCR 回填合作方资料 */
    @Query(value = "SELECT * FROM partner_info WHERE REPLACE(COALESCE(bank_account, ''), ' ', '') = REPLACE(TRIM(:bankAccount), ' ', '') LIMIT 1", nativeQuery = true)
    Optional<PartnerInfo> findFirstByBankAccountNormalized(@Param("bankAccount") String bankAccount);
}