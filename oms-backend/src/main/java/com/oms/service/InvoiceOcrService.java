package com.oms.service;

import com.oms.dto.BaiduVatInvoiceResult;
import com.oms.dto.InvoiceOcrValidationResult;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import com.oms.service.ocr.BaiduOcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InvoiceOcrService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceOcrService.class);

    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?:发票号码|票据号码|No\\.?)[^\\n：:]{0,8}[：:]?\\s*([0-9A-Za-z]{6,30})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INVOICE_DATE = Pattern.compile(
            "(?:开票日期|日期)[^\\n：:]{0,8}[：:]?\\s*([0-9]{4}[年\\-/\\.][0-9]{1,2}[月\\-/\\.][0-9]{1,2}日?)");
    /** 电子发票 PDF 常把「购买方」「销售方」分块；220 字窗口易截断，改用分块截取更稳 */
    private static final Pattern SECTION_BUYER_NAME = Pattern.compile(
            "(?s)购买方(?:信息)?[\\s\\S]{0,800}?名称\\s*[：:：]\\s*([^\\n\\r]{4,120})");
    private static final Pattern SECTION_SELLER_NAME = Pattern.compile(
            "(?s)销售方(?:信息)?[\\s\\S]{0,800}?名称\\s*[：:：]\\s*([^\\n\\r]{4,120})");
    /** 税务局全电发票导出：dzfp_发票号码_销方或购方名称_时间戳 */
    private static final Pattern DZFP_FILENAME_TITLE = Pattern.compile(
            "(?i)^dzfp_[0-9]{10,40}_([^_]+)_[0-9]{8,16}(?:\\.[^.]+)?$");
    /** 排除「项目名称」等行尾部的「名称：」误匹配 */
    private static final Pattern LINE_NAME_COLON = Pattern.compile(
            "(?m)(?:^|[\\n\\r])\\s*(?<!项目)(?<!服务)(?:名称|名\\s*称)\\s*[：:：]\\s*([^\\n\\r]{4,120})");
    private static final Pattern BUYER_NAME = Pattern.compile(
            "(?:购买方名称|购方名称|购方|购买方)[^\\n：:]{0,8}[：:]?\\s*([^\\n]{4,120})");
    private static final Pattern SELLER_NAME = Pattern.compile(
            "(?:销售方名称|销方名称|销方|销售方)[^\\n：:]{0,8}[：:]?\\s*([^\\n]{4,120})");

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    private OrderImportService orderImportService;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BaiduOcrService baiduOcrService;

    public InvoiceOcrValidationResult analyzeSalesInvoice(Long orderId, String invoiceUrl) {
        return analyzeSalesInvoice(orderId, invoiceUrl, null);
    }

    /**
     * @param originalUploadFilename 用户上传时的原始文件名（服务端存盘为 UUID，dzfp 抬头需用此字段解析）
     */
    public InvoiceOcrValidationResult analyzeSalesInvoice(Long orderId, String invoiceUrl, String originalUploadFilename) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        InvoiceOcrValidationResult result = new InvoiceOcrValidationResult();
        result.setExpectedBuyerTitle(resolveExpectedBuyerTitle(order));
        result.setExpectedSellerTitle(resolveExpectedSellerTitle(order));

        String normalizedUrl = normalizeBlankToNull(invoiceUrl);
        if (normalizedUrl == null) {
            result.setSuccess(false);
            result.setMessage("缺少发票附件地址");
            return result;
        }

        Path filePath = resolveUploadPath(normalizedUrl);
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            result.setSuccess(false);
            result.setMessage("发票附件不存在，无法识别");
            return result;
        }

        String fileName = filePath.getFileName().toString();
        result.setFileType(resolveFileType(fileName));
        String filenameForDzfpHint = basenameOnly(normalizeBlankToNull(originalUploadFilename));
        if (filenameForDzfpHint == null) {
            filenameForDzfpHint = fileName;
        }

        String text;
        try {
            text = orderImportService.extractTextFromStoredFile(filePath, fileName);
        } catch (IllegalArgumentException e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        } catch (UnsupportedOperationException e) {
            result.setSuccess(false);
            result.setMessage("OCR 未识别到有效文本，请手工核对发票号码与抬头");
            return result;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("发票 OCR 解析失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return result;
        }

        String raw = text == null ? "" : text.trim();
        result.setRawTextLength(raw.replaceAll("\\s+", "").length());
        result.setInvoiceNumber(extractInvoiceNumber(raw));
        result.setInvoiceDate(normalizeInvoiceDate(extractInvoiceDateRaw(raw)));
        result.setBuyerTitle(cleanTitle(extractBuyerTitle(raw)));
        result.setSellerTitle(cleanTitle(extractSellerTitle(raw)));
        mergeBaiduVatStructuredFields(filePath, fileName, result);
        applyDzfpFilenameTitleHint(filenameForDzfpHint, result);
        log.info("发票OCR汇总 orderId={} uploadName='{}' buyer='{}' seller='{}' baiduVatUsed={}",
                orderId,
                dash(filenameForDzfpHint),
                dash(result.getBuyerTitle()),
                dash(result.getSellerTitle()),
                result.getBaiduVatInvoiceUsed());

        List<String> warnings = new ArrayList<>();
        if (normalizeBlankToNull(result.getInvoiceNumber()) == null) {
            warnings.add("未识别到发票号码，请手工核对。");
        }
        if (normalizeBlankToNull(result.getInvoiceDate()) == null) {
            warnings.add("未识别到开票日期，请手工核对。");
        }
        if (normalizeBlankToNull(result.getBuyerTitle()) == null) {
            warnings.add("未识别到购买方抬头，请手工核对。");
        }
        if (normalizeBlankToNull(result.getSellerTitle()) == null) {
            warnings.add("未识别到销售方抬头，请手工核对。");
        }

        boolean buyerMatched = matchCompanyTitle(result.getExpectedBuyerTitle(), result.getBuyerTitle());
        boolean sellerMatched = matchCompanyTitle(result.getExpectedSellerTitle(), result.getSellerTitle());
        result.setBuyerMatched(buyerMatched);
        result.setSellerMatched(sellerMatched);

        if (normalizeBlankToNull(result.getBuyerTitle()) != null && !buyerMatched) {
            warnings.add("OCR 识别购买方抬头与当前订单甲方不一致。系统:「"
                    + dash(result.getExpectedBuyerTitle()) + "」, OCR:「" + dash(result.getBuyerTitle()) + "」");
        }
        if (normalizeBlankToNull(result.getSellerTitle()) != null && !sellerMatched) {
            warnings.add("OCR 识别销售方抬头与当前订单销售主体不一致。系统:「"
                    + dash(result.getExpectedSellerTitle()) + "」, OCR:「" + dash(result.getSellerTitle()) + "」");
        }

        boolean duplicateInvoiceNumber = hasDuplicateInvoiceNumber(order.getId(), result.getInvoiceNumber());
        result.setDuplicateInvoiceNumber(duplicateInvoiceNumber);
        if (duplicateInvoiceNumber) {
            warnings.add("识别出的发票号码已被其他订单使用，请确认是否上传了错误发票。");
        }

        result.getWarnings().clear();
        result.getWarnings().addAll(warnings);
        result.setRequiresConfirmation(
                (normalizeBlankToNull(result.getBuyerTitle()) != null && !buyerMatched)
                        || (normalizeBlankToNull(result.getSellerTitle()) != null && !sellerMatched)
        );
        result.setSuccess(true);
        result.setMessage(warnings.isEmpty() ? "发票识别完成" : "发票识别完成，请核对提示信息");
        return result;
    }

    public void assertInvoiceNumberUnique(Long currentOrderId, String invoiceNumber) {
        String normalized = normalizeBlankToNull(invoiceNumber);
        if (normalized == null) {
            return;
        }
        List<SalesOrder> orders = salesOrderRepository.findByInvoiceNumber(normalized);
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (SalesOrder order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }
            if (!order.getId().equals(currentOrderId)) {
                throw new IllegalArgumentException("发票号码已被订单 " + dash(order.getOmsOrderNo()) + " 使用，请核对后再保存");
            }
        }
    }

    private boolean hasDuplicateInvoiceNumber(Long currentOrderId, String invoiceNumber) {
        String normalized = normalizeBlankToNull(invoiceNumber);
        if (normalized == null) {
            return false;
        }
        List<SalesOrder> orders = salesOrderRepository.findByInvoiceNumber(normalized);
        if (orders == null) {
            return false;
        }
        for (SalesOrder order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }
            if (!order.getId().equals(currentOrderId)) {
                return true;
            }
        }
        return false;
    }

    private Path resolveUploadPath(String invoiceUrl) {
        String normalized = invoiceUrl == null ? "" : invoiceUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                java.net.URL parsed = new java.net.URL(normalized);
                normalized = parsed.getPath();
            } catch (Exception ignored) {
            }
        }
        if (normalized.startsWith("/uploads/")) {
            normalized = normalized.substring("/uploads/".length());
        } else if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        if (normalized.contains("..")) {
            return null;
        }
        return Paths.get(uploadDir).resolve(normalized).normalize();
    }

    private String resolveFileType(String fileName) {
        if (fileName == null) {
            return "";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) {
            return "image";
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "word";
        }
        return "other";
    }

    private String resolveExpectedBuyerTitle(SalesOrder order) {
        if (order == null) {
            return null;
        }
        String a = normalizeBlankToNull(order.getPartyATitle());
        if (a != null) {
            return a;
        }
        return normalizeBlankToNull(order.getPlatformName());
    }

    private String resolveExpectedSellerTitle(SalesOrder order) {
        if (order == null) {
            return null;
        }
        String op = normalizeBlankToNull(order.getOperationEntityTitle());
        if (op != null) {
            return op;
        }
        if (order.getCreatedBy() != null) {
            User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
            if (creator != null) {
                return normalizeBlankToNull(creator.getCompanyTitle());
            }
        }
        return null;
    }

    private String extractInvoiceNumber(String raw) {
        Matcher m = INVOICE_NUMBER.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractInvoiceDateRaw(String raw) {
        Matcher m = INVOICE_DATE.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String normalizeInvoiceDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replace("年", "-").replace("月", "-").replace("日", "").trim();
        s = s.replace('/', '.').replace('．', '.');
        String[] parts = s.split("[\\-\\./]");
        if (parts.length < 3) {
            return null;
        }
        try {
            int y = Integer.parseInt(parts[0].trim());
            int mo = Integer.parseInt(parts[1].trim());
            int d = Integer.parseInt(parts[2].trim());
            return String.format("%04d-%02d-%02d", y, mo, d);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractBuyerTitle(String raw) {
        String t = tryExtractTitleBetweenBlocks(raw, "购买方", "销售方");
        if (t != null) {
            return t;
        }
        Matcher m = SECTION_BUYER_NAME.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = BUYER_NAME.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractSellerTitle(String raw) {
        String t = tryExtractSellerTitleBlock(raw);
        if (t != null) {
            return t;
        }
        Matcher m = SECTION_SELLER_NAME.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        m = SELLER_NAME.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 在「起始标记」与「结束标记」之间取第一处「名称：」行，适配电子发票 PDF 文本顺序。
     */
    private String tryExtractTitleBetweenBlocks(String raw, String startMarker, String endMarker) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int iStart = raw.indexOf(startMarker);
        int iEnd = raw.indexOf(endMarker);
        if (iStart < 0 || iEnd <= iStart) {
            return null;
        }
        String block = raw.substring(iStart, iEnd);
        return extractFirstLineName(block);
    }

    private String tryExtractSellerTitleBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int iSell = raw.indexOf("销售方");
        if (iSell < 0) {
            return null;
        }
        int end = raw.length();
        int searchFrom = iSell + 6;
        for (String mk : new String[] {"价税合计", "合　计", "合计", "备注", "项目名称", "货物或应税劳务、服务名称", "开票人"}) {
            int p = raw.indexOf(mk, searchFrom);
            if (p > iSell && p < end) {
                end = p;
            }
        }
        int cap = Math.min(iSell + 2500, end);
        String block = raw.substring(iSell, cap);
        return extractFirstLineName(block);
    }

    private String extractFirstLineName(String block) {
        if (block == null || block.isBlank()) {
            return null;
        }
        Matcher m = LINE_NAME_COLON.matcher(block);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 百度「增值税发票识别」结构化结果：与本地 PDF 文本 + 正则独立，成功则覆盖对应字段（购/销方、票号、日期）。
     */
    private void mergeBaiduVatStructuredFields(Path filePath, String fileName, InvoiceOcrValidationResult result) {
        if (result == null || filePath == null || baiduOcrService == null || !baiduOcrService.isVatInvoiceEnabled()) {
            return;
        }
        BaiduVatInvoiceResult vat = baiduOcrService.tryRecognizeVatInvoice(filePath, fileName);
        if (vat == null || !vat.hasAnyField()) {
            return;
        }
        result.setBaiduVatInvoiceUsed(true);
        if (normalizeBlankToNull(vat.getInvoiceNum()) != null) {
            result.setInvoiceNumber(vat.getInvoiceNum().trim().replaceAll("\\s+", ""));
        }
        if (normalizeBlankToNull(vat.getInvoiceDateRaw()) != null) {
            String normalized = normalizeInvoiceDate(vat.getInvoiceDateRaw());
            if (normalized != null) {
                result.setInvoiceDate(normalized);
            }
        }
        if (normalizeBlankToNull(vat.getPurchaserName()) != null) {
            result.setBuyerTitle(cleanTitle(vat.getPurchaserName()));
        }
        if (normalizeBlankToNull(vat.getSellerName()) != null) {
            result.setSellerTitle(cleanTitle(vat.getSellerName()));
        }
    }

    /**
     * 全电发票文件名常含一方主体名称；优先在能与订单甲方/销售主体匹配时补全，否则在购销仍全空时带入文件名主体作为疑似购买方。
     */
    private void applyDzfpFilenameTitleHint(String fileName, InvoiceOcrValidationResult result) {
        if (result == null || fileName == null) {
            return;
        }
        String hint = extractTitleFromDzfpFilename(fileName);
        hint = cleanTitle(hint);
        if (hint == null) {
            return;
        }
        String eb = result.getExpectedBuyerTitle();
        String es = result.getExpectedSellerTitle();
        if (normalizeBlankToNull(result.getBuyerTitle()) == null && matchCompanyTitle(eb, hint)) {
            result.setBuyerTitle(hint);
        }
        if (normalizeBlankToNull(result.getSellerTitle()) == null && matchCompanyTitle(es, hint)) {
            result.setSellerTitle(hint);
        }
        boolean buyerEmpty = normalizeBlankToNull(result.getBuyerTitle()) == null;
        boolean sellerEmpty = normalizeBlankToNull(result.getSellerTitle()) == null;
        if (buyerEmpty && sellerEmpty) {
            boolean matchedOrder = matchCompanyTitle(eb, hint) || matchCompanyTitle(es, hint);
            if (!matchedOrder) {
                result.setBuyerTitle(hint);
            }
        }
    }

    private String extractTitleFromDzfpFilename(String fileName) {
        if (fileName == null) {
            return null;
        }
        String trimmed = fileName.trim();
        Matcher m = DZFP_FILENAME_TITLE.matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        return extractDzfpCompanyBySplit(trimmed);
    }

    /**
     * dzfp_发票号码_公司名称_时间戳(.pdf) 分段解析，避免正则与后缀变体不一致时取不到公司名。
     */
    private String extractDzfpCompanyBySplit(String baseName) {
        int slash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (slash >= 0) {
            baseName = baseName.substring(slash + 1);
        }
        String[] segs = baseName.split("_");
        if (segs.length < 4) {
            return null;
        }
        if (!"dzfp".equalsIgnoreCase(segs[0].trim())) {
            return null;
        }
        if (!segs[1].trim().matches("[0-9]{10,40}")) {
            return null;
        }
        String timePart = segs[3].replaceFirst("\\.[^.]+$", "").trim();
        if (!timePart.matches("[0-9]{8,16}")) {
            return null;
        }
        return segs[2].trim();
    }

    private String cleanTitle(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.startsWith("：") || t.startsWith(":")) {
            t = t.substring(1).trim();
        }
        return t.isEmpty() ? null : t;
    }

    private boolean matchCompanyTitle(String expected, String ocr) {
        String e = normalizeBlankToNull(expected);
        String o = normalizeBlankToNull(ocr);
        if (e == null || o == null) {
            return true;
        }
        String ne = normalizeForCompare(e);
        String no = normalizeForCompare(o);
        if (ne.isEmpty() || no.isEmpty()) {
            return true;
        }
        return ne.equals(no) || ne.contains(no) || no.contains(ne);
    }

    private String normalizeForCompare(String s) {
        return s.replaceAll("[\\s　]", "")
                .replace("（", "(")
                .replace("）", ")")
                .toLowerCase();
    }

    private String normalizeBlankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String basenameOnly(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String s = path.trim();
        int p = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return p >= 0 ? s.substring(p + 1) : s;
    }

    private String dash(String s) {
        String t = normalizeBlankToNull(s);
        return t == null ? "-" : t;
    }
}
