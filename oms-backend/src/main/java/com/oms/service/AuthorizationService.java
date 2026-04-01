package com.oms.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfGState;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.BaseFont;
import com.oms.dto.AuthorizationImportResult;
import com.oms.dto.AuthorizationVerifyResult;
import com.oms.entity.AuthorizationRecord;
import com.oms.repository.AuthorizationRecordRepository;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import fr.opensagres.xdocreport.itext.extension.font.IFontProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AuthorizationService {
    private final AuthorizationRecordRepository recordRepository;
    private final AuthorizationVerifyService verifyService;
    private final AuthorizationScanLogService scanLogService;
    private final com.oms.repository.UserRepository userRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${authorization.verify.public-base-url:http://localhost:8080/authorization/verify}")
    private String verifyPublicBaseUrl;

    /** lowagie(iText2) 用于 docx→pdf，与 itextpdf 5.x 的 BaseFont 不同实例，需单独缓存 */
    private volatile com.lowagie.text.pdf.BaseFont lowagieCjkBaseFont;

    /** 从 classpath 释放到临时文件的思源黑体，解决 Linux ECS 无中文字体时 docx→pdf / 水印报错 */
    private volatile Path bundledCjkFontPath;

    /** 与 {@link #resolveWatermarkBaseFont()} 一致：优先系统已安装的中文字体文件 */
    private static final List<String> CJK_FONT_FILE_CANDIDATES = List.of(
            "/usr/share/fonts/sourcehansans/SOURCEHANSANSCN.TTF",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "C:\\Windows\\Fonts\\msyh.ttc,0",
            "C:\\Windows\\Fonts\\msyh.ttf",
            "C:\\Windows\\Fonts\\simhei.ttf",
            "C:\\Windows\\Fonts\\simsun.ttc,0"
    );

    public AuthorizationService(AuthorizationRecordRepository recordRepository,
                                AuthorizationVerifyService verifyService,
                                AuthorizationScanLogService scanLogService,
                                com.oms.repository.UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.verifyService = verifyService;
        this.scanLogService = scanLogService;
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthorizationImportResult importFromXlsx(MultipartFile file) {
        return importFromXlsx(file, null);
    }

    @Transactional
    public AuthorizationImportResult importFromXlsx(MultipartFile file, Map<String, String> headerMapping) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx/.xls 文件");
        }

        AuthorizationImportResult result = new AuthorizationImportResult();
        result.setSuccess(true);
        result.setMessage("导入成功");
        int total = 0;
        int imported = 0;
        int createdCount = 0;
        int updatedCount = 0;
        List<AuthorizationRecord> created = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            SheetHeaderSelection selected = selectBestImportSheet(wb, headerMapping);
            if (selected == null || selected.sheet == null || selected.headerMap == null) {
                throw new IllegalArgumentException("未找到可识别的表头，请在“列映射”中配置对应列名");
            }
            Sheet sheet = selected.sheet;
            Map<String, Integer> headerMap = selected.headerMap;

            for (int r = selected.headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String code = getCellByMappingOrAliases(row, headerMap, headerMapping, "authorizationCode", "授权编码", "授权编号", "编码", "authorizationCode");
                String platform = getCellByMappingOrAliases(row, headerMap, headerMapping, "platformName", "平台", "平台名称", "电商平台", "platformName");
                String grantor = getCellByMappingOrAliases(row, headerMap, headerMapping, "grantorName", "授权方", "甲方", "授权公司", "grantorName");
                String grantee = getCellByMappingOrAliases(row, headerMap, headerMapping, "granteeName", "被授权方", "乙方", "被授权公司", "granteeName");
                String subject = getCellByMappingOrAliases(row, headerMap, headerMapping, "authorizedSubject",
                        "授权主体", "主体", "店铺主体", "平台店铺主体", "店铺名称", "授权经销商店名", "店铺名", "authorizedSubject");
                String productModel = getCellByMappingOrAliases(row, headerMap, headerMapping, "productModel", "具体产品型号", "产品型号", "型号", "productModel");
                String projectName = getCellByMappingOrAliases(row, headerMap, headerMapping, "projectName", "具体项目", "授权项目", "项目", "项目名称", "projectName");
                String validFromStr = getCellByMappingOrAliases(row, headerMap, headerMapping, "validFrom",
                        "生效日期", "生效时间", "授权开始", "开始日期", "有效期开始", "有效期起", "validFrom");
                String validToStr = getCellByMappingOrAliases(row, headerMap, headerMapping, "validTo",
                        "失效日期", "失效时间", "授权结束", "结束日期", "有效期结束", "有效期止", "validTo");
                String templateUrl = getCellByMappingOrAliases(row, headerMap, headerMapping, "templateUrl", "模板", "模板地址", "模板URL", "templateUrl");
                String remark = getCellByMappingOrAliases(row, headerMap, headerMapping, "remark", "备注", "说明", "remark");

                if (isBlank(code) && isBlank(platform) && isBlank(grantee) && isBlank(subject) && isBlank(productModel) && isBlank(projectName)) {
                    continue;
                }
                total++;
                String normalizedCode = !isBlank(code) ? code.trim() : generateAuthorizationCode(platform);
                Optional<AuthorizationRecord> existingOpt = recordRepository.findByAuthorizationCode(normalizedCode);
                AuthorizationRecord record;
                if (existingOpt.isPresent()) {
                    record = existingOpt.get();
                    updatedCount++;
                } else {
                    record = new AuthorizationRecord();
                    record.setAuthorizationCode(normalizedCode);
                    fillCreator(record);
                    createdCount++;
                }
                record.setPlatformName(trimToLength(platform, 255));
                record.setGrantorName(trimToLength(grantor, 255));
                record.setGranteeName(trimToLength(grantee, 255));
                record.setAuthorizedSubject(trimToLength(subject, 255));
                record.setProductModel(trimToLength(productModel, 255));
                record.setProjectName(trimToLength(projectName, 255));
                record.setTemplateUrl(trimOrNull(templateUrl));
                record.setRemark(trimOrNull(remark));
                LocalDate validFrom = parseDateLoose(validFromStr);
                LocalDate validTo = parseDateLoose(validToStr);
                // 若某列给的是“起止区间”文本（如 2025-01-01~2025-12-31），自动拆分补齐。
                if (validFrom == null && !isBlank(validToStr)) {
                    List<LocalDate> dates = parseDateRangeLoose(validToStr);
                    if (!dates.isEmpty()) validFrom = dates.get(0);
                    if (dates.size() > 1 && validTo == null) validTo = dates.get(1);
                }
                if (validTo == null && !isBlank(validFromStr)) {
                    List<LocalDate> dates = parseDateRangeLoose(validFromStr);
                    if (!dates.isEmpty() && validFrom == null) validFrom = dates.get(0);
                    if (dates.size() > 1) validTo = dates.get(1);
                }
                record.setValidFrom(validFrom);
                record.setValidTo(validTo);
                record = recordRepository.save(record);
                created.add(record);
                imported++;
            }
        } catch (Exception e) {
            throw new RuntimeException("解析授权统计表失败: " + e.getMessage(), e);
        }

        result.setTotalRows(total);
        result.setImportedRows(imported);
        result.setRecords(created);
        result.setMessage(String.format("导入完成：新增 %d，更新 %d，总计 %d", createdCount, updatedCount, imported));
        return result;
    }

    private SheetHeaderSelection selectBestImportSheet(Workbook wb, Map<String, String> headerMapping) {
        SheetHeaderSelection best = null;
        int bestScore = -1;
        for (int si = 0; si < wb.getNumberOfSheets(); si++) {
            Sheet sheet = wb.getSheetAt(si);
            if (sheet == null || sheet.getLastRowNum() < sheet.getFirstRowNum()) continue;
            int maxHeaderScan = Math.min(sheet.getLastRowNum(), 30);
            for (int r = sheet.getFirstRowNum(); r <= maxHeaderScan; r++) {
                Row candidate = sheet.getRow(r);
                if (candidate == null) continue;
                Map<String, Integer> headerMap = buildHeaderMap(candidate);
                if (headerMap.isEmpty()) continue;
                int score = scoreHeaderMap(headerMap, headerMapping);
                if (score > bestScore) {
                    bestScore = score;
                    best = new SheetHeaderSelection(sheet, r, headerMap);
                }
            }
        }
        // 至少命中 2 个核心字段才认为是有效表头，避免误识别。
        return bestScore >= 2 ? best : null;
    }

    private int scoreHeaderMap(Map<String, Integer> headerMap, Map<String, String> mapping) {
        int score = 0;
        if (resolveHeaderIndex(headerMap, mapping, "authorizationCode", "授权编码", "授权编号", "编码", "authorizationCode") != null) score++;
        if (resolveHeaderIndex(headerMap, mapping, "platformName", "平台", "平台名称", "电商平台", "platformName") != null) score++;
        if (resolveHeaderIndex(headerMap, mapping, "granteeName", "被授权方", "乙方", "被授权公司", "granteeName") != null) score++;
        if (resolveHeaderIndex(headerMap, mapping, "authorizedSubject", "授权主体", "主体", "店铺主体", "平台店铺主体", "店铺名称", "授权经销商店名", "店铺名", "authorizedSubject") != null) score++;
        if (resolveHeaderIndex(headerMap, mapping, "validFrom", "生效日期", "生效时间", "授权开始", "开始日期", "有效期开始", "有效期起", "validFrom") != null) score++;
        if (resolveHeaderIndex(headerMap, mapping, "validTo", "失效日期", "失效时间", "授权结束", "结束日期", "有效期结束", "有效期止", "validTo") != null) score++;
        return score;
    }

    private Integer resolveHeaderIndex(Map<String, Integer> headerMap, Map<String, String> mapping, String fieldKey, String... aliases) {
        if (mapping != null && fieldKey != null) {
            String mappedHeader = mapping.get(fieldKey);
            if (!isBlank(mappedHeader)) {
                Integer idx = headerMap.get(mappedHeader.trim());
                if (idx != null) return idx;
            }
        }
        for (String alias : aliases) {
            Integer idx = headerMap.get(alias);
            if (idx != null) return idx;
        }
        return null;
    }

    public Page<AuthorizationRecord> search(String keyword, String status, Pageable pageable) {
        Specification<AuthorizationRecord> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!isBlank(keyword)) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("authorizationCode"), kw),
                        cb.like(root.get("platformName"), kw),
                        cb.like(root.get("grantorName"), kw),
                        cb.like(root.get("granteeName"), kw),
                        cb.like(root.get("authorizedSubject"), kw),
                        cb.like(root.get("productModel"), kw),
                        cb.like(root.get("projectName"), kw)
                ));
            }
            if (!isBlank(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            query.orderBy(cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return recordRepository.findAll(spec, pageable);
    }

    public AuthorizationRecord get(Long id) {
        return recordRepository.findById(id).orElseThrow(() -> new RuntimeException("授权记录不存在"));
    }

    @Transactional
    public AuthorizationRecord save(AuthorizationRecord input) {
        if (input == null) throw new IllegalArgumentException("请求体不能为空");
        if (isBlank(input.getAuthorizationCode())) throw new IllegalArgumentException("授权编码不能为空");
        if (input.getId() == null) {
            fillCreator(input);
            if (isBlank(input.getStatus())) input.setStatus("草稿");
            return recordRepository.save(input);
        }
        AuthorizationRecord old = get(input.getId());
        old.setAuthorizationCode(input.getAuthorizationCode());
        old.setPlatformName(trimToLength(input.getPlatformName(), 255));
        old.setGrantorName(trimToLength(input.getGrantorName(), 255));
        old.setGranteeName(trimToLength(input.getGranteeName(), 255));
        old.setAuthorizedSubject(trimToLength(input.getAuthorizedSubject(), 255));
        old.setProductModel(trimToLength(input.getProductModel(), 255));
        old.setProjectName(trimToLength(input.getProjectName(), 255));
        old.setValidFrom(input.getValidFrom());
        old.setValidTo(input.getValidTo());
        old.setTemplateUrl(input.getTemplateUrl());
        old.setRemark(input.getRemark());
        if (!isBlank(input.getStatus())) old.setStatus(input.getStatus());
        return recordRepository.save(old);
    }

    @Transactional
    public AuthorizationRecord generateDocument(Long id, String watermarkCode, String templateUrlOverride) {
        AuthorizationRecord record = get(id);
        String templateUrl = !isBlank(templateUrlOverride) ? templateUrlOverride : record.getTemplateUrl();
        if (isBlank(templateUrl)) {
            throw new IllegalArgumentException("请先维护模板地址 templateUrl");
        }
        try {
            String dateFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path outDir = Paths.get(uploadDir, dateFolder);
            if (!Files.exists(outDir)) Files.createDirectories(outDir);
            String baseName = "AUTH_" + safeFile(record.getAuthorizationCode()) + "_" + System.currentTimeMillis();

            Path templatePath = resolveUploadPath(templateUrl.trim());
            if (!Files.exists(templatePath)) {
                throw new IllegalArgumentException("模板文件不存在: " + templatePath.toAbsolutePath());
            }
            Path wordPath = outDir.resolve(baseName + ".docx");
            buildWordByTemplate(templatePath, wordPath, record);

            Path pdfPath = outDir.resolve(baseName + ".pdf");
            convertWordToPdf(wordPath, pdfPath);

            String token = verifyService.createVerifyToken(record);
            String verifyUrl = buildVerifyUrl(token);
            Path qrPath = outDir.resolve(baseName + "_qr.png");
            writeQrImage(verifyUrl, qrPath, 240, 240);

            Path watermarkedPdf = outDir.resolve(baseName + "_wm.pdf");
            addWatermarkAndQr(pdfPath, watermarkedPdf, isBlank(watermarkCode) ? record.getAuthorizationCode() : watermarkCode, qrPath);

            Path imagePdf = outDir.resolve(baseName + "_image.pdf");
            convertPdfToImagePdf(watermarkedPdf, imagePdf);

            Path encryptedPdf = outDir.resolve(baseName + "_secure.pdf");
            encryptPdf(imagePdf, encryptedPdf);

            String sha256 = sha256Hex(encryptedPdf);
            record.setWordUrl(toUploadUrl(wordPath));
            record.setPdfUrl(toUploadUrl(pdfPath));
            record.setFinalPdfUrl(toUploadUrl(encryptedPdf));
            record.setQrUrl(toUploadUrl(qrPath));
            record.setVerifyToken(token);
            record.setDocumentSha256(sha256);
            record.setStatus("已生成");
            record = recordRepository.save(record);
            return record;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            if (e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isBlank()) {
                detail = detail + " — " + e.getCause().getMessage();
            }
            throw new RuntimeException("生成授权文档失败: " + detail, e);
        }
    }

    public AuthorizationVerifyResult verify(String token, String channel, HttpServletRequest request) {
        AuthorizationVerifyResult out = new AuthorizationVerifyResult();
        token = normalizeVerifyToken(token);
        String fingerprint = token == null || token.isBlank() ? "-" : sha256Hex(token);
        try {
            Map<String, Object> payload = verifyService.parseAndValidate(token);
            Long rid = Long.parseLong(String.valueOf(payload.get("rid")));
            AuthorizationRecord record = get(rid);
            // 库里有落库 token 时必须一致（区分是否重新生成过 PDF）；兼容微信/浏览器对 query/path 的多余编码
            if (!isBlank(record.getVerifyToken()) && !verifyTokenMatchesStored(record.getVerifyToken(), token)) {
                out.setValid(false);
                out.setMessage("二维码令牌与当前授权文件不一致（若刚重新生成过授权 PDF，请扫描最新文件上的二维码）");
                scanLogService.log(record, "TOKEN_MISMATCH", fingerprint, channel, request);
                return out;
            }
            if (isBlank(record.getFinalPdfUrl())) {
                out.setValid(false);
                out.setMessage("授权文档未生成");
                scanLogService.log(record, "NO_FILE", fingerprint, channel, request);
                return out;
            }
            Path finalPath = resolveUploadPath(record.getFinalPdfUrl());
            if (!Files.exists(finalPath)) {
                out.setValid(false);
                out.setMessage("授权文件不存在");
                scanLogService.log(record, "FILE_MISSING", fingerprint, channel, request);
                return out;
            }
            String currentSha = sha256Hex(finalPath);
            boolean same = !isBlank(record.getDocumentSha256()) && record.getDocumentSha256().equalsIgnoreCase(currentSha);
            if (!same) {
                out.setValid(false);
                out.setMessage("文件校验失败，疑似被篡改");
                scanLogService.log(record, "HASH_MISMATCH", fingerprint, channel, request);
                return out;
            }
            out.setValid(true);
            out.setMessage("验真通过，文件未被篡改");
            out.setAuthorizationRecordId(record.getId());
            out.setAuthorizationCode(record.getAuthorizationCode());
            out.setPlatformName(record.getPlatformName());
            out.setGrantorName(record.getGrantorName());
            out.setGranteeName(record.getGranteeName());
            out.setProductModel(record.getProductModel());
            out.setProjectName(record.getProjectName());
            out.setValidFrom(record.getValidFrom() == null ? null : record.getValidFrom().toString());
            out.setValidTo(record.getValidTo() == null ? null : record.getValidTo().toString());
            scanLogService.log(record, "OK", fingerprint, channel, request);
            return out;
        } catch (Exception e) {
            out.setValid(false);
            out.setMessage(e.getMessage() != null ? e.getMessage() : "验真失败");
            scanLogService.log(null, "INVALID", fingerprint, channel, request);
            return out;
        }
    }

    /** 去掉首尾空白，并对 path/query 中偶发的双重 URL 编码做一次解码（不改变合法 base64url 内容） */
    private String normalizeVerifyToken(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return t;
        }
        try {
            String once = URLDecoder.decode(t, StandardCharsets.UTF_8).trim();
            if (!once.equals(t)) {
                t = once;
            }
        } catch (Exception ignored) {
        }
        return t;
    }

    private boolean verifyTokenMatchesStored(String storedRaw, String requestRaw) {
        if (storedRaw == null || requestRaw == null) {
            return false;
        }
        String stored = storedRaw.trim();
        String req = requestRaw.trim();
        if (stored.equals(req)) {
            return true;
        }
        try {
            String d1 = URLDecoder.decode(req, StandardCharsets.UTF_8).trim();
            if (stored.equals(d1)) {
                return true;
            }
            return stored.equals(URLDecoder.decode(d1, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return false;
        }
    }

    private void buildWordByTemplate(Path templatePath, Path outPath, AuthorizationRecord record) throws Exception {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("${authorizationCode}", nvl(record.getAuthorizationCode()));
        placeholders.put("${platformName}", nvl(record.getPlatformName()));
        placeholders.put("${grantorName}", nvl(record.getGrantorName()));
        placeholders.put("${granteeName}", nvl(record.getGranteeName()));
        placeholders.put("${authorizedSubject}", nvl(record.getAuthorizedSubject()));
        placeholders.put("${productModel}", nvl(record.getProductModel()));
        placeholders.put("${projectName}", nvl(record.getProjectName()));
        placeholders.put("${validFrom}", record.getValidFrom() == null ? "" : record.getValidFrom().toString());
        placeholders.put("${validTo}", record.getValidTo() == null ? "" : record.getValidTo().toString());
        placeholders.put("${generateDate}", formatChineseDate(LocalDate.now()));
        placeholders.put("${remark}", nvl(record.getRemark()));
        try (InputStream in = Files.newInputStream(templatePath);
             XWPFDocument doc = new XWPFDocument(in);
             OutputStream out = Files.newOutputStream(outPath)) {
            replaceDocxPlaceholders(doc, placeholders);
            doc.write(out);
        }
    }

    private void replaceDocxPlaceholders(XWPFDocument doc, Map<String, String> placeholders) {
        replacePlaceholdersInBody(doc, placeholders);
        for (XWPFHeader h : doc.getHeaderList()) {
            replacePlaceholdersInBody(h, placeholders);
        }
        for (XWPFFooter f : doc.getFooterList()) {
            replacePlaceholdersInBody(f, placeholders);
        }
    }

    private void replacePlaceholdersInBody(IBody body, Map<String, String> placeholders) {
        if (body == null) {
            return;
        }
        for (org.apache.poi.xwpf.usermodel.XWPFParagraph p : body.getParagraphs()) {
            replaceParagraph(p, placeholders);
        }
        for (org.apache.poi.xwpf.usermodel.XWPFTable t : body.getTables()) {
            t.getRows().forEach(r ->
                    r.getTableCells().forEach(c -> c.getParagraphs().forEach(p -> replaceParagraph(p, placeholders))));
        }
    }

    private void replaceParagraph(org.apache.poi.xwpf.usermodel.XWPFParagraph p, Map<String, String> placeholders) {
        String text = p.getText();
        if (text == null || text.isEmpty()) return;
        // 优先逐 run 替换，尽量保留模板中已有字体（如宋体）与东亚文字样式，避免转 PDF 时中文丢失。
        boolean runChanged = false;
        List<org.apache.poi.xwpf.usermodel.XWPFRun> runs = p.getRuns();
        if (runs != null && !runs.isEmpty()) {
            for (org.apache.poi.xwpf.usermodel.XWPFRun run : runs) {
                String runText = run.getText(0);
                if (runText == null || runText.isEmpty()) continue;
                String replacedRun = applyPlaceholders(runText, placeholders);
                if (!replacedRun.equals(runText)) {
                    run.setText(replacedRun, 0);
                    applyCjkFontIfNeeded(run, replacedRun);
                    runChanged = true;
                }
            }
        }
        if (runChanged) return;

        // 回退：处理占位符被拆成多 run 的情况。
        String replaced = applyPlaceholders(text, placeholders);
        if (!replaced.equals(text)) {
            // 必须先读出样式再 removeRun；否则首个 run 被移除后 styleRun 对应 XML 已断开，会抛 XmlValueDisconnectedException
            String inheritFontFamily = null;
            Double inheritFontSize = null;
            boolean inheritBold = false;
            boolean inheritItalic = false;
            String inheritColor = null;
            if (runs != null && !runs.isEmpty()) {
                org.apache.poi.xwpf.usermodel.XWPFRun styleRun = runs.get(0);
                inheritFontFamily = styleRun.getFontFamily();
                inheritFontSize = styleRun.getFontSizeAsDouble();
                inheritBold = styleRun.isBold();
                inheritItalic = styleRun.isItalic();
                inheritColor = styleRun.getColor();
            }
            for (int i = p.getRuns().size() - 1; i >= 0; i--) {
                p.removeRun(i);
            }
            org.apache.poi.xwpf.usermodel.XWPFRun run = p.createRun();
            if (inheritFontFamily != null && !inheritFontFamily.isBlank()) {
                run.setFontFamily(inheritFontFamily);
            }
            if (inheritFontSize != null && inheritFontSize > 0) {
                run.setFontSize(inheritFontSize);
            }
            run.setBold(inheritBold);
            run.setItalic(inheritItalic);
            if (inheritColor != null) {
                run.setColor(inheritColor);
            }
            run.setText(replaced, 0);
            applyCjkFontIfNeeded(run, replaced);
        }
    }

    private boolean containsCjkScript(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        return s.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HIRAGANA
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL);
    }

    /** 占位替换后的中文若仍沿用西文字体，docx→pdf 可能缺字形；强制东亚字体名（Windows 常见） */
    private void applyCjkFontIfNeeded(org.apache.poi.xwpf.usermodel.XWPFRun run, String text) {
        if (run == null || !containsCjkScript(text)) {
            return;
        }
        run.setFontFamily("宋体");
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String replaced = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            replaced = replaced.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return replaced;
    }

    private void convertWordToPdf(Path docx, Path pdf) throws Exception {
        // 默认 PdfConverter 使用 Helvetica 等西文字体，中文无法嵌入，PDFBox 栅格化后正文会变成空白/方框。
        PdfOptions options = PdfOptions.create();
        options.fontEncoding(com.lowagie.text.pdf.BaseFont.IDENTITY_H);
        options.fontProvider(new IFontProvider() {
            @Override
            public com.lowagie.text.Font getFont(String familyName, String encoding, float size, int style, Color color) {
                float sz = size > 0 ? size : 12f;
                try {
                    com.lowagie.text.pdf.BaseFont bf = getOrCreateLowagieCjkBaseFont();
                    if (color != null) {
                        return new com.lowagie.text.Font(bf, sz, style, color);
                    }
                    return new com.lowagie.text.Font(bf, sz, style);
                } catch (Exception e) {
                    if (color != null) {
                        return new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, sz, style, color);
                    }
                    return new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, sz, style);
                }
            }
        });
        try (InputStream in = Files.newInputStream(docx);
             XWPFDocument document = new XWPFDocument(in);
             OutputStream out = Files.newOutputStream(pdf)) {
            PdfConverter.getInstance().convert(document, out, options);
        }
    }

    /**
     * 将 jar 内 /fonts/SourceHanSansCN.ttf 复制到临时文件（仅一次），供 PdfConverter / iText 加载。
     */
    private Path resolveBundledCjkFontPath() {
        if (bundledCjkFontPath != null && Files.exists(bundledCjkFontPath)) {
            return bundledCjkFontPath;
        }
        synchronized (this) {
            if (bundledCjkFontPath != null && Files.exists(bundledCjkFontPath)) {
                return bundledCjkFontPath;
            }
            try (InputStream in = AuthorizationService.class.getResourceAsStream("/fonts/SourceHanSansCN.ttf")) {
                if (in == null) {
                    return null;
                }
                Path tmp = Files.createTempFile("oms-cjk-", ".ttf");
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                bundledCjkFontPath = tmp;
                return tmp;
            } catch (Exception e) {
                System.err.println("[AuthorizationService] 释放内置中文字体失败: " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * 返回第一个可用的字体路径（含 ttc 的 ,0 后缀），供水印与 docx→pdf 共用。
     * 优先使用 jar 内置字体，避免阿里云等 Linux 镜像无 Noto/文泉驿 时生成失败。
     */
    private String firstExistingCjkFontSpec() {
        Path bundled = resolveBundledCjkFontPath();
        if (bundled != null) {
            return bundled.toAbsolutePath().toString();
        }
        for (String candidate : CJK_FONT_FILE_CANDIDATES) {
            String filePath = candidate.contains(",") ? candidate.substring(0, candidate.indexOf(',')) : candidate;
            if (Files.exists(Paths.get(filePath))) {
                return candidate;
            }
        }
        return null;
    }

    private com.lowagie.text.pdf.BaseFont getOrCreateLowagieCjkBaseFont() throws Exception {
        if (lowagieCjkBaseFont != null) {
            return lowagieCjkBaseFont;
        }
        synchronized (this) {
            if (lowagieCjkBaseFont != null) {
                return lowagieCjkBaseFont;
            }
            String spec = firstExistingCjkFontSpec();
            if (spec != null) {
                lowagieCjkBaseFont = com.lowagie.text.pdf.BaseFont.createFont(
                        spec, com.lowagie.text.pdf.BaseFont.IDENTITY_H, true);
                return lowagieCjkBaseFont;
            }
            try {
                lowagieCjkBaseFont = com.lowagie.text.pdf.BaseFont.createFont(
                        "STSong-Light", "UniGB-UCS2-H", false);
            } catch (Exception ignored) {
                lowagieCjkBaseFont = com.lowagie.text.pdf.BaseFont.createFont(
                        com.lowagie.text.pdf.BaseFont.HELVETICA, com.lowagie.text.pdf.BaseFont.WINANSI, false);
            }
            return lowagieCjkBaseFont;
        }
    }

    private void writeQrImage(String text, Path path, int width, int height) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        ImageIO.write(img, "png", path.toFile());
    }

    private void addWatermarkAndQr(Path srcPdf, Path outPdf, String watermarkCode, Path qrPath) throws Exception {
        PdfReader reader = new PdfReader(srcPdf.toAbsolutePath().toString());
        try (OutputStream os = Files.newOutputStream(outPdf)) {
            PdfStamper stamper = new PdfStamper(reader, os);
            String watermarkText = (watermarkCode == null ? "-" : watermarkCode) + " 禁止转授权和二次授权";
            // 按需求固定：文字水印、连续重复铺满、15x15、字号6、角度20、颜色更亮。
            int rows = 15;
            int cols = 15;
            float fontSize = 6f;
            float rotation = 20f;
            // 提升可见性：在图片化PDF流程中，过亮颜色会被白底吞没。
            float fillOpacity = 0x8C / 255f;
            Font font = new Font(resolveWatermarkBaseFont(), fontSize, Font.NORMAL, new BaseColor(0xDC, 0xDC, 0xDC));
            byte[] qrBytes = Files.readAllBytes(qrPath);
            com.itextpdf.text.Image qrImage = com.itextpdf.text.Image.getInstance(qrBytes);
            qrImage.scaleToFit(92, 92);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                Rectangle page = reader.getPageSizeWithRotation(i);
                PdfContentByte over = stamper.getOverContent(i);
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(fillOpacity);
                over.saveState();
                over.setGState(gs);
                float stepX = page.getWidth() / (cols + 1f);
                float stepY = page.getHeight() / (rows + 1f);
                for (int r = 1; r <= rows; r++) {
                    float y = stepY * r;
                    for (int c = 1; c <= cols; c++) {
                        float x = stepX * c;
                        ColumnText.showTextAligned(over, Element.ALIGN_CENTER,
                                new Phrase(watermarkText, font), x, y, rotation);
                    }
                }
                over.restoreState();

                com.itextpdf.text.Image qr = com.itextpdf.text.Image.getInstance(qrImage);
                qr.setAbsolutePosition(page.getRight() - 110f, page.getTop() - 110f);
                over.addImage(qr);
            }
            stamper.close();
        } finally {
            reader.close();
        }
    }

    private void convertPdfToImagePdf(Path srcPdf, Path outPdf) throws Exception {
        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(srcPdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                pages.add(renderer.renderImageWithDPI(i, 220, ImageType.RGB));
            }
        }
        if (pages.isEmpty()) throw new IllegalArgumentException("PDF 无页面");

        com.itextpdf.text.Document outDoc = new com.itextpdf.text.Document(new Rectangle(PageSize.A4));
        try (OutputStream os = Files.newOutputStream(outPdf)) {
            PdfWriter.getInstance(outDoc, os);
            outDoc.open();
            for (BufferedImage image : pages) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", baos);
                byte[] bytes = baos.toByteArray();
                com.itextpdf.text.Image img = com.itextpdf.text.Image.getInstance(bytes);
                Rectangle pageRect = new Rectangle(img.getWidth(), img.getHeight());
                outDoc.setPageSize(pageRect);
                outDoc.newPage();
                img.setAbsolutePosition(0, 0);
                img.scaleAbsolute(pageRect.getWidth(), pageRect.getHeight());
                outDoc.add(img);
            }
            // Must close document before output stream closes, otherwise iText flushes to a closed channel.
            outDoc.close();
        }
    }

    private void encryptPdf(Path srcPdf, Path outPdf) throws Exception {
        PdfReader reader = new PdfReader(srcPdf.toAbsolutePath().toString());
        try (OutputStream os = Files.newOutputStream(outPdf)) {
            PdfStamper stamper = new PdfStamper(reader, os);
            byte[] ownerPwd = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            stamper.setEncryption(
                    null,
                    ownerPwd,
                    PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_SCREENREADERS,
                    PdfWriter.ENCRYPTION_AES_256
            );
            stamper.close();
        } finally {
            reader.close();
        }
    }

    private Path resolveUploadPath(String urlOrPath) {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            try {
                java.net.URL u = new java.net.URL(urlOrPath);
                return resolveUploadPath(u.getPath());
            } catch (Exception e) {
                throw new IllegalArgumentException("无效模板地址: " + urlOrPath);
            }
        }
        String p = urlOrPath;
        if (p.startsWith("/uploads/")) p = p.substring("/uploads/".length());
        return Paths.get(uploadDir, p).normalize();
    }

    private String toUploadUrl(Path absolute) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path abs = absolute.toAbsolutePath().normalize();
        Path rel = root.relativize(abs);
        return "/uploads/" + rel.toString().replace("\\", "/");
    }

    private String buildVerifyUrl(String token) {
        String base = verifyPublicBaseUrl == null ? "" : verifyPublicBaseUrl.trim();
        // 兼容历史 hash 链接配置：http://host/#/authorization/verify -> http://host/authorization/verify
        int hashRouteIdx = base.indexOf("#/");
        if (hashRouteIdx >= 0) {
            String prefix = base.substring(0, hashRouteIdx);
            String route = base.substring(hashRouteIdx + 1);
            base = prefix + route;
        }
        if (base.contains("?")) {
            return base + "&token=" + token;
        }
        return base + "?token=" + token;
    }

    private String sha256Hex(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return String.format("%064x", new BigInteger(1, md.digest(bytes)));
        } catch (Exception e) {
            throw new RuntimeException("计算文件哈希失败: " + e.getMessage(), e);
        }
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, digest));
        } catch (Exception e) {
            return "ERR";
        }
    }

    private void fillCreator(AuthorizationRecord record) {
        String username = SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) return;
        com.oms.entity.User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return;
        record.setCreatedBy(user.getId());
        record.setCreatedByName(user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : user.getUsername());
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = headerRow.getFirstCellNum(); i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String text = getCellAsText(cell);
            if (text != null && !text.isBlank()) map.put(text.trim(), i);
        }
        return map;
    }

    private String getCellByAliases(Row row, Map<String, Integer> headerMap, String... aliases) {
        for (String alias : aliases) {
            Integer idx = headerMap.get(alias);
            if (idx == null) continue;
            String v = getCellAsText(row, idx);
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }

    /**
     * 映射优先：mapping[fieldKey]=表头名；若未配置或未命中，则回退内置别名。
     */
    private String getCellByMappingOrAliases(Row row,
                                             Map<String, Integer> headerMap,
                                             Map<String, String> mapping,
                                             String fieldKey,
                                             String... aliases) {
        Integer mappedIdx = resolveHeaderIndex(headerMap, mapping, fieldKey);
        if (mappedIdx != null) {
            String v = getCellAsText(row, mappedIdx);
            if (!isBlank(v)) return v.trim();
        }
        return getCellByAliases(row, headerMap, aliases);
    }

    /**
     * 兼容 Excel 合并单元格：若当前格为空，尝试读取所在合并区域左上角的值。
     */
    private String getCellAsText(Row row, int colIndex) {
        if (row == null) return null;
        Cell direct = row.getCell(colIndex);
        String directText = getCellAsText(direct);
        if (!isBlank(directText)) return directText;
        Cell mergedTopLeft = findMergedRegionTopLeftCell(row, colIndex);
        return getCellAsText(mergedTopLeft);
    }

    private Cell findMergedRegionTopLeftCell(Row row, int colIndex) {
        if (row == null || row.getSheet() == null) return null;
        Sheet sheet = row.getSheet();
        int rowIndex = row.getRowNum();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.isInRange(rowIndex, colIndex)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                return firstRow == null ? null : firstRow.getCell(region.getFirstColumn());
            }
        }
        return null;
    }

    private String getCellAsText(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new DataFormatter().formatCellValue(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> new DataFormatter().formatCellValue(cell);
            default -> null;
        };
    }

    private LocalDate parseDateLoose(String s) {
        if (isBlank(s)) return null;
        String v = normalizeDateText(s);
        if (v.matches("^\\d{5,6}(\\.0+)?$")) {
            try {
                double serial = Double.parseDouble(v);
                return DateUtil.getLocalDateTime(serial).toLocalDate();
            } catch (Exception ignored) {
            }
        }
        String token = extractFirstDateToken(v);
        if (!isBlank(token)) {
            LocalDate parsed = parseDateToken(token);
            if (parsed != null) return parsed;
        }
        return parseDateToken(v);
    }

    private LocalDate parseDateToken(String raw) {
        if (isBlank(raw)) return null;
        String v = normalizeDateText(raw);
        v = v.replaceAll("-{2,}", "-");
        if (v.endsWith("-")) v = v.substring(0, v.length() - 1);
        try {
            if (v.length() > 10 && v.contains("-")) {
                v = v.substring(0, 10);
            }
            // 兼容两位年份：YY-M-D / YY.MM.DD / YY/MM/DD，默认映射到 20YY。
            if (v.matches("^\\d{2}-\\d{1,2}-\\d{1,2}$")) {
                String[] parts = v.split("-");
                int yy = Integer.parseInt(parts[0]);
                int y = 2000 + yy;
                int m = Integer.parseInt(parts[1]);
                int d = Integer.parseInt(parts[2]);
                YearMonth ym = YearMonth.of(y, m);
                d = Math.min(d, ym.lengthOfMonth());
                return LocalDate.of(y, m, d);
            }
            if (v.matches("^\\d{8}$")) {
                return LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            if (v.matches("^\\d{6}$")) {
                int yy = Integer.parseInt(v.substring(0, 2));
                int y = 2000 + yy;
                int m = Integer.parseInt(v.substring(2, 4));
                int d = Integer.parseInt(v.substring(4, 6));
                YearMonth ym = YearMonth.of(y, m);
                d = Math.min(d, ym.lengthOfMonth());
                return LocalDate.of(y, m, d);
            }
            // 兜底：手动拆分 yyyy-M-d，自动修正超出当月天数（如 2025-6-31 -> 2025-06-30）。
            if (v.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
                String[] parts = v.split("-");
                int y = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int d = Integer.parseInt(parts[2]);
                YearMonth ym = YearMonth.of(y, m);
                d = Math.min(d, ym.lengthOfMonth());
                return LocalDate.of(y, m, d);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<LocalDate> parseDateRangeLoose(String s) {
        if (isBlank(s)) return List.of();
        String v = normalizeDateText(s);
        Pattern p = Pattern.compile("(\\d{4}[\\-./]\\d{1,2}[\\-./]\\d{1,2}|\\d{2}[\\-./]\\d{1,2}[\\-./]\\d{1,2}|\\d{4}年\\d{1,2}月\\d{1,2}日?|\\d{2}年\\d{1,2}月\\d{1,2}日?|\\d{8}|\\d{6})");
        Matcher m = p.matcher(v);
        List<LocalDate> out = new ArrayList<>();
        while (m.find()) {
            LocalDate d = parseDateToken(m.group(1));
            if (d != null) out.add(d);
            if (out.size() >= 2) break;
        }
        return out;
    }

    private String extractFirstDateToken(String text) {
        if (isBlank(text)) return null;
        Pattern p = Pattern.compile("(\\d{4}[\\-./]\\d{1,2}[\\-./]\\d{1,2}|\\d{2}[\\-./]\\d{1,2}[\\-./]\\d{1,2}|\\d{4}年\\d{1,2}月\\d{1,2}日?|\\d{2}年\\d{1,2}月\\d{1,2}日?|\\d{8}|\\d{6})");
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String normalizeDateText(String s) {
        if (s == null) return "";
        String v = s.trim().replaceAll("\\s+", "");
        v = v.replace('．', '.').replace('。', '.').replace('／', '/').replace('－', '-');
        v = v.replace("年", "-").replace("月", "-").replace("日", "");
        v = v.replace(".", "-").replace("/", "-");
        return v;
    }

    private String generateAuthorizationCode(String platform) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String p = isBlank(platform) ? "AUTH" : platform.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (p.length() > 8) p = p.substring(0, 8);
        return p + "-" + date + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String safeFile(String s) {
        if (s == null || s.isBlank()) return "NA";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String formatChineseDate(LocalDate date) {
        if (date == null) return "";
        String year = String.valueOf(date.getYear())
                .replace("0", "〇")
                .replace("1", "一")
                .replace("2", "二")
                .replace("3", "三")
                .replace("4", "四")
                .replace("5", "五")
                .replace("6", "六")
                .replace("7", "七")
                .replace("8", "八")
                .replace("9", "九");
        return year + "年" + toChineseNumber(date.getMonthValue()) + "月" + toChineseNumber(date.getDayOfMonth()) + "日";
    }

    private String toChineseNumber(int n) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n <= 10) {
            if (n == 10) return "十";
            return digits[n];
        }
        if (n < 20) {
            return "十" + digits[n % 10];
        }
        if (n % 10 == 0) {
            return digits[n / 10] + "十";
        }
        return digits[n / 10] + "十" + digits[n % 10];
    }

    /**
     * 选择可用的中文字体，避免因环境缺少 STSong 而导致生成失败。
     */
    private BaseFont resolveWatermarkBaseFont() throws Exception {
        String spec = firstExistingCjkFontSpec();
        if (spec != null) {
            try {
                // 文件字体优先嵌入，避免 PDF 在无该字体客户端上显示异常。
                return BaseFont.createFont(spec, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
            }
        }
        try {
            return BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        } catch (Exception ignored) {
        }
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }

    private String trimToLength(String s, int maxLen) {
        String v = trimOrNull(s);
        if (v == null) return null;
        if (v.length() <= maxLen) return v;
        return v.substring(0, maxLen);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static class SheetHeaderSelection {
        final Sheet sheet;
        final int headerRowIndex;
        final Map<String, Integer> headerMap;

        private SheetHeaderSelection(Sheet sheet, int headerRowIndex, Map<String, Integer> headerMap) {
            this.sheet = sheet;
            this.headerRowIndex = headerRowIndex;
            this.headerMap = headerMap;
        }
    }
}
