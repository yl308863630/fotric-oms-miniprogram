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
        if (!dingTalkConfig.getEnabled()) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "text");
        
        Map<String, Object> text = new HashMap<>();
        text.put("content", content);
        message.put("text", text);
        
        sendMessage(message);
    }

    public void sendMarkdownMessage(String title, String text) {
        if (!dingTalkConfig.getEnabled()) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");
        
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", text);
        message.put("markdown", markdown);
        
        sendMessage(message);
    }

    public void sendOrderNotification(String orderNo, String customerName, String productName, BigDecimal amount) {
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
        sendMarkdownMessage(title, text);
    }

    public void sendLogisticsNotification(String orderNo, String trackingNumber, String status) {
        String title = "🚚 物流更新通知";
        String text = String.format(
            "## 🚚 物流更新通知\n\n" +
            "**订单号：** %s\n\n" +
            "**物流单号：** %s\n\n" +
            "**物流状态：** %s\n\n" +
            "---\n" +
            "*来自 OMS 订单系统*",
            orderNo, trackingNumber, status
        );
        sendMarkdownMessage(title, text);
    }

    public void sendStockWarning(String productName, String productCode, Integer stock) {
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
        sendMarkdownMessage(title, text);
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

    private String generateSign(Long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), StandardCharsets.UTF_8);
    }
}