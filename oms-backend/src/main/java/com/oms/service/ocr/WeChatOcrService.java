package com.oms.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.WeChatOcrConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
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
import java.util.Iterator;

/**
 * 调用微信「通用印刷体识别」接口，作为本地 OCR 的补充。
 * <p>
 * 文档与额度：微信服务市场「微信 OCR 识别」；接口形态为 multipart 上传 img。
 */
@Service
public class WeChatOcrService {

    private static final Logger log = LoggerFactory.getLogger(WeChatOcrService.class);

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String OCR_URL = "https://api.weixin.qq.com/cv/ocr/comm";

    private final WeChatOcrConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private volatile String cachedAccessToken;
    private volatile long tokenExpireAtMillis;

    public WeChatOcrService(WeChatOcrConfig config, ObjectMapper objectMapper) {
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
        String id = config.getAppId() == null ? "" : config.getAppId().trim();
        String secret = config.getAppSecret() == null ? "" : config.getAppSecret().trim();
        return !id.isEmpty() && !secret.isEmpty();
    }

    public int getMinLocalChars() {
        return config.getMinLocalChars() == null ? 30 : Math.max(0, config.getMinLocalChars());
    }

    /**
     * 是否应在本次识别中调用微信 OCR（结合额度策略）。
     *
     * @param localNormalizedNoSpace 本地 OCR 文本去掉所有空白后的长度判断依据
     */
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
     * 对图片调用微信 OCR，失败返回 null。
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
                log.warn("微信 OCR: 无法获取 access_token");
                return null;
            }
            String url = UriComponentsBuilder.fromUriString(OCR_URL)
                    .queryParam("access_token", token)
                    .build(true)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("img", new ByteArrayResource(jpeg) {
                @Override
                public String getFilename() {
                    return "ocr.jpg";
                }
            });
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("微信 OCR: HTTP 异常 {}", resp.getStatusCode());
                return null;
            }
            return parseOcrResponse(resp.getBody());
        } catch (Exception e) {
            log.warn("微信 OCR 调用失败: {}", e.getMessage());
            return null;
        }
    }

    private String parseOcrResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                int code = root.get("errcode").asInt();
                String msg = root.has("errmsg") ? root.get("errmsg").asText() : "";
                log.warn("微信 OCR 业务错误 errcode={} errmsg={}", code, msg);
                return null;
            }
            JsonNode items = root.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : items) {
                if (item.has("text")) {
                    String t = item.get("text").asText("");
                    if (!t.isBlank()) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(t.trim());
                    }
                }
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            log.warn("微信 OCR 解析响应失败: {}", e.getMessage());
            return null;
        }
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < tokenExpireAtMillis - 120_000L) {
            return cachedAccessToken;
        }
        String id = config.getAppId() == null ? "" : config.getAppId().trim();
        String secret = config.getAppSecret() == null ? "" : config.getAppSecret().trim();
        String url = UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", id)
                .queryParam("secret", secret)
                .build(true)
                .toUriString();
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                log.warn("微信 token 错误: {}", resp.getBody());
                return null;
            }
            String token = root.has("access_token") ? root.get("access_token").asText(null) : null;
            int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt(7200) : 7200;
            if (token == null || token.isBlank()) {
                return null;
            }
            cachedAccessToken = token;
            tokenExpireAtMillis = now + Math.max(60, expiresIn) * 1000L;
            log.debug("微信 access_token 已刷新，约 {} 秒内有效", expiresIn);
            return token;
        } catch (Exception e) {
            log.warn("获取微信 access_token 失败: {}", e.getMessage());
            return null;
        }
    }

    private byte[] toJpegUnderLimit(BufferedImage src) throws Exception {
        int maxBytes = config.getMaxUploadBytes() != null ? config.getMaxUploadBytes() : 1_800_000;
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
