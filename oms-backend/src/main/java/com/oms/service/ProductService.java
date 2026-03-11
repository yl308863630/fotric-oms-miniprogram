package com.oms.service;

import com.oms.entity.Product;
import com.oms.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DingTalkService dingTalkService;

    @Transactional
    public void importProducts(MultipartFile file) throws Exception {
        List<Product> products = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // 跳过表头
            if (rows.hasNext()) {
                rows.next();
            }

            while (rows.hasNext()) {
                Row row = rows.next();
                Product product = new Product();
                
                // 根据模板列顺序解析数据
                // 0:商品名称, 1:商品编码, 2:条形码, 3:品牌, 4:型号, 5:规格, 6:单位, 7:分类, 8:销售价, 9:库存, 10:竞品链接, 11:备注
                product.setName(getCellValue(row.getCell(0)));
                product.setCode(getCellValue(row.getCell(1)));
                product.setBarcode(getCellValue(row.getCell(2)));
                product.setBrand(getCellValue(row.getCell(3)));
                product.setModel(getCellValue(row.getCell(4)));
                product.setSpecs(getCellValue(row.getCell(5)));
                product.setUnit(getCellValue(row.getCell(6)));
                product.setCategory(getCellValue(row.getCell(7)));
                
                String priceStr = getCellValue(row.getCell(8));
                if (priceStr != null && !priceStr.isEmpty()) {
                    product.setPrice(new BigDecimal(priceStr));
                }
                
                String stockStr = getCellValue(row.getCell(9));
                if (stockStr != null && !stockStr.isEmpty()) {
                    product.setStock((int) Double.parseDouble(stockStr));
                }
                
                product.setCompetitorLink(getCellValue(row.getCell(10)));
                product.setRemark(getCellValue(row.getCell(11)));
                product.setIsActive(true);
                
                products.add(product);
            }
        }
        productRepository.saveAll(products);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> searchProducts(String name, String brand, String category) {
        return productRepository.findAll((Specification<Product>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (brand != null && !brand.isEmpty()) {
                predicates.add(cb.like(root.get("brand"), "%" + brand + "%"));
            }
            if (category != null && !category.isEmpty()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            // 倒序：优先按更新时间
            try {
                query.orderBy(cb.desc(root.get("updateTime")));
            } catch (Exception ignored) { }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public Page<Product> searchProductsWithPagination(String name, String brand, String category, Pageable pageable) {
        return productRepository.findAll((Specification<Product>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (brand != null && !brand.isEmpty()) {
                predicates.add(cb.like(root.get("brand"), "%" + brand + "%"));
            }
            if (category != null && !category.isEmpty()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            // 倒序：优先按更新时间
            try {
                query.orderBy(cb.desc(root.get("updateTime")));
            } catch (Exception ignored) { }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public Product getProductById(Long id) {
        if (id == null) return null;
        return productRepository.findById(id).orElse(null);
    }

    @Transactional
    public Product saveProduct(Product product) {
        if (product == null) return null;
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, Product product) {
        if (id == null || product == null) return null;
        Product existingProduct = productRepository.findById(id).orElse(null);
        if (existingProduct == null) return null;
        
        // 更新字段
        existingProduct.setName(product.getName());
        existingProduct.setCode(product.getCode());
        existingProduct.setBarcode(product.getBarcode());
        existingProduct.setMaterialNo(product.getMaterialNo());
        existingProduct.setBrand(product.getBrand());
        existingProduct.setModel(product.getModel());
        existingProduct.setSpecs(product.getSpecs());
        existingProduct.setProductConfig(product.getProductConfig());
        existingProduct.setWarrantyPeriod(product.getWarrantyPeriod());
        existingProduct.setUnit(product.getUnit());
        existingProduct.setCategory(product.getCategory());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setStock(product.getStock());
        existingProduct.setCompetitorLink(product.getCompetitorLink());
        existingProduct.setRemark(product.getRemark());
        existingProduct.setImage(product.getImage());
        
        Product savedProduct = productRepository.save(existingProduct);
        
        // 库存低于10时发送预警
        if (product.getStock() != null && product.getStock() < 10) {
            try {
                dingTalkService.sendStockWarning(
                    savedProduct.getName() != null ? savedProduct.getName() : "未命名商品",
                    savedProduct.getCode() != null ? savedProduct.getCode() : "未编码",
                    product.getStock()
                );
            } catch (Exception e) {
                System.err.println("发送钉钉库存预警通知失败: " + e.getMessage());
            }
        }
        
        return savedProduct;
    }

    @Transactional
    public Product updateProductStatus(Long id, Boolean isActive) {
        if (id == null) return null;
        Product existingProduct = productRepository.findById(id).orElse(null);
        if (existingProduct == null) return null;
        
        existingProduct.setIsActive(isActive != null ? isActive : true);
        return productRepository.save(existingProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (id != null) {
            productRepository.deleteById(id);
        }
    }
}
