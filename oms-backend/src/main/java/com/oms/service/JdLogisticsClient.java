package com.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.JdLogisticsConfig;
import com.oms.entity.LogisticsTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class JdLogisticsClient {

    private final JdLogisticsConfig config;
    private final JdTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public JdLogisticsClient(JdLogisticsConfig config, JdTokenService tokenService, ObjectMapper objectMapper) {
        this.config = config;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    /** 京东轨迹查询（Bearer Token 鉴权）。 */
    public LogisticsTrace queryOrderStatus(String companyName, String trackingNumber) {
        LogisticsTrace result = new LogisticsTrace();
        result.setCompany(companyName);
        result.setTrackingNumber(trackingNumber);

        try {
            String url = buildUrl(config.getBaseUrl(), "/ecap/v1/orders/status/get");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("waybillCode", trackingNumber);
            body.put("orderOrigin", config.getOrderOrigin() == null ? 1 : config.getOrderOrigin());
            body.put("customerCode", config.getCustomerCode());

            HttpHeaders headers = tokenService.buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            // JDL 部分接口除 Authorization 外，还要求透传 accessToken / appKey 头
            headers.set("accessToken", tokenService.getAccessToken());
            headers.set("appKey", config.getAppKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(url, entity, String.class);
            log.info("京东轨迹查询响应: {}", response);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String code = root.path("code").asText("");
            if (!"0".equals(code)) {
                result.setIsSuccess(false);
                result.setMessage("京东查询失败: " + root.path("msg").asText("未知错误"));
                return result;
            }

            JsonNode data = root.path("data");
            result.setIsSuccess(true);
            String statusDesc = data.path("statusDesc").asText("");
            if (statusDesc != null && !statusDesc.isBlank()) {
                result.setStatus(statusDesc);
            } else {
                result.setStatus(getJDStatusText(data.path("status").asText("")));
            }

            List<LogisticsTrace.TraceItem> traces = extractTraceItems(data);
            result.setTraces(traces);
            if ((result.getStatus() == null || result.getStatus().isBlank()) && !traces.isEmpty()) {
                result.setStatus(traces.get(0).getStatus());
            }
            return result;
        } catch (Exception e) {
            log.error("京东物流查询失败", e);
            result.setIsSuccess(false);
            result.setMessage("京东查询失败: " + e.getMessage());
            return result;
        }
    }

    private List<LogisticsTrace.TraceItem> extractTraceItems(JsonNode data) {
        List<LogisticsTrace.TraceItem> traces = new ArrayList<>();
        JsonNode arr = null;
        if (data.has("traceList")) arr = data.path("traceList");
        else if (data.has("tracks")) arr = data.path("tracks");
        else if (data.has("routeList")) arr = data.path("routeList");

        if (arr != null && arr.isArray()) {
            for (JsonNode t : arr) {
                LogisticsTrace.TraceItem item = new LogisticsTrace.TraceItem();
                item.setTime(firstNonBlank(
                        t.path("createTime").asText(""),
                        t.path("operateTime").asText(""),
                        t.path("time").asText("")
                ));
                item.setDesc(firstNonBlank(
                        t.path("msg").asText(""),
                        t.path("content").asText(""),
                        t.path("desc").asText("")
                ));
                item.setStatus(firstNonBlank(
                        t.path("statusDesc").asText(""),
                        getJDStatusText(t.path("status").asText("")),
                        "在途中"
                ));
                traces.add(item);
            }
        }
        return traces;
    }

    private String buildUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private String getJDStatusText(String status) {
        if (status == null) return "在途中";
        switch (status) {
            case "1":
                return "已揽收";
            case "2":
                return "在途中";
            case "3":
                return "派件中";
            case "4":
                return "已签收";
            case "5":
                return "退回中";
            case "510":
                return "已妥投";
            case "440":
                return "派送中";
            default:
                return "在途中";
        }
    }
}

