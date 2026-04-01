package com.oms.repository;

import com.oms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    /** 按型号取第一个匹配的商品，用于合同产品名称取自「我的商品-型号列表」的商品名称 */
    Optional<Product> findFirstByModel(String model);
    Optional<Product> findFirstByCodeIgnoreCase(String code);
    Optional<Product> findFirstByMaterialNoIgnoreCase(String materialNo);
    Optional<Product> findFirstByCodeStartingWithIgnoreCase(String codePrefix);
    Optional<Product> findFirstByMaterialNoStartingWithIgnoreCase(String materialNoPrefix);
}
