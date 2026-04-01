package com.oms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.service.JdTrackingPushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/jd/tracking")
public class JdTrackingPushController {

    private final JdTrackingPushService pushService;
    private final ObjectMapper objectMapper;

    public JdTrackingPushController(JdTrackingPushService pushService, ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    /** 京东轨迹标准推送接收端点（jd_tracking_push）。 */
    @PostMapping(value = "/push", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, Object>> receivePush(@RequestBody(required = false) String rawBody) {
        int updated = 0;
        try {
            JsonNode body = null;
            if (rawBody != null && !rawBody.trim().isEmpty()) {
                body = objectMapper.readTree(rawBody);
            }
            updated = pushService.handlePush(body);
        } catch (Exception e) {
            // 对接平台通常只认可固定成功应答，异常仅记录日志避免持续重试风暴
            log.error("处理京东轨迹推送失败", e);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", 1000);
        res.put("msg", "请求成功");
        res.put("success", true);
        res.put("updatedOrders", updated);
        return ResponseEntity.ok(res);
    }
}

