package com.oms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.BaiduOcrConfig;
import com.oms.dto.BaiduVatInvoiceResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;

/**
 * 调用百度「通用文字识别」接口，作为本地 OCR 的补充。
 */
@Service
public class BaiduOcrService {

    private static final Logger log = LoggerFactory.getLogger(BaiduOcrService.class);

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";
    /** 增值税发票识别（结构化购/销方、票号、日期等），与 general_basic 独立 */
    private static final String VAT_INVOICE_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/vat_invoice";

    private final BaiduOcrConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private volatile String cachedAccessToken;
    private volatile long tokenExpireAtMillis;

    public BaiduOcrService(BaiduOcrConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int connect = config.getConnectTimeoutMs() != null ? config.getConnectTimeoutMs() : 15000;
        int read = config.getReadTimeoutMs() != null ? config.getReadTimeoutMs() : 90000;
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isEnabled() {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        return !resolveApiKey().isEmpty() && !resolveSecretKey().isEmpty();
    }

    /**
     * 是否可调用增值税发票专用接口。与「通用文字识别」开关 {@link #isEnabled()} 解耦：
     * 只要配置了 api-key/secret-key 且未关闭 vat-invoice-enabled，结算发票即可调用 vat_invoice（无需把 baidu.ocr.enabled 设为 true）。
     */
    public boolean isVatInvoiceEnabled() {
        if (!Boolean.TRUE.equals(config.getVatInvoiceEnabled())) {
            return false;
        }
        return !resolveApiKey().isEmpty() && !resolveSecretKey().isEmpty();
    }

    /**
     * 调用百度增值税发票识别。支持 PDF（首页渲染为图）与常见图片格式；失败返回 null，不抛给上层。
     */
    public BaiduVatInvoiceResult tryRecognizeVatInvoice(Path filePath, String originalFilename) {
        if (!isVatInvoiceEnabled() || filePath == null || !Files.isRegularFile(filePath)) {
            return null;
        }
        String name = originalFilename == null ? "" : originalFilename.toLowerCase();
        try {
            byte[] jpeg;
            if (name.endsWith(".pdf")) {
                byte[] pdfBytes = Files.readAllBytes(filePath);
                try (PDDocument doc = PDDocument.load(pdfBytes)) {
                    if (doc.getNumberOfPages() <= 0) {
                        return null;
                    }
                    PDFRenderer renderer = new PDFRenderer(doc);
                    BufferedImage page = renderer.renderImageWithDPI(0, 240, ImageType.RGB);
                    jpeg = toJpegUnderLimit(page);
                }
            } else if (name.matches(".*\\.(png|jpg|jpeg|bmp|gif|webp)$")) {
                BufferedImage img = ImageIO.read(filePath.toFile());
                if (img == null) {
                    return null;
                }
                jpeg = toJpegUnderLimit(toRgb(img));
            } else {
                return null;
            }
            if (jpeg == null || jpeg.length == 0) {
                return null;
            }
            return callVatInvoiceApi(jpeg);
        } catch (Exception e) {
            log.debug("百度增值税发票识别跳过: {}", e.getMessage());
            return null;
        }
    }

    private BaiduVatInvoiceResult callVatInvoiceApi(byte[] jpegBytes) {
        try {
            String token = getAccessToken();
            if (token == null || token.isBlank()) {
                log.warn("百度增值税发票识别未执行：access_token 为空");
                return null;
            }
            String url = UriComponentsBuilder.fromUriString(VAT_INVOICE_URL)
                    .queryParam("access_token", token)
                    .build(true)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", Base64.getEncoder().encodeToString(jpegBytes));
            body.add("seal_tag", "false");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("百度增值税发票识别 HTTP {}", resp.getStatusCode());
                return null;
            }
            String bodyText = resp.getBody();
            if (bodyText == null) {
                return null;
            }
            log.info("百度增值税发票识别已返回响应，body长度={}", bodyText.length());
            return parseVatInvoiceResponse(bodyText);
        } catch (Exception e) {
            log.warn("百度增值税发票识别失败: {}", e.getMessage());
            return null;
        }
    }

