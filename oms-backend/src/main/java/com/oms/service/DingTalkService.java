package com.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.DingTalkConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DingTalkService {

    @Autowired
    private DingTalkConfig dingTalkConfig;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendTextMessage(String content) {
        sendTextMessage(content, null);
    }

    public void sendTextMessage(String content, List<String> atMobiles) {
        if (!dingTalkConfig.getEnabled()) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "text");
        
        Map<String, Object> text = new HashMap<>();
        text.put("content", content);
        message.put("text", text);
        
        // 添加@指定人
        List<String> normalizedMobiles = normalizeAtMobiles(atMobiles);
        if (!normalizedMobiles.isEmpty()) {
            Map<String, Object> at = new HashMap<>();
            at.put("atMobiles", normalizedMobiles);
            at.put("isAtAll", false);
            message.put("at", at);
        }
        
        sendMessage(message);
    }

    public void sendMarkdownMessage(String title, String text) {
        sendMarkdownMessage(title, text, null);
    }

    public void sendMarkdownMessage(String title, String text, List<String> atMobiles) {
        if (!dingTalkConfig.getEnabled()) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", text);
        message.put("markdown", markdown);
        
        // 添加@指定人
        List<String> normalizedMobiles = normalizeAtMobiles(atMobiles);
        if (!normalizedMobiles.isEmpty()) {
            Map<String, Object> at = new HashMap<>();
            at.put("atMobiles", normalizedMobiles);
            at.put("isAtAll", false);
            message.put("at", at);
        }
        
        sendMessage(message);
    }

    public void sendOrderNotification(String orderNo, String customerName, String productName, BigDecimal amount) {
        sendOrderNotification(orderNo, customerName, productName, amount, null);
    }

    public void sendOrderNotification(String orderNo, String customerName, String productName, BigDecimal amount, List<String> atMobiles) {
        String title = "📦 新订单通知";
        String text = String.format(
            "## 📦 新订单通知\n\n" +
            "**订单号：** %s\n\n" +
            "**客户名称：** %s\n\n" +
            "**商品名称：** %s\n\n" +
            "**订单金额：** ¥%.2f\n\n" +
            "---\n" +
            "*来自 OMS 订单系统*",
            orderNo, customerName, productName, amount
        );
        sendMarkdownMessage(title, text, atMobiles);
    }

    public void sendLogisticsNotification(String orderNo, String trackingNumber, String status) {
        sendLogisticsNotification(orderNo, trackingNumber, status, null);
    }

    public void sendLogisticsNotification(String orderNo, String trackingNumber, String status, List<String> atMobiles) {
        sendLogisticsNotificationDetailed(orderNo, trackingNumber, status, null, null, null, null, null, null, atMobiles);
    }

    public void sendLogisticsNotificationDetailed(String orderNo,
                                                  String trackingNumber,
                                                  String status,
                                                  String returnTrackingNumber,
                                                  String businessOwners,
                                                  String deliverySummary,
                                                  String requirementSummary,
                                                  List<LogisticsLineSummary> lineSummaries,
                                                  List<String> snCodes,
                                                  List<String> atMobiles) {
        String title = "🚚 物流更新通知";
        String normalizedOrderNo = normalizeMarkdownValue(orderNo);
        String normalizedTracking = normalizeMarkdownValue(trackingNumber);
        String normalizedStatus = normalizeMarkdownValue(status);
        String normalizedReturnTracking = normalizeMarkdownValue(returnTrackingNumber);
        String normalizedBusinessOwners = normalizeMarkdownValue(businessOwners);
        String normalizedDeliverySummary = normalizeMarkdownValue(deliverySummary);
        String normalizedRequirementSummary = normalizeMarkdownValue(requirementSummary);
        String lineSummaryMarkdown = buildLogisticsLineSummaryMarkdown(lineSummaries);
        String snSummary = buildSnSummary(snCodes);
        String text = String.format(
            "## 🚚 物流更新通知\n\n" +
            "**订单号：** %s\n\n" +
            "**物流单号：** %s\n\n" +
            "**物流状态：** %s\n\n" +
            "**回单物流单号：** %s\n\n" +
            "**业务员：** %s\n\n" +
            "**交付信息：** %s\n\n" +
            "**发货要求：** %s\n\n" +
            "**商品明细：** %s\n\n" +
            "**SN编码：** %s\n\n" +
            "---\n" +
            "*来自 OMS 订单系统*",
            normalizedOrderNo,
            normalizedTracking,
            normalizedStatus,
            normalizedReturnTracking,
            normalizedBusinessOwners,
            normalizedDeliverySummary,
            normalizedRequirementSummary,
            lineSummaryMarkdown,
            snSummary
        );
        sendMarkdownMessage(title, text, atMobiles);
    }

    public void sendReceiptUploadNotification(String orderNo, String receiptStatus, String receiptTime, List<String> atMobiles) {
        String title = "📄 签收单上传通知";
        String normalizedStatus = (receiptStatus == null || receiptStatus.isBlank()) ? "签收单已上传" : receiptStatus;
        String normalizedTime = (receiptTime == null || receiptTime.isBlank()) ? "-" : receiptTime;
        String text = String.format(
            "## 📄 签收单上传通知\n\n" +
            "**订单号：** %s\n\n" +
            "**签收状态：** %s\n\n" +
            "**签收时间：** %s\n\n" +
            "---\n" +
            "*来自 OMS 订单系统*",
            orderNo, normalizedStatus, normalizedTime
        );
        sendMarkdownMessage(title, text, atMobiles);
    }

    public void sendStockWarning(String productName, String productCode, Integer stock) {
        sendStockWarning(productName, productCode, stock, null);
    }

    public void sendStockWarning(String productName, String productCode, Integer stock, List<String> atMobiles) {
        String title = "⚠️ 库存预警";
        String text = String.format(
            "## ⚠️ 库存预警\n\n" +
            "**商品名称：** %s\n\n" +
            "**商品编码：** %s\n\n" +
            "**当前库存：** %d\n\n" +
            "**库存不足，请及时补货！**\n\n" +
            "---\n" +
            "*来自 OMS 订单系统*",
            productName, productCode, stock
        );
        sendMarkdownMessage(title, text, atMobiles);
    }

    private void sendMessage(Map<String, Object> message) {
        try {
            String url = dingTalkConfig.getWebhookUrl();
            
            if (dingTalkConfig.getSecret() != null && !dingTalkConfig.getSecret().isEmpty()) {
                Long timestamp = System.currentTimeMillis();
                String sign = generateSign(timestamp, dingTalkConfig.getSecret());
                url += "&timestamp=" + timestamp + "&sign=" + sign;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(message), 
                headers
            );

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            System.out.println("钉钉消息发送结果: " + response.getBody());
        } catch (Exception e) {
            System.err.println("发送钉钉消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<String> normalizeAtMobiles(List<String> atMobiles) {
        if (atMobiles == null || atMobiles.isEmpty()) {
            return Collections.emptyList();
        }
        return atMobiles.stream()
                .map(this::normalizeDingTalkMobile)
                .filter(mobile -> !mobile.isEmpty())
                .distinct()
                .toList();
    }

    private String normalizeDingTalkMobile(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() > 11) {
            digits = digits.substring(digits.length() - 11);
        }
        return digits.matches("^1\\d{10}$") ? digits : "";
    }

    private String normalizeMarkdownValue(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? "-" : text;
    }

    private String buildLogisticsLineSummaryMarkdown(List<LogisticsLineSummary> lineSummaries) {
        if (lineSummaries == null || lineSummaries.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (LogisticsLineSummary summary : lineSummaries) {
            if (summary == null) continue;
            String model = normalizeMarkdownValue(summary.getModel());
            String qty = summary.getQuantity() == null ? "-" : String.valueOf(summary.getQuantity());
            parts.add(model + " x" + qty);
        }
        return parts.isEmpty() ? "-" : String.join("；", parts);
    }

    private String buildSnSummary(List<String> snCodes) {
        if (snCodes == null || snCodes.isEmpty()) {
            return "-";
        }
        List<String> normalized = snCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return "-";
        }
        if (normalized.size() <= 8) {
            return String.join("，", normalized);
        }
        return String.join("，", normalized.subList(0, 8)) + String.format(" 等共 %d 个", normalized.size());
    }

    public static class LogisticsLineSummary {
        private String model;
        private Integer quantity;

        public LogisticsLineSummary() {
        }

        public LogisticsLineSummary(String model, Integer quantity) {
            this.model = model;
            this.quantity = quantity;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    private String generateSign(Long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), StandardCharsets.UTF_8);
    }
}