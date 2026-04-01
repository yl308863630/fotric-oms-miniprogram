package com.oms.dto;

import lombok.Data;

/**
 * 百度「增值税发票识别」接口（/ocr/v1/vat_invoice）解析后的常用字段。
 * <p>
 * 与 {@link com.oms.service.ocr.BaiduOcrService} 中通用文字识别（general_basic）独立。
 */
@Data
public class BaiduVatInvoiceResult {

    private String purchaserName;
    private String sellerName;
    private String invoiceNum;
    /** 开票日期原始字符串，如 2026年01月13日 或 2026-01-13 */
    private String invoiceDateRaw;

    public boolean hasAnyField() {
        return notBlank(purchaserName) || notBlank(sellerName) || notBlank(invoiceNum) || notBlank(invoiceDateRaw);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