    private BaiduVatInvoiceResult parseVatInvoiceResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            // 成功时也可能带 error_code=0，仅非 0 视为失败
            if (root.has("error_code") && root.get("error_code").asInt() != 0) {
                int code = root.get("error_code").asInt();
                String msg = root.has("error_msg") ? root.get("error_msg").asText() : "";
                log.warn("百度增值税发票识别业务错误 error_code={} error_msg={}", code, msg);
                return null;
            }
            JsonNode wr = root.get("words_result");
            if (wr == null || wr.isNull()) {
                log.warn("百度增值税发票识别未返回 words_result");
                return null;
            }
            BaiduVatInvoiceResult out = new BaiduVatInvoiceResult();
            if (wr.isObject()) {
                log.info("百度增值税发票 words_result 为对象，顶层key={}", wr.fieldNames().hasNext() ? wr.fieldNames().next() : "<empty>");
                fillVatFromWordsResultObject(wr, out);
            } else if (wr.isArray()) {
                log.info("百度增值税发票 words_result 为数组，元素数={}", wr.size());
                fillVatFromWordsResultArray(wr, out);
            }
            if (!out.hasAnyField()) {
                log.warn("百度增值税发票识别已调用，但未解析出购销方/票号/日期字段");
                return null;
            }
            log.info("百度增值税发票识别解析结果 purchaser='{}' seller='{}' invoice='{}' date='{}'",
                    safe(out.getPurchaserName()), safe(out.getSellerName()), safe(out.getInvoiceNum()), safe(out.getInvoiceDateRaw()));
            return out;
        } catch (Exception e) {
            log.warn("百度增值税发票识别解析响应失败: {}", e.getMessage());
            return null;
        }
    }

    /** 少数版本 words_result 为数组 */
    private void fillVatFromWordsResultArray(JsonNode arr, BaiduVatInvoiceResult out) {
        for (JsonNode item : arr) {
            if (item != null && item.isObject()) {
                fillVatFromWordsResultObject(item, out);
                if (out.getPurchaserName() != null && out.getSellerName() != null) {
                    break;
                }
            }
        }
    }

    /** 兼容中英字段名及控制台返回的多种 key（含全电发票） */
    private void fillVatFromWordsResultObject(JsonNode wr, BaiduVatInvoiceResult out) {
        out.setPurchaserName(firstCompanyField(wr,
                "PurchaserName", "purchaser_name", "PurchaserRegisterName",
                "购买方名称", "购方名称", "购买方", "购买方信息", "买方名称"));
        out.setSellerName(firstCompanyField(wr,
                "SellerName", "seller_name", "SellerRegisterName",
                "销售方名称", "销方名称", "销售方", "销售方信息", "卖方名称"));
        out.setInvoiceNum(firstNonBlankPlain(wr,
                "InvoiceNum", "invoice_num", "发票号码", "InvoiceNumber"));
        out.setInvoiceDateRaw(firstNonBlankPlain(wr,
                "InvoiceDate", "invoice_date", "开票日期", "InvoiceTime"));
        if (out.getPurchaserName() == null) {
            out.setPurchaserName(scanWordsResultForCompanyByKey(wr, true));
        }
        if (out.getSellerName() == null) {
            out.setSellerName(scanWordsResultForCompanyByKey(wr, false));
        }
        // 嵌套：{ "购买方": { "名称": "..." }, "销售方": { "名称": "..." } }
        if (out.getPurchaserName() == null) {
            out.setPurchaserName(nestedNameUnder(wr, "购买方", "购方"));
        }
        if (out.getSellerName() == null) {
            out.setSellerName(nestedNameUnder(wr, "销售方", "销方"));
        }
    }

    private static String nestedNameUnder(JsonNode wr, String... parentHints) {
        var it = wr.fields();
        while (it.hasNext()) {
            var e = it.next();
            String k = e.getKey();
            boolean match = false;
            for (String h : parentHints) {
                if (k.contains(h)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            JsonNode v = e.getValue();
            if (v != null && v.isTextual()) {
                String t = v.asText().trim();
                if (!t.isBlank() && looksLikeCompanyTitle(t)) {
                    return t;
                }
            }
            if (v != null && v.isObject()) {
                String t = firstNonBlankPlain(v, "名称", "Name", "纳税人名称");
                if (t != null && !t.isBlank()) {
                    return t.trim();
                }
            }
        }
        return null;
    }

    /** 购/销方：值可能是字符串，也可能是 { "名称": "xxx" } */
    private static String firstCompanyField(JsonNode wr, String... keys) {
        for (String k : keys) {
            if (!wr.has(k)) {
                continue;
            }
            String t = extractCompanyValue(wr.get(k));
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
        }
        return null;
    }

    private static String extractCompanyValue(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isObject()) {
            String t = firstNonBlankPlain(n, "名称", "Name", "word", "纳税人名称");
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return nodeToText(n);
    }

    private static String firstNonBlankPlain(JsonNode wr, String... keys) {
        for (String k : keys) {
            if (!wr.has(k)) {
                continue;
            }
            String t = nodeToText(wr.get(k));
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
        }
        return null;
    }

    /** 按字段名粗匹配购方/销方（百度不同版本 key 不一致时兜底） */
    private static String scanWordsResultForCompanyByKey(JsonNode wr, boolean purchaser) {
        var it = wr.fields();
        while (it.hasNext()) {
            var e = it.next();
            String k = e.getKey();
            if (purchaser) {
                if (k.contains("销") || k.contains("卖")) {
                    continue;
                }
                if (!(k.contains("购") || k.contains("买"))) {
                    continue;
                }
            } else {
                if (k.contains("购") || k.contains("买")) {
                    continue;
                }
                if (!(k.contains("销") || k.contains("卖"))) {
                    continue;
                }
            }
            if (!(k.contains("名称") || k.contains("信息"))) {
                continue;
            }
            String t = extractCompanyValue(e.getValue());
            if (t != null && !t.isBlank() && looksLikeCompanyTitle(t)) {
                return t.trim();
            }
        }
        return null;
    }

    private static boolean looksLikeCompanyTitle(String t) {
        String s = t.trim();
        if (s.length() < 4 || s.length() > 200) {
            return false;
        }
        return s.contains("公司") || s.contains("厂") || s.contains("中心") || s.contains("院")
                || s.contains("店") || s.contains("部") || s.contains("有限合伙") || s.contains("集团");
    }

    private static String nodeToText(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isObject() && n.has("word")) {
            return n.get("word").asText("");
        }
        return n.asText(null);
    }

    private static String safe(String s) {
        return s == null ? "-" : s;
    }

    public int getMinLocalChars() {
        return config.getMinLocalChars() == null ? 30 : Math.max(0, config.getMinLocalChars());
    }

    public boolean shouldSupplement(String localNormalizedNoSpace) {
        if (!isEnabled()) {
            return false;
        }
        if (Boolean.TRUE.equals(config.getAlwaysTryAfterLocal())) {
            return true;
        }
        String n = localNormalizedNoSpace == null ? "" : localNormalizedNoSpace.trim();
        return n.length() < getMinLocalChars();
    }

    /**
     * 对图片调用百度 OCR，失败返回 null。
     */
    public String recognize(BufferedImage image) {
        if (!isEnabled() || image == null) {
            return null;
        }
        try {
            byte[] jpeg = toJpegUnderLimit(image);
            if (jpeg == null || jpeg.length == 0) {
                return null;
            }
            String token = getAccessToken();
            if (token == null || token.isBlank()) {
                log.warn("百度 OCR: 无法获取 access_token");
                return null;
            }

            String url = UriComponentsBuilder.fromUriString(OCR_URL)
                    .queryParam("access_token", token)
                    .build(true)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("image", Base64.getEncoder().encodeToString(jpeg));
            body.add("language_type", defaultIfBlank(config.getLanguageType(), "CHN_ENG"));
            body.add("detect_direction", Boolean.TRUE.equals(config.getDetectDirection()) ? "true" : "false");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("百度 OCR: HTTP 异常 {}", resp.getStatusCode());
                return null;
            }
            return parseOcrResponse(resp.getBody());
        } catch (Exception e) {
            log.warn("百度 OCR 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseOcrResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("error_code")) {
                int code = root.get("error_code").asInt();
                String msg = root.has("error_msg") ? root.get("error_msg").asText() : "";
                log.warn("百度 OCR 业务错误 error_code={} error_msg={}", code, msg);
                return null;
            }
            JsonNode items = root.get("words_result");
            if (items == null || !items.isArray() || items.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : items) {
                if (item.has("words")) {
                    String t = item.get("words").asText("");
                    if (!t.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(t.trim());
                    }
                }
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("百度 OCR 解析响应失败: {}", e.getMessage());
            return null;
        }
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < tokenExpireAtMillis - 120_000L) {
            return cachedAccessToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", resolveApiKey());
        body.add("client_secret", resolveSecretKey());

        try {
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(TOKEN_URL, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.has("error")) {
                log.warn("百度 token 错误: {}", resp.getBody());
                return null;
            }
            String token = root.has("access_token") ? root.get("access_token").asText(null) : null;
            int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt(2592000) : 2592000;
            if (token == null || token.isBlank()) {
                return null;
            }
            cachedAccessToken = token;
            tokenExpireAtMillis = now + Math.max(60, expiresIn) * 1000L;
            log.debug("百度 access_token 已刷新，约 {} 秒内有效", expiresIn);
            return token;
        } catch (Exception e) {
            log.warn("获取百度 access_token 失败: {}", e.getMessage());
            return null;
        }
    }

    private String resolveApiKey() {
        return defaultIfBlank(config.getApiKey(), defaultIfBlank(config.getAppId(), ""));
    }

    private String resolveSecretKey() {
        return defaultIfBlank(config.getSecretKey(), defaultIfBlank(config.getSecret(), ""));
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private byte[] toJpegUnderLimit(BufferedImage src) throws Exception {
        int maxBytes = config.getMaxUploadBytes() != null ? config.getMaxUploadBytes() : 3_800_000;
        BufferedImage rgb = toRgb(src);
        float quality = 0.92f;
        byte[] bytes = writeJpeg(rgb, quality);
        while (bytes.length > maxBytes && quality > 0.35f) {
            quality -= 0.08f;
            bytes = writeJpeg(rgb, quality);
        }
        if (bytes.length > maxBytes) {
            BufferedImage scaled = scale(rgb, 0.85);
            quality = 0.82f;
            bytes = writeJpeg(scaled, quality);
            while (bytes.length > maxBytes && quality > 0.35f) {
                quality -= 0.08f;
                bytes = writeJpeg(scaled, quality);
            }
        }
        return bytes;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static BufferedImage scale(BufferedImage src, double factor) {
        int w = Math.max(1, (int) (src.getWidth() * factor));
        int h = Math.max(1, (int) (src.getHeight() * factor));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("无 JPEG ImageWriter");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.1f, Math.min(1f, quality)));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
