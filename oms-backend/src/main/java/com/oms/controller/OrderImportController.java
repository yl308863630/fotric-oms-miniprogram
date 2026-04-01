package com.oms.controller;

import com.oms.dto.OrderImportResult;
import com.oms.service.OrderImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 订单导入：上传 PDF（震坤行等平台订单）解析并模糊匹配，返回可预填「新建销售订单」的数据。
 * 随 oms-backend 打在同一 JAR，部署 ECS 即可使用。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderImportController {

    @Autowired
    private OrderImportService orderImportService;

    /**
     * 从 PDF 解析订单并模糊匹配客户/商品，返回建议的销售订单字段。
     * 前端可用返回结果预填「新建销售订单」表单。
     *
     * @param file Word/PDF/图片；OCR 优先内置 RapidOCR，可选配置 baidu.ocr 调用百度 OCR 作补充，亦可安装 Tesseract
     * @return 解析与匹配结果，含 platformOrderNo、partyATitle、model、quantity、金额、收货人等
     */
    @PostMapping("/import-from-file")
    public ResponseEntity<OrderImportResult> importFromFile(@RequestParam("file") MultipartFile file) {
        OrderImportResult result = orderImportService.importFromFile(file);
        return ResponseEntity.ok(result);
    }
}
