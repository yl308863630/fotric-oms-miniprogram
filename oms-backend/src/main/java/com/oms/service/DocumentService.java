package com.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.Contract;
import com.oms.entity.Opportunity;
import com.oms.entity.OpportunityProduct;
import com.oms.entity.User;
import com.oms.repository.OpportunityProductRepository;
import com.oms.repository.UserRepository;
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

    @Autowired
    private OpportunityProductRepository opportunityProductRepository;

    @Autowired
    private UserRepository userRepository;

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

        writePlaceholderReport(contract, targetPath.getParent(), contract.getContractNo());
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
        placeholders.put("${partyABankAccount}", contract.getPartyAAccount() != null ? contract.getPartyAAccount() : "");
        placeholders.put("${partyBBankAccount}", contract.getPartyBAccount() != null ? contract.getPartyBAccount() : "");
        placeholders.put("${partyAPhone}", contract.getPartyAPhone() != null ? contract.getPartyAPhone() : "");
        placeholders.put("${partyBPhone}", contract.getPartyBPhone() != null ? contract.getPartyBPhone() : "");
        placeholders.put("${productModel}", contract.getProductModel() != null ? contract.getProductModel() : "");
        placeholders.put("${productName}", contract.getProductName() != null ? contract.getProductName() : "");
        placeholders.put("${materialNo}", contract.getMaterialNo() != null ? contract.getMaterialNo() : "");
        String productInfo = buildProductInfo(
                contract.getMaterialNo(),
                contract.getProductModel(),
                contract.getProductName()
        );
        placeholders.put("${productInfo}", productInfo);
        placeholders.put("${商品信息}", productInfo);
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
        placeholders.put("${partyATitle}", contract.getPartyAName() != null ? contract.getPartyAName() : "");
        placeholders.put("${partyAOrderNo}", contract.getPartyAOrderNo() != null ? contract.getPartyAOrderNo() : "");
        placeholders.put("${paymentMethod}", contract.getPaymentMethod() != null ? contract.getPaymentMethod() : "");
        placeholders.put("${甲方}", contract.getPartyAName() != null ? contract.getPartyAName() : "");
        placeholders.put("${乙方}", contract.getPartyBName() != null ? contract.getPartyBName() : "");
        placeholders.put("${deliveryAddress}", contract.getDeliveryAddress() != null ? contract.getDeliveryAddress() : "");
        placeholders.put("${deliveryDate}", contract.getDeliveryDate() != null ? contract.getDeliveryDate().toString() : "");
        placeholders.put("${orderDate}", contract.getOrderDate() != null ? contract.getOrderDate().toString() : "");
        String deliveryCycleStr = contract.getDeliveryCycle() != null ? String.valueOf(contract.getDeliveryCycle()) : "";
        placeholders.put("${deliveryCycle}", deliveryCycleStr);
        // 模板中写「${deliveryCycle}天」时整段替换为「4天」，避免「天」丢失
        placeholders.put("${deliveryCycle}天", contract.getDeliveryCycle() != null ? (contract.getDeliveryCycle() + "天") : "天");
        placeholders.put("${partyBRepresentative}", contract.getPartyBRepresentative() != null ? contract.getPartyBRepresentative() : "");
        // 模板中可能使用的拼写变体（签约后交期、产品保修期）
        placeholders.put("${deliy}", deliveryCycleStr);
        placeholders.put("${deliy}天", contract.getDeliveryCycle() != null ? (contract.getDeliveryCycle() + "天") : "天");
        placeholders.put("${warra}", contract.getWarrantyPeriod() != null ? contract.getWarrantyPeriod() : "");
        
        // 兼容模板中 $(xxx) 写法及常见命名变体，避免合同里产品配置、甲方/乙方抬头、代表、签约日期等不替换
        addPlaceholderAliases(placeholders);
        
        System.out.println("=== Placeholders built ===");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            System.out.println(entry.getKey() + " = '" + entry.getValue() + "'");
        }
        
        return placeholders;
    }
    
    /** 为 $(key) 格式及 partyAname/partyBname 等常见变体添加同值映射，保证模板占位符与后端一致可替换 */
    private void addPlaceholderAliases(Map<String, String> placeholders) {
        Map<String, String> aliases = new java.util.LinkedHashMap<>();
        String contractNo = placeholders.get("${contractNo}");
        String signDate = placeholders.get("${signDate}");
        String partyA = placeholders.get("${partyA}");
        String partyB = placeholders.get("${partyB}");
        String partyAName = placeholders.get("${partyAName}");
        String partyBName = placeholders.get("${partyBName}");
        String productName = placeholders.get("${productName}");
        String productModel = placeholders.get("${productModel}");
        String productInfo = placeholders.get("${productInfo}");
        String productConfig = placeholders.get("${productConfig}");
        String quantity = placeholders.get("${quantity}");
        String unitPrice = placeholders.get("${unitPrice}");
        String totalAmount = placeholders.get("${totalAmount}");
        String amountCn = placeholders.get("${amountCn}");
        String salesName = placeholders.get("${salesName}");
        String partyBRep = placeholders.get("${partyBRepresentative}");
        if (contractNo != null) {
            aliases.put("$(contractNo)", contractNo);
        }
        if (signDate != null) {
            aliases.put("$(signDate)", signDate);
        }
        if (partyA != null) {
            aliases.put("$(partyA)", partyA);
        }
        if (partyB != null) {
            aliases.put("$(partyB)", partyB);
        }
        if (partyAName != null) {
            aliases.put("$(partyAname)", partyAName);
            aliases.put("$(partyAName)", partyAName);
        }
        if (partyBName != null) {
            aliases.put("$(partyBname)", partyBName);
            aliases.put("$(partyBName)", partyBName);
        }
        if (productName != null) {
            aliases.put("$(productName)", productName);
        }
        if (productModel != null) {
            aliases.put("$(productModel)", productModel);
        }
        if (productInfo != null) {
            aliases.put("$(productInfo)", productInfo);
            aliases.put("$(商品信息)", productInfo);
        }
        if (productConfig != null) {
            aliases.put("$(productConfig)", productConfig);
        }
        if (quantity != null) {
            aliases.put("$(quantity)", quantity);
        }
        if (unitPrice != null) {
            aliases.put("$(unitPrice)", unitPrice);
        }
        if (totalAmount != null) {
            aliases.put("$(totalAmount)", totalAmount);
        }
        if (amountCn != null) {
            aliases.put("$(amountCn)", amountCn);
            aliases.put("$[amountCn)", amountCn);
        }
        if (salesName != null) {
            aliases.put("$(salesName)", salesName);
        }
        if (partyBRep != null) {
            aliases.put("$(partyBRepresentative)", partyBRep);
        }
        String deliy = placeholders.get("${deliy}");
        String warra = placeholders.get("${warra}");
        String deliveryCycleTian = placeholders.get("${deliveryCycle}天");
        if (deliy != null) { aliases.put("$(deliy)", deliy); }
        if (warra != null) { aliases.put("$(warra)", warra); }
        if (deliveryCycleTian != null) { aliases.put("$(deliveryCycle)天", deliveryCycleTian); aliases.put("$(deliy)天", deliveryCycleTian); }
        String partyAAddress = placeholders.get("${partyAAddress}");
        String partyBAddress = placeholders.get("${partyBAddress}");
        String partyABankVal = placeholders.get("${partyABank}");
        String partyBBankVal = placeholders.get("${partyBBank}");
        String partyAAccount = placeholders.get("${partyAAccount}");
        String partyBAccount = placeholders.get("${partyBAccount}");
        String partyATaxNo = placeholders.get("${partyATaxNo}");
        String partyBTaxNo = placeholders.get("${partyBTaxNo}");
        String partyAPhone = placeholders.get("${partyAPhone}");
        String partyBPhone = placeholders.get("${partyBPhone}");
        if (partyAAddress != null) { aliases.put("$(partyAaddress)", partyAAddress); }
        if (partyBAddress != null) { aliases.put("$(partyBaddress)", partyBAddress); }
        if (partyABankVal != null) { aliases.put("$(partyAbank)", partyABankVal); }
        if (partyBBankVal != null) { aliases.put("$(partyBbank)", partyBBankVal); }
        if (partyAAccount != null) {
            aliases.put("$(partyAaccount)", partyAAccount);
            aliases.put("$(partyABankAccount)", partyAAccount);
            aliases.put("$(partyAbankAccount)", partyAAccount);
        }
        if (partyBAccount != null) {
            aliases.put("$(partyBaccount)", partyBAccount);
            aliases.put("$(partyBBankAccount)", partyBAccount);
            aliases.put("$(partyBbankAccount)", partyBAccount);
        }
        if (partyATaxNo != null) { aliases.put("$(partyAtax)", partyATaxNo); aliases.put("$(partyAtaxNo)", partyATaxNo); }
        if (partyBTaxNo != null) { aliases.put("$(partyBtax)", partyBTaxNo); aliases.put("$(partyBtaxNo)", partyBTaxNo); }
        if (partyAPhone != null) { aliases.put("$(partyAphone)", partyAPhone); }
        if (partyBPhone != null) { aliases.put("$(partyBphone)", partyBPhone); }
        String amount = placeholders.get("${amount}");
        if (amount != null) { aliases.put("$(amountCo)", amount); aliases.put("$(amount)", amount); }
        placeholders.putAll(aliases);
    }

    /** 占位符跟踪表：供核查「生成合同时各占位符对应的值与代码字段」 */
    public List<Map<String, String>> buildPlaceholderReport(Contract contract) {
        Map<String, String> placeholders = buildPlaceholders(contract);
        List<Map<String, String>> rows = new ArrayList<>();
        String[][] canonical = {
            {"${contractNo}", "contract.contractNo", "合同编号"},
            {"${partyA}", "contract.partyA", "甲方"},
            {"${partyAName}", "contract.partyAName", "甲方抬头/名称"},
            {"${partyB}", "contract.partyB", "乙方"},
            {"${partyBName}", "contract.partyBName", "乙方抬头/名称"},
            {"${partyAAddress}", "contract.partyAAddress", "甲方地址"},
            {"${partyBAddress}", "contract.partyBAddress", "乙方地址"},
            {"${partyABank}", "contract.partyABank", "甲方开户行"},
            {"${partyBBank}", "contract.partyBBank", "乙方开户行"},
            {"${partyAAccount}", "contract.partyAAccount", "甲方账号"},
            {"${partyBAccount}", "contract.partyBAccount", "乙方账号"},
            {"${partyATaxNo}", "contract.partyATaxNo", "甲方税号"},
            {"${partyBTaxNo}", "contract.partyBTaxNo", "乙方税号"},
            {"${partyAPhone}", "contract.partyAPhone", "甲方电话"},
            {"${partyBPhone}", "contract.partyBPhone", "乙方电话"},
            {"${productName}", "contract.productName", "产品名称"},
            {"${productModel}", "contract.productModel", "型号"},
            {"${productInfo}", "materialNo+productModel+productName", "商品信息(汇总)"},
            {"${productConfig}", "contract.productConfig", "产品配置"},
            {"${quantity}", "contract.quantity", "数量"},
            {"${unitPrice}", "contract.unitPrice", "含税单价"},
            {"${totalAmount}", "contract.totalAmount", "含税金额"},
            {"${deliveryCycle}", "contract.deliveryCycle", "签约后交期(天)"},
            {"${deliveryCycle}天", "contract.deliveryCycle + \"天\"", "签约后交期(带单位)"},
            {"${warrantyPeriod}", "contract.warrantyPeriod", "产品保修期"},
            {"${signDate}", "contract.signDate", "签约日期"},
            {"${salesName}", "contract.salesName", "甲方代表"},
            {"${partyBRepresentative}", "contract.partyBRepresentative", "乙方代表"},
            {"${platformName}", "contract.platformName", "平台/结算方抬头(与${settlementParty}同值)"},
            {"${settlementParty}", "contract.settlementPartyName", "平台/结算方抬头(与${platformName}同值)"},
            {"${partyAOrderNo}", "contract.partyAOrderNo", "甲方订单号"},
            {"${paymentMethod}", "contract.paymentMethod", "支付方式"},
            {"${deliveryAddress}", "contract.deliveryAddress", "交货地址"},
            {"${amountCn}", "contract.amountCn", "金额大写"},
        };
        for (String[] triple : canonical) {
            String val = placeholders.get(triple[0]);
            if (val == null) val = "";
            Map<String, String> row = new LinkedHashMap<>();
            row.put("占位符", triple[0]);
            row.put("生成值", val);
            row.put("代码字段", triple[1]);
            row.put("说明", triple[2]);
            rows.add(row);
        }
        return rows;
    }

    private void writePlaceholderReport(Contract contract, Path dir, String contractNo) {
        if (contractNo == null || contractNo.isBlank() || dir == null) return;
        try {
            List<Map<String, String>> report = buildPlaceholderReport(contract);
            String safeName = contractNo.replaceAll("[\\\\/:*?\"<>|]", "_") + "_placeholders.json";
            Path reportPath = dir.resolve(safeName);
            if (objectMapper != null) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
                System.out.println("[合同占位符跟踪表] 已写入: " + reportPath.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("[合同占位符跟踪表] 写入失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
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

    /**
     * 先在各 run 内做占位符替换；若段落仍含占位符（Word 常将 ${xxx} 拆成多 run），
     * 再对连续纯文本 run 合并后替换，避免跨 run 占位符不生效；含图片的 run 不合并以保留印章等。
     */
    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || placeholders == null || placeholders.isEmpty()) {
            return;
        }
        // 1) 单 run 内替换
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text == null || text.isEmpty()) continue;
            boolean changed = false;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (text.contains(entry.getKey())) {
                    text = text.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                    changed = true;
                }
            }
            if (changed) run.setText(text, 0);
        }
        // 2) 若整段仍含占位符，说明被拆到多 run，对连续纯文本 run 合并后替换
        String fullText = paragraph.getText();
        if (fullText == null) return;
        boolean stillHasPlaceholder = false;
        for (String key : placeholders.keySet()) {
            if (fullText.contains(key)) { stillHasPlaceholder = true; break; }
        }
        if (!stillHasPlaceholder) return;

        int i = 0;
        while (i < runs.size()) {
            XWPFRun run = runs.get(i);
            if (runHasPicture(run)) { i++; continue; }
            int j = i;
            StringBuilder merged = new StringBuilder();
            while (j < runs.size() && !runHasPicture(runs.get(j))) {
                String t = runs.get(j).getText(0);
                if (t != null) merged.append(t);
                j++;
            }
            String mergedStr = merged.toString();
            String replaced = mergedStr;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                replaced = replaced.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
            if (!replaced.equals(mergedStr)) {
                runs.get(i).setText(replaced, 0);
                for (int k = j - 1; k >= i + 1; k--) {
                    paragraph.removeRun(k);
                }
                runs = paragraph.getRuns();
                i++;
            } else {
                i = j;
            }
        }
    }

    private boolean runHasPicture(XWPFRun run) {
        if (run == null) return false;
        try {
            return run.getEmbeddedPictures() != null && !run.getEmbeddedPictures().isEmpty();
        } catch (Exception e) {
            return false;
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

        writePlaceholderReport(contract, targetPath.getParent(), contract.getContractNo());
        return "/uploads/" + dateStr + "/" + fileName;
    }
    
    private void processProductTable(XWPFDocument document, List<Map<String, Object>> products, Map<String, String> basePlaceholders) {
        for (XWPFTable table : document.getTables()) {
            List<Integer> placeholderRowIndexes = new ArrayList<>();
            for (int i = 0; i < table.getRows().size(); i++) {
                XWPFTableRow row = table.getRow(i);
                if (rowContainsProductPlaceholder(row)) {
                    placeholderRowIndexes.add(i);
                }
            }
            if (placeholderRowIndexes.isEmpty()) continue;

            int templateRowIndex = placeholderRowIndexes.get(0);
            XWPFTableRow templateRow = table.getRow(templateRowIndex);

            // 模板里若有多条占位符行，只保留第一条作为模板，其他先删除，避免残留占位符行。
            for (int k = placeholderRowIndexes.size() - 1; k >= 1; k--) {
                table.removeRow(placeholderRowIndexes.get(k));
            }

            if (products == null || products.isEmpty()) {
                // 无产品时移除模板占位符行，避免导出占位符文本
                table.removeRow(templateRowIndex);
                continue;
            }

            // 复制模板行到与产品数量一致（第一行用模板本身，剩余行克隆）
            for (int i = 1; i < products.size(); i++) {
                copyTableRow(table, templateRow, templateRowIndex + i);
            }

            for (int i = 0; i < products.size(); i++) {
                XWPFTableRow targetRow = table.getRow(templateRowIndex + i);
                Map<String, Object> product = products.get(i);

                Map<String, String> productPlaceholders = new HashMap<>(basePlaceholders);
                String pName = getValue(product, "productName", getValue(product, "platformSku", ""));
                String pConfig = getValue(product, "productConfig", "");
                String dCycle = getValue(product, "deliveryCycle", "");
                String deliveryPeriod = getValue(product, "deliveryPeriod", dCycle);
                productPlaceholders.put("${productName}", pName);
                productPlaceholders.put("${productModel}", getValue(product, "model", ""));
                productPlaceholders.put("${productConfig}", pConfig);
                productPlaceholders.put("${quantity}", getValue(product, "quantity", "0"));
                productPlaceholders.put("${unitPrice}", getValue(product, "taxIncludedPrice", "0"));
                productPlaceholders.put("${totalAmount}", getValue(product, "taxIncludedTotal", "0"));
                productPlaceholders.put("${warrantyPeriod}", getValue(product, "warrantyPeriod", ""));
                productPlaceholders.put("${deliveryPeriod}", deliveryPeriod);
                productPlaceholders.put("${deliveryPeriod}天", (deliveryPeriod != null && !deliveryPeriod.isEmpty()) ? (deliveryPeriod + "天") : "天");
                productPlaceholders.put("${deliveryCycle}", dCycle);
                productPlaceholders.put("${deliveryCycle}天", (dCycle != null && !dCycle.isEmpty()) ? (dCycle + "天") : "天");
                productPlaceholders.put("${deliy}天", productPlaceholders.get("${deliveryCycle}天"));
                replaceRowText(targetRow, productPlaceholders);
                // 无论占位符是否全部替换成功，都按列强制覆盖一次，
                // 防止模板示例值（如“50%”）残留在产品行里。
                overwriteProductRowByColumns(targetRow, productPlaceholders);
            }
        }
    }

    private boolean rowContainsProductPlaceholder(XWPFTableRow row) {
        String rowText = getRowText(row);
        String normalized = rowText == null ? "" : rowText.toLowerCase().replaceAll("\\s+", "");
        String xml;
        try {
            xml = row.getCtRow() != null ? row.getCtRow().xmlText() : "";
        } catch (Exception e) {
            xml = "";
        }
        String normalizedXml = xml == null ? "" : xml.toLowerCase().replaceAll("\\s+", "");
        String merged = normalized + normalizedXml;
        return merged.contains("${productname}")
                || merged.contains("${productmodel}")
                || merged.contains("${productconfig}")
                || merged.contains("${quantity}")
                || merged.contains("${unitprice}")
                || merged.contains("${totalamount}")
                || (merged.contains("${product") && merged.contains("name}"))
                || (merged.contains("${product") && merged.contains("model}"))
                || (merged.contains("${product") && merged.contains("config}"));
    }

    private void overwriteProductRowByColumns(XWPFTableRow row, Map<String, String> productPlaceholders) {
        if (row == null || row.getTableCells() == null || row.getTableCells().isEmpty()) return;
        List<String> values = new ArrayList<>();
        values.add(productPlaceholders.getOrDefault("${productName}", ""));
        values.add(productPlaceholders.getOrDefault("${productModel}", ""));
        values.add(productPlaceholders.getOrDefault("${productConfig}", ""));
        values.add(productPlaceholders.getOrDefault("${quantity}", "0"));
        values.add(productPlaceholders.getOrDefault("${unitPrice}", "0"));
        values.add(productPlaceholders.getOrDefault("${totalAmount}", "0"));
        values.add(productPlaceholders.getOrDefault("${deliveryPeriod}", ""));
        values.add(productPlaceholders.getOrDefault("${warrantyPeriod}", ""));

        int count = Math.min(values.size(), row.getTableCells().size());
        for (int i = 0; i < count; i++) {
            setCellPlainText(row.getCell(i), values.get(i));
        }
    }

    private void setCellPlainText(XWPFTableCell cell, String text) {
        if (cell == null) return;
        String safeText = text == null ? "" : text;
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        XWPFParagraph paragraph;
        if (paragraphs == null || paragraphs.isEmpty()) {
            paragraph = cell.addParagraph();
        } else {
            paragraph = paragraphs.get(0);
            // 保留第一个段落样式，移除多余段落，避免残留占位符文本
            for (int i = paragraphs.size() - 1; i >= 1; i--) {
                cell.removeParagraph(i);
            }
        }

        CTRPr styleRef = null;
        if (paragraph.getRuns() != null && !paragraph.getRuns().isEmpty()) {
            XWPFRun firstRun = paragraph.getRuns().get(0);
            if (firstRun.getCTR() != null && firstRun.getCTR().getRPr() != null) {
                styleRef = (CTRPr) firstRun.getCTR().getRPr().copy();
            }
        }

        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.createRun();
        if (styleRef != null) {
            run.getCTR().setRPr(styleRef);
        }
        run.setText(safeText, 0);
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

    private String buildProductInfo(String materialNo, String model, String productName) {
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        if (materialNo != null && !materialNo.isBlank()) lines.add(materialNo.trim());
        if (model != null && !model.isBlank()) lines.add(model.trim());
        if (productName != null && !productName.isBlank()) lines.add(productName.trim());
        return String.join("\n", lines);
    }
    
    private String getValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * 构建报价单占位符：客户、收件人、日期、业务员、付款条件、质保期等。
     */
    public Map<String, String> buildQuotationPlaceholders(Opportunity opp, List<OpportunityProduct> products, User salesUser) {
        Map<String, String> m = new HashMap<>();
        String cust = opp.getCustomerName() != null ? opp.getCustomerName() : "";
        m.put("${customerName}", cust);
        m.put("${contact}", opp.getContact() != null ? opp.getContact() : "");
        m.put("${phone}", opp.getPhone() != null ? opp.getPhone() : "");
        m.put("${date}", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")));
        String salesName = opp.getEcommerceSales() != null ? opp.getEcommerceSales() : "";
        m.put("${salesName}", salesName);
        String salesPhone = "";
        String salesEmail = "";
        if (salesUser != null) {
            salesPhone = salesUser.getPhone() != null ? salesUser.getPhone() : "";
            salesEmail = ""; // User 实体暂无 email，后续可扩展
        }
        m.put("${salesPhone}", salesPhone);
        m.put("${salesEmail}", salesEmail);
        String paymentTerms = opp.getDeliveryPeriod() != null && !opp.getDeliveryPeriod().isBlank()
                ? opp.getDeliveryPeriod()
                : "票到60天";
        m.put("${paymentTerms}", paymentTerms);
        m.put("${warrantyPeriod}", "1年");
        String deliveryPeriod = "4-6周";
        m.put("${deliveryPeriod}", deliveryPeriod);
        m.put("${deliveryPeriod}天", deliveryPeriod + "天");
        return m;
    }

    /**
     * 根据商机生成报价单 Word 文档，填充占位符并处理多产品表格。
     */
    public String generateQuotationDocument(Opportunity opportunity, String templateUrl) throws Exception {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path targetDir = Paths.get(uploadDir, dateStr);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        String fileName = "quotation_" + opportunity.getId() + "_" + System.currentTimeMillis() + ".docx";
        Path targetPath = targetDir.resolve(fileName);

        String actualTemplateUrl = normalizeTemplateUrl(templateUrl);
        if (actualTemplateUrl == null || actualTemplateUrl.isBlank()) {
            throw new IOException("未指定报价单模板。");
        }
        Path resolvedPath = null;
        InputStream templateStream;
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
            String pathHint = resolvedPath != null ? (" 路径: " + resolvedPath.toAbsolutePath()) : "";
            throw new IOException("报价单模板不是有效的 .docx 文件。" + pathHint + " " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
        templateStream.close();

        List<OpportunityProduct> products = opportunity.getId() != null
                ? opportunityProductRepository.findByOpportunityId(opportunity.getId())
                : Collections.emptyList();
        User salesUser = null;
        if (opportunity.getEcommerceSales() != null && !opportunity.getEcommerceSales().isBlank()) {
            List<User> users = userRepository.findByRealName(opportunity.getEcommerceSales().trim());
            if (users != null && !users.isEmpty()) {
                salesUser = users.get(0);
            }
        }

        Map<String, String> placeholders = buildQuotationPlaceholders(opportunity, products, salesUser);
        List<Map<String, Object>> productMaps = new ArrayList<>();
        for (OpportunityProduct op : products) {
            Map<String, Object> row = new HashMap<>();
            int quantity = op.getQuantity() != null ? op.getQuantity() : 0;
            BigDecimal total = op.getTotalPrice() != null ? op.getTotalPrice() : BigDecimal.ZERO;
            BigDecimal unit = op.getSellingPrice() != null ? op.getSellingPrice() : BigDecimal.ZERO;
            if (quantity > 0 && op.getTotalPrice() != null) {
                unit = total.divide(BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP);
            } else if (quantity > 0 && op.getSellingPrice() != null) {
                total = op.getSellingPrice().multiply(BigDecimal.valueOf(quantity));
            }
            row.put("platformSku", op.getProductName() != null ? op.getProductName() : "");
            row.put("model", op.getProductModel() != null ? op.getProductModel() : "");
            row.put("productConfig",
                    op.getProductConfig() != null && !op.getProductConfig().isBlank()
                            ? op.getProductConfig()
                            : (op.getRemarks() != null && !op.getRemarks().isBlank()
                                    ? op.getRemarks()
                                    : (op.getProductModel() != null ? op.getProductModel() : "")));
            row.put("quantity", quantity);
            row.put("taxIncludedPrice", unit.toPlainString());
            row.put("taxIncludedTotal", total.toPlainString());
            row.put("warrantyPeriod", op.getWarrantyPeriod() != null && !op.getWarrantyPeriod().isBlank()
                    ? op.getWarrantyPeriod()
                    : "1年");
            row.put("deliveryPeriod", op.getDeliveryPeriod() != null && !op.getDeliveryPeriod().isBlank()
                    ? op.getDeliveryPeriod()
                    : "4-6周");
            productMaps.add(row);
        }
        System.out.println("[Quotation] opportunityId=" + opportunity.getId() + ", productCount=" + productMaps.size());
        for (int i = 0; i < productMaps.size(); i++) {
            Map<String, Object> r = productMaps.get(i);
            System.out.println("[Quotation] row#" + (i + 1)
                    + " name=" + String.valueOf(r.getOrDefault("platformSku", ""))
                    + ", model=" + String.valueOf(r.getOrDefault("model", ""))
                    + ", qty=" + String.valueOf(r.getOrDefault("quantity", "0"))
                    + ", unit=" + String.valueOf(r.getOrDefault("taxIncludedPrice", "0"))
                    + ", total=" + String.valueOf(r.getOrDefault("taxIncludedTotal", "0")));
        }
        if (!productMaps.isEmpty()) {
            processProductTable(document, productMaps, placeholders);
        }
        replacePlaceholdersWithPartySeal(document, placeholders, null);

        FileOutputStream out = new FileOutputStream(targetPath.toFile());
        document.write(out);
        out.close();
        document.close();
        return "/uploads/" + dateStr + "/" + fileName;
    }

    private void insertSealsInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        insertSealsInParagraph(paragraph, placeholders, null);
    }
    
    private void insertSealsInParagraph(XWPFParagraph paragraph, Map<String, String> placeholders, String partyType) {
        String paragraphText = paragraph.getText();
        if (paragraphText == null) return;

        if (partyType == null || "A".equals(partyType)) {
            if (paragraphText.contains("${partyASeal}")) {
                String companyName = getPlaceholderValue(placeholders, "${partyAName}", "${partyA}");
                if (companyName != null && !companyName.isEmpty()) {
                    replacePlaceholderWithSeal(paragraph, "${partyASeal}", companyName);
                    System.out.println("[DocumentService] 已插入甲方章占位符: ${partyASeal} -> " + companyName);
                }
                // 未签署时保留 ${partyASeal} 原文，不删除
            }
        }

        if (partyType == null || "B".equals(partyType)) {
            if (paragraphText.contains("${partyBSeal}")) {
                String companyName = getPlaceholderValue(placeholders, "${partyBName}", "${partyB}");
                if (companyName != null && !companyName.isEmpty()) {
                    replacePlaceholderWithSeal(paragraph, "${partyBSeal}", companyName);
                    System.out.println("[DocumentService] 已插入乙方章占位符: ${partyBSeal} -> " + companyName);
                }
                // 未签署时保留 ${partyBSeal} 原文，不删除
            }
        }
        // 报价单等模板中可按需插入飞础科智慧科技（上海）有限公司电子章
        if (paragraphText.contains("${fotricSeal}")) {
            replacePlaceholderWithSeal(paragraph, "${fotricSeal}", "飞础科智慧科技（上海）有限公司");
        }
    }

    /** 优先取 primary 键值，为空时取 fallback 键值（用于甲方/乙方名称，章用公司名）。 */
    private String getPlaceholderValue(Map<String, String> placeholders, String primaryKey, String fallbackKey) {
        String v = placeholders != null ? placeholders.get(primaryKey) : null;
        if (v != null && !v.isEmpty()) return v;
        v = placeholders != null ? placeholders.get(fallbackKey) : null;
        return (v != null && !v.isEmpty()) ? v : "";
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
