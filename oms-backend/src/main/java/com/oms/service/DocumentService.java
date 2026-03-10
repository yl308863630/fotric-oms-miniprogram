package com.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.Contract;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DocumentService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private SealService sealService;

    public String generateContractDocument(Contract contract, String templateUrl) throws Exception {
        return generateContractDocumentInternal(contract, templateUrl, null);
    }

    public String generateContractDocumentWithPartySeal(Contract contract, String templateUrl, String partyType) throws Exception {
        return generateContractDocumentInternal(contract, templateUrl, partyType);
    }

    private String generateContractDocumentInternal(Contract contract, String templateUrl, String partyType) throws Exception {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path targetDir = Paths.get(uploadDir, dateStr);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        String fileName = contract.getContractNo() + "_" + System.currentTimeMillis() + ".docx";
        Path targetPath = targetDir.resolve(fileName);

        InputStream templateStream;
        
        String actualTemplateUrl = templateUrl;
        if (contract.getGeneratedUrl() != null && !contract.getGeneratedUrl().isEmpty()) {
            actualTemplateUrl = contract.getGeneratedUrl();
            System.out.println("使用已有带章合同作为模板: " + actualTemplateUrl);
        } else {
            System.out.println("使用原始模板: " + actualTemplateUrl);
        }
        // 前端可能传带域名的完整 URL（如 http://localhost:8080/uploads/xxx.docx），改为从磁盘读，避免自请求 HTTP 导致 404 或拿到非 docx 内容
        actualTemplateUrl = normalizeTemplateUrl(actualTemplateUrl);
        if (actualTemplateUrl == null || actualTemplateUrl.isBlank()) {
            throw new IOException("未指定合同模板，请在指派时选择或配置合同模板。");
        }
        
        Path resolvedPath = null;
        if (actualTemplateUrl.startsWith("http://") || actualTemplateUrl.startsWith("https://")) {
            URL url = new URL(actualTemplateUrl);
            templateStream = url.openStream();
        } else {
            resolvedPath = resolveTemplatePath(actualTemplateUrl);
            templateStream = Files.newInputStream(resolvedPath);
        }

        XWPFDocument document;
        try {
            document = new XWPFDocument(templateStream);
        } catch (Exception e) {
            templateStream.close();
            String pathHint = (resolvedPath != null) ? (" 实际使用的模板路径: " + resolvedPath.toAbsolutePath()) : "";
            throw new IOException("模板文件不是有效的 Word 文档(.docx)，请检查合同模板是否已正确上传或重新上传；若模板未改动，请确认系统「合同模板」配置指向的正是该文件，或尝试用 Word 另存为一次 .docx 后再上传。" + pathHint + " 原始错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
        templateStream.close();

        Map<String, String> placeholders = buildPlaceholders(contract);
        
        replacePlaceholdersWithPartySeal(document, placeholders, partyType);

        FileOutputStream out = new FileOutputStream(targetPath.toFile());
        document.write(out);
        out.close();
        document.close();

        return "/uploads/" + dateStr + "/" + fileName;
    }

    /** 若为指向 /uploads/ 的 http(s) URL，转为路径，从磁盘读取，避免自请求失败或拿到错误内容 */
    private String normalizeTemplateUrl(String url) {
        if (url == null || url.isBlank()) return url;
        String u = url.trim();
        if (u.startsWith("http://") || u.startsWith("https://")) {
            try {
                java.net.URL parsed = new URL(u);
                String path = parsed.getPath();
                if (path != null && path.startsWith("/uploads/")) {
                    System.out.println("合同模板 URL 转为本地路径: " + path);
                    return path;
                }
            } catch (Exception ignored) { }
        }
        return url;
    }

    /** 解析模板路径：支持 /uploads/xxx 或相对路径，若文件不存在且无 .docx 后缀则尝试加 .docx；不存在则抛明确异常。仅支持 .docx，不支持旧版 .doc */
    private Path resolveTemplatePath(String actualTemplateUrl) throws IOException {
        String lower = actualTemplateUrl == null ? "" : actualTemplateUrl.toLowerCase();
        if (lower.endsWith(".doc") && !lower.endsWith(".docx")) {
            throw new IOException("合同模板仅支持 .docx 格式（Word 2007 及以上），不支持旧版 .doc 格式。请用 Word 打开该文档，选择「另存为」→「Word 文档 (*.docx)」后重新上传。");
        }
        Path templatePath;
        if (actualTemplateUrl.startsWith("/uploads/")) {
            String relativePath = actualTemplateUrl.substring("/uploads/".length());
            templatePath = Paths.get(uploadDir, relativePath);
        } else {
            templatePath = Paths.get(uploadDir, actualTemplateUrl);
        }
        if (!Files.exists(templatePath) && !actualTemplateUrl.endsWith(".docx")) {
            Path withExt = Paths.get(uploadDir, actualTemplateUrl + ".docx");
            if (Files.exists(withExt)) return withExt;
        }
        if (!Files.exists(templatePath)) {
            throw new IOException("模板文件不存在: " + templatePath.toAbsolutePath() + "，请检查合同模板名称或上传模板到 " + uploadDir);
        }
        return templatePath;
    }

    private Map<String, String> buildPlaceholders(Contract contract) {
        System.out.println("=== buildPlaceholders called ===");
        System.out.println("contract.getContractNo(): " + contract.getContractNo());
        System.out.println("contract.getPartyAName(): " + contract.getPartyAName());
        System.out.println("contract.getPartyBName(): " + contract.getPartyBName());
        if (contract.getPartyBName() == null || contract.getPartyBName().isBlank()) {
            System.err.println("[buildPlaceholders] 乙方名称为空，合同内 ${partyBName}/${乙方} 将替换为空；${partyBSeal} 不会插入。请检查订单 deliveryParty 及 ensureContractPartyB 是否已执行。");
        }
        if (contract.getSettlementPartyName() == null || contract.getSettlementPartyName().isBlank()) {
            System.out.println("[buildPlaceholders] 结算方为空（非工业电商合同），${settlementParty} 将替换为空字符串。");
        }
        System.out.println("contract.getProductModel(): " + contract.getProductModel());
        System.out.println("contract.getMaterialNo(): " + contract.getMaterialNo());
        System.out.println("contract.getProductConfig(): " + contract.getProductConfig());
        System.out.println("contract.getWarrantyPeriod(): " + contract.getWarrantyPeriod());
        System.out.println("contract.getQuantity(): " + contract.getQuantity());
        System.out.println("contract.getUnitPrice(): " + contract.getUnitPrice());
        System.out.println("contract.getTotalAmount(): " + contract.getTotalAmount());
        System.out.println("contract.getAmount(): " + contract.getAmount());
        System.out.println("contract.getAmountCn(): " + contract.getAmountCn());
        System.out.println("contract.getPlatformName(): " + contract.getPlatformName());
        System.out.println("contract.getPaymentMethod(): " + contract.getPaymentMethod());
        System.out.println("contract.getDeliveryAddress(): " + contract.getDeliveryAddress());
        System.out.println("contract.getDeliveryDate(): " + contract.getDeliveryDate());
        System.out.println("contract.getOrderDate(): " + contract.getOrderDate());
        System.out.println("contract.getDeliveryCycle(): " + contract.getDeliveryCycle());
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("${contractNo}", contract.getContractNo() != null ? contract.getContractNo() : "");
        placeholders.put("${partyA}", contract.getPartyA() != null ? contract.getPartyA() : "");
        placeholders.put("${partyB}", contract.getPartyB() != null ? contract.getPartyB() : "");
        placeholders.put("${partyAName}", contract.getPartyAName() != null ? contract.getPartyAName() : "");
        placeholders.put("${partyBName}", contract.getPartyBName() != null ? contract.getPartyBName() : "");
        placeholders.put("${partyATaxNo}", contract.getPartyATaxNo() != null ? contract.getPartyATaxNo() : "");
        placeholders.put("${partyBTaxNo}", contract.getPartyBTaxNo() != null ? contract.getPartyBTaxNo() : "");
        placeholders.put("${partyAAddress}", contract.getPartyAAddress() != null ? contract.getPartyAAddress() : "");
        placeholders.put("${partyBAddress}", contract.getPartyBAddress() != null ? contract.getPartyBAddress() : "");
        placeholders.put("${partyABank}", contract.getPartyABank() != null ? contract.getPartyABank() : "");
        placeholders.put("${partyBBank}", contract.getPartyBBank() != null ? contract.getPartyBBank() : "");
        placeholders.put("${partyAAccount}", contract.getPartyAAccount() != null ? contract.getPartyAAccount() : "");
        placeholders.put("${partyBAccount}", contract.getPartyBAccount() != null ? contract.getPartyBAccount() : "");
        placeholders.put("${partyAPhone}", contract.getPartyAPhone() != null ? contract.getPartyAPhone() : "");
        placeholders.put("${partyBPhone}", contract.getPartyBPhone() != null ? contract.getPartyBPhone() : "");
        placeholders.put("${productModel}", contract.getProductModel() != null ? contract.getProductModel() : "");
        placeholders.put("${productName}", contract.getProductName() != null ? contract.getProductName() : "");
        placeholders.put("${materialNo}", contract.getMaterialNo() != null ? contract.getMaterialNo() : "");
        placeholders.put("${productConfig}", contract.getProductConfig() != null ? contract.getProductConfig() : "");
        placeholders.put("${warrantyPeriod}", contract.getWarrantyPeriod() != null ? contract.getWarrantyPeriod() : "");
        placeholders.put("${quantity}", contract.getQuantity() != null ? String.valueOf(contract.getQuantity()) : "");
        placeholders.put("${unitPrice}", contract.getUnitPrice() != null ? contract.getUnitPrice().toString() : "0");
        placeholders.put("${totalAmount}", contract.getTotalAmount() != null ? contract.getTotalAmount().toString() : "0");
        placeholders.put("${amount}", contract.getAmount() != null ? contract.getAmount().toString() : "0");
        placeholders.put("${amountCn}", contract.getAmountCn() != null ? contract.getAmountCn() : "");
        placeholders.put("${signDate}", contract.getSignDate() != null ? contract.getSignDate().toString() : LocalDate.now().toString());
        placeholders.put("${salesName}", contract.getSalesName() != null ? contract.getSalesName() : "");
        placeholders.put("${platformName}", contract.getPlatformName() != null ? contract.getPlatformName() : "");
        // 结算方式及期限：仅工业电商合同有值（顶层客户名）；其他用户合同为空，占位符替换为空字符串，界面不显示
        placeholders.put("${settlementParty}", contract.getSettlementPartyName() != null ? contract.getSettlementPartyName() : "");
        placeholders.put("${partyATitle}", contract.getPlatformName() != null ? contract.getPlatformName() : "");
        placeholders.put("${partyAOrderNo}", contract.getPartyAOrderNo() != null ? contract.getPartyAOrderNo() : "");
        placeholders.put("${paymentMethod}", contract.getPaymentMethod() != null ? contract.getPaymentMethod() : "");
        placeholders.put("${甲方}", contract.getPartyAName() != null ? contract.getPartyAName() : "");
        placeholders.put("${乙方}", contract.getPartyBName() != null ? contract.getPartyBName() : "");
        placeholders.put("${deliveryAddress}", contract.getDeliveryAddress() != null ? contract.getDeliveryAddress() : "");
        placeholders.put("${deliveryDate}", contract.getDeliveryDate() != null ? contract.getDeliveryDate().toString() : "");
        placeholders.put("${orderDate}", contract.getOrderDate() != null ? contract.getOrderDate().toString() : "");
        placeholders.put("${deliveryCycle}", contract.getDeliveryCycle() != null ? String.valueOf(contract.getDeliveryCycle()) : "");
        placeholders.put("${partyBRepresentative}", contract.getPartyBRepresentative() != null ? contract.getPartyBRepresentative() : "");
        
        System.out.println("=== Placeholders built ===");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            System.out.println(entry.getKey() + " = '" + entry.getValue() + "'");
        }
        
        return placeholders;
    }

    private void replacePlaceholders(XWPFDocument document, Map<String, String> placeholders) {
        replacePlaceholdersWithPartySeal(document, placeholders, null);
    }
    
    private void replacePlaceholdersWithPartySeal(XWPFDocument document, Map<String, String> placeholders, String partyType) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceInParagraph(paragraph, placeholders);
            insertSealsInParagraph(paragraph, placeholders, partyType);
        }
        
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        replaceInParagraph(paragraph, placeholders);
                        insertSealsInParagraph(paragraph, placeholders, partyType);
                    }
                }
            }
        }
        
        replaceInContentControls(document, placeholders, partyType);
    }
    
    private void replaceInContentControls(XWPFDocument document, Map<String, String> placeholders, String partyType) {
        try {
            CTBody body = document.getDocument().getBody();
            if (body == null) return;
            
            List<CTSdtBlock> sdtBlocks = body.getSdtList();
            if (sdtBlocks != null) {
                for (CTSdtBlock sdtBlock : sdtBlocks) {
                    try {
                        CTSdtContentBlock sdtContent = sdtBlock.getSdtContent();
                        if (sdtContent != null) {
                            List<CTP> pList = sdtContent.getPList();
                            if (pList != null) {
                                for (CTP ctP : pList) {
                                    XWPFParagraph paragraph = new XWPFParagraph(ctP, document);
                                    replaceInParagraph(paragraph, placeholders);
                                    insertSealsInParagraph(paragraph, placeholders, partyType);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing content control: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing content controls: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }

        StringBuilder fullText = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) {
                fullText.append(text);
            }
        }

        String text = fullText.toString();
        boolean changed = false;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (text.contains(entry.getKey())) {
                text = text.replace(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        if (changed) {
            for (int i = runs.size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            XWPFRun newRun = paragraph.createRun();
            newRun.setText(text);
        }
    }
    
    public String generateContractDocumentWithProducts(Contract contract, String templateUrl, List<Map<String, Object>> products) throws Exception {
        return generateContractDocumentWithProducts(contract, templateUrl, products, null);
    }
    
    public String generateContractDocumentWithProducts(Contract contract, String templateUrl, List<Map<String, Object>> products, String partyType) throws Exception {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path targetDir = Paths.get(uploadDir, dateStr);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        String fileName = contract.getContractNo() + "_" + System.currentTimeMillis() + ".docx";
        Path targetPath = targetDir.resolve(fileName);

        InputStream templateStream;
        
        String actualTemplateUrl = templateUrl;
        if (contract.getGeneratedUrl() != null && !contract.getGeneratedUrl().isEmpty()) {
            actualTemplateUrl = contract.getGeneratedUrl();
            System.out.println("使用已有带章合同作为模板（多产品）: " + actualTemplateUrl);
        } else {
            System.out.println("使用原始模板（多产品）: " + actualTemplateUrl);
        }
        actualTemplateUrl = normalizeTemplateUrl(actualTemplateUrl);
        if (actualTemplateUrl == null || actualTemplateUrl.isBlank()) {
            throw new IOException("未指定合同模板，请在指派时选择或配置合同模板。");
        }
        
        Path resolvedPath = null;
        if (actualTemplateUrl.startsWith("http://") || actualTemplateUrl.startsWith("https://")) {
            URL url = new URL(actualTemplateUrl);
            templateStream = url.openStream();
        } else {
            resolvedPath = resolveTemplatePath(actualTemplateUrl);
            templateStream = Files.newInputStream(resolvedPath);
        }

        XWPFDocument document;
        try {
            document = new XWPFDocument(templateStream);
        } catch (Exception e) {
            templateStream.close();
            String pathHint = (resolvedPath != null) ? (" 实际使用的模板路径: " + resolvedPath.toAbsolutePath()) : "";
            throw new IOException("模板文件不是有效的 Word 文档(.docx)，请检查合同模板是否已正确上传或重新上传；若模板未改动，请确认系统「合同模板」配置指向的正是该文件，或尝试用 Word 另存为一次 .docx 后再上传。" + pathHint + " 原始错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
        templateStream.close();

        Map<String, String> placeholders = buildPlaceholders(contract);
        
        if (products != null && !products.isEmpty()) {
            processProductTable(document, products, placeholders);
        }
        
        replacePlaceholdersWithPartySeal(document, placeholders, partyType);

        FileOutputStream out = new FileOutputStream(targetPath.toFile());
        document.write(out);
        out.close();
        document.close();

        return "/uploads/" + dateStr + "/" + fileName;
    }
    
    private void processProductTable(XWPFDocument document, List<Map<String, Object>> products, Map<String, String> basePlaceholders) {
        for (XWPFTable table : document.getTables()) {
            int templateRowIndex = -1;
            
            for (int i = 0; i < table.getRows().size(); i++) {
                XWPFTableRow row = table.getRow(i);
                String rowText = getRowText(row);
                if (rowText.contains("${productName}") || rowText.contains("${productModel}")) {
                    templateRowIndex = i;
                    break;
                }
            }
            
            if (templateRowIndex != -1) {
                XWPFTableRow templateRow = table.getRow(templateRowIndex);
                
                for (int i = 0; i < products.size(); i++) {
                    Map<String, Object> product = products.get(i);
                    XWPFTableRow newRow;
                    
                    if (i == 0) {
                        newRow = templateRow;
                    } else {
                        newRow = copyTableRow(table, templateRow, templateRowIndex + i);
                    }
                    
                    Map<String, String> productPlaceholders = new HashMap<>(basePlaceholders);
                    productPlaceholders.put("${productName}", getValue(product, "platformSku", ""));
                    productPlaceholders.put("${productModel}", getValue(product, "model", ""));
                    productPlaceholders.put("${productConfig}", getValue(product, "productConfig", ""));
                    productPlaceholders.put("${quantity}", getValue(product, "quantity", "0"));
                    productPlaceholders.put("${unitPrice}", getValue(product, "taxIncludedPrice", "0"));
                    productPlaceholders.put("${totalAmount}", getValue(product, "taxIncludedTotal", "0"));
                    productPlaceholders.put("${warrantyPeriod}", getValue(product, "warrantyPeriod", ""));
                    productPlaceholders.put("${deliveryCycle}", getValue(product, "deliveryCycle", ""));
                    
                    replaceRowText(newRow, productPlaceholders);
                }
                
                if (products.size() > 0) {
                    for (int i = table.getRows().size() - 1; i > templateRowIndex + products.size() - 1; i--) {
                        table.removeRow(i);
                    }
                }
            }
        }
    }
    
    private String getRowText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                sb.append(paragraph.getText());
            }
        }
        return sb.toString();
    }
    
    private XWPFTableRow copyTableRow(XWPFTable table, XWPFTableRow sourceRow, int insertIndex) {
        XWPFTableRow newRow = table.insertNewTableRow(insertIndex);
        
        for (int i = 0; i < sourceRow.getTableCells().size(); i++) {
            XWPFTableCell sourceCell = sourceRow.getCell(i);
            XWPFTableCell newCell = newRow.createCell();
            
            for (XWPFParagraph sourcePara : sourceCell.getParagraphs()) {
                XWPFParagraph newPara = newCell.addParagraph();
                newPara.getCTP().set(sourcePara.getCTP());
            }
            
            if (sourceCell.getCTTc().getTcPr() != null) {
                newCell.getCTTc().setTcPr(sourceCell.getCTTc().getTcPr());
            }
        }
        
        if (sourceRow.getCtRow().getTrPr() != null) {
            newRow.getCtRow().setTrPr(sourceRow.getCtRow().getTrPr());
        }
        
        return newRow;
    }
    
    private void replaceRowText(XWPFTableRow row, Map<String, String> placeholders) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                replaceInParagraph(paragraph, placeholders);
            }
        }
    }
    
    private String getValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
    
    private void insertSealsInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        insertSealsInParagraph(paragraph, placeholders, null);
    }
    
    private void insertSealsInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders, String partyType) {
        String paragraphText = paragraph.getText();
        
        if (partyType == null || "A".equals(partyType)) {
            if (paragraphText.contains("${partyASeal}") && placeholders.containsKey("${partyAName}")) {
                String partyAName = placeholders.get("${partyAName}");
                if (partyAName != null && !partyAName.isEmpty()) {
                    replacePlaceholderWithSeal(paragraph, "${partyASeal}", partyAName);
                    System.out.println("[DocumentService] 已插入甲方章占位符: ${partyASeal} -> " + partyAName);
                } else {
                    System.err.println("[DocumentService] 甲方章未插入: partyAName 为空，模板中 ${partyASeal} 将保留为文字。");
                }
            }
        }
        
        if (partyType == null || "B".equals(partyType)) {
            if (paragraphText.contains("${partyBSeal}") && placeholders.containsKey("${partyBName}")) {
                String partyBName = placeholders.get("${partyBName}");
                if (partyBName != null && !partyBName.isEmpty()) {
                    replacePlaceholderWithSeal(paragraph, "${partyBSeal}", partyBName);
                    System.out.println("[DocumentService] 已插入乙方章占位符: ${partyBSeal} -> " + partyBName);
                } else {
                    System.err.println("[DocumentService] 乙方章未插入: partyBName 为空，模板中 ${partyBSeal} 将保留为文字。");
                }
            }
        }
    }
    
    private void replacePlaceholderWithSeal(XWPFParagraph paragraph, String placeholder, String companyName) {
        String fullText = paragraph.getText();
        if (fullText == null || !fullText.contains(placeholder)) {
            return;
        }
        try {
            byte[] sealBytes = sealService.generateSeal(companyName);
            int emuWidth = Units.toEMU(100);
            int emuHeight = Units.toEMU(100);

            List<XWPFRun> runs = paragraph.getRuns();
            boolean foundInRun = false;
            if (runs != null && !runs.isEmpty()) {
                for (XWPFRun run : runs) {
                    String text = run.getText(0);
                    if (text != null && text.contains(placeholder)) {
                        text = text.replace(placeholder, "");
                        run.setText(text, 0);
                        ByteArrayInputStream sealStream = new ByteArrayInputStream(sealBytes);
                        run.addPicture(sealStream, XWPFDocument.PICTURE_TYPE_PNG, companyName + "_seal.png", emuWidth, emuHeight);
                        sealStream.close();
                        foundInRun = true;
                        break;
                    }
                }
            }
            if (!foundInRun) {
                int idx = fullText.indexOf(placeholder);
                String before = fullText.substring(0, idx);
                String after = fullText.substring(idx + placeholder.length());
                if (runs != null) {
                    for (int i = runs.size() - 1; i >= 0; i--) {
                        paragraph.removeRun(i);
                    }
                }
                if (before != null && !before.isEmpty()) {
                    XWPFRun r1 = paragraph.createRun();
                    r1.setText(before);
                }
                XWPFRun r2 = paragraph.createRun();
                ByteArrayInputStream sealStream2 = new ByteArrayInputStream(sealBytes);
                r2.addPicture(sealStream2, XWPFDocument.PICTURE_TYPE_PNG, companyName + "_seal.png", emuWidth, emuHeight);
                sealStream2.close();
                if (after != null && !after.isEmpty()) {
                    XWPFRun r3 = paragraph.createRun();
                    r3.setText(after);
                }
                System.out.println("[DocumentService] 占位符 " + placeholder + " 跨多 run，已合并后插入印章: " + companyName);
            }
        } catch (Exception e) {
            System.err.println("[DocumentService] 合同章生成失败 companyName=" + companyName + " placeholder=" + placeholder + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
