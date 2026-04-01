package com.oms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InvoiceOcrValidationResult {
    private boolean success;
    private String message;
    private String fileType;
    private String invoiceNumber;
    private String invoiceDate;
    private String buyerTitle;
    private String sellerTitle;
    private String expectedBuyerTitle;
    private String expectedSellerTitle;
    private boolean buyerMatched = true;
    private boolean sellerMatched = true;
    private boolean duplicateInvoiceNumber;
    private boolean requiresConfirmation;
    private int rawTextLength;
    /** 是否合并了百度增值税发票专用接口的结构化字段（与本地 PDF/正则解析独立板块） */
    private Boolean baiduVatInvoiceUsed;
    private List<String> warnings = new ArrayList<>();
}
