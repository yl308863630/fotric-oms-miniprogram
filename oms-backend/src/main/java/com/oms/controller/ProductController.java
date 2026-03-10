package com.oms.controller;

import com.oms.entity.Product;
import com.oms.service.ProductService;
import com.oms.util.ExcelExportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping("/import")
    public ResponseEntity<String> importProducts(@RequestParam("file") MultipartFile file) {
        try {
            productService.importProducts(file);
            return ResponseEntity.ok("导入成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        String[] headers = {"商品名称", "商品编码", "条形码", "品牌", "型号", "规格", "单位", "分类", "销售价", "库存", "竞品链接", "备注"};
        String[] fields = {"name", "code", "barcode", "brand", "model", "specs", "unit", "category", "price", "stock", "competitorLink", "remark"};
        
        Map<Integer, String[]> dropdowns = new HashMap<>();
        dropdowns.put(7, new String[]{"办公用品", "电子设备", "耗材"}); // 分类在第7列 (0-indexed)
        
        byte[] content = ExcelExportUtil.exportToExcel(new ArrayList<>(), headers, fields, dropdowns);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=product_template.xlsx")
                .contentType(MediaType.parseMediaType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(content);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportProducts() throws IOException {
        List<Product> products = productService.getAllProducts();
        String[] headers = {"商品名称", "商品编码", "条形码", "品牌", "型号", "规格", "单位", "分类", "销售价", "库存", "竞品链接", "备注", "新增时间", "更新时间"};
        String[] fields = {"name", "code", "barcode", "brand", "model", "specs", "unit", "category", "price", "stock", "competitorLink", "remark", "createTime", "updateTime"};
        
        Map<Integer, String[]> dropdowns = new HashMap<>();
        dropdowns.put(7, new String[]{"办公用品", "电子设备", "耗材"});
        
        byte[] content = ExcelExportUtil.exportToExcel(products, headers, fields, dropdowns);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=products_export.xlsx")
                .contentType(MediaType.parseMediaType(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(content);
    }

    @GetMapping
    public Page<Product> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.searchProductsWithPagination(name, brand, category, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PostMapping
    public Product save(@RequestBody Product product) {
        return productService.saveProduct(product);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @RequestBody Product product) {
        System.out.println("=== 收到更新商品请求，ID: " + id);
        System.out.println("=== 商品数据: " + product);
        System.out.println("=== 物料号 (materialNo): " + product.getMaterialNo());
        Product result = productService.updateProduct(id, product);
        System.out.println("=== 更新后商品: " + result);
        System.out.println("=== 更新后物料号 (materialNo): " + (result != null ? result.getMaterialNo() : null));
        return result;
    }

    @PatchMapping("/{id}/status")
    public Product updateStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> status) {
        return productService.updateProductStatus(id, status.get("isActive"));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}
