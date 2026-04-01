package com.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.SfExpressConfig;
import com.oms.entity.LogisticsTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.*;

/**
 * 顺丰速运 API 独立服务：路由查询(EXP_RECE_SEARCH_ROUTES)、服务网点查询(EXP_RECE_QUERY_GIS_DEPARTMENT)
 * 文档：https://qiao.sf-express.com/Api/ApiDetails?apiServiceCode=EXP_RECE_SEARCH_ROUTES
 */
@Slf4j
@Service
public class SfExpressApiService {

    private final SfExpressConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public SfExpressApiService(SfExpressConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /** 顺丰丰桥常用签名：Base64(MD5(msgData + timestamp + checkWord)) */
    public String signBase64(String msgData, long timestampMs) {
        try {
            String raw = msgData + timestampMs + config.getCheckword();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("顺丰签名失败", e);
        }
    }

    /** 历史兼容：32位大写 HEX（部分旧实现仍使用） */
    public String signHexUpper(String msgData, long timestampMs) {
        try {
            String raw = msgData + timestampMs + config.getCheckword();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("顺丰签名失败", e);
        }
    }

    /** 通用 POST：顺丰要求 application/x-www-form-urlencoded 表单，组装 partnerID、requestID、serviceCode、timestamp、msgData、msgDigest */
    public JsonNode post(String serviceCode, Map<String, Object> msgDataMap) throws Exception {
        String msgData = objectMapper.writeValueAsString(msgDataMap);
        long timestamp = System.currentTimeMillis();
        String url = config.getBaseUrl();

        // 优先走官方常用 Base64 签名；若返回 A1006（数字签名无效）再回退 HEX 签名，便于平滑兼容旧环境
        JsonNode first = doPost(serviceCode, msgData, timestamp, signBase64(msgData, timestamp), url);
        if (!"A1006".equals(first.path("apiResultCode").asText(""))) {
            return first;
        }
        log.warn("顺丰API[{}] 返回A1006，尝试回退HEX签名重试一次", serviceCode);
        return doPost(serviceCode, msgData, timestamp, signHexUpper(msgData, timestamp), url);
    }

    private JsonNode doPost(String serviceCode, String msgData, long timestamp, String msgDigest, String url) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("partnerID", config.getPartnerId());
        form.add("requestID", UUID.randomUUID().toString().replace("-", ""));
        form.add("serviceCode", serviceCode);
        form.add("timestamp", String.valueOf(timestamp));
        form.add("msgData", msgData);
        form.add("msgDigest", msgDigest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        String response = restTemplate.postForObject(url, entity, String.class);
        log.info("顺丰API[{}] 响应: {}", serviceCode, response);
        return objectMapper.readTree(response);
    }

    /** 路由查询 EXP_RECE_SEARCH_ROUTES，最多 10 个运单号；checkPhoneNo 收件人手机后四位 */
    public LogisticsTrace searchRoutes(String trackingNumber, String checkPhoneNo) {
        LogisticsTrace result = new LogisticsTrace();
        result.setCompany("顺丰速运");
        result.setTrackingNumber(trackingNumber);
        try {
            String phoneSuffix = "0000";
            if (checkPhoneNo != null && !checkPhoneNo.trim().isEmpty()) {
                String digits = checkPhoneNo.trim().replaceAll("\\D", "");
                phoneSuffix = digits.length() >= 4 ? digits.substring(digits.length() - 4)
                    : String.format("%4s", digits).replace(' ', '0');
            }
            // 与官方示例一致：language=zh-CN, trackingType=1, methodType=1；批量时 checkPhoneNo 为 "0001,0002" 形式
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("language", "zh-CN");
            msgData.put("trackingType", "1");
            msgData.put("trackingNumber", Collections.singletonList(trackingNumber));
            msgData.put("methodType", "1");
            msgData.put("checkPhoneNo", phoneSuffix);

            JsonNode root = post("EXP_RECE_SEARCH_ROUTES", msgData);
            String code = root.path("apiResultCode").asText("");
            String errMsg = root.path("apiErrorMsg").asText("");

            if (!"A1000".equals(code)) {
                result.setIsSuccess(false);
                result.setMessage("顺丰查询失败: " + errMsg + " (错误码: " + code + ")");
                return result;
            }

            result.setIsSuccess(true);
            List<LogisticsTrace.TraceItem> traces = new ArrayList<>();
            // 官方返回：业务结果在 apiResultData 字符串中，需再解析得到 msgData.routeResps
            JsonNode routeResps;
            if (root.has("apiResultData") && !root.path("apiResultData").asText("").isEmpty()) {
                String apiResultDataStr = root.path("apiResultData").asText();
                JsonNode inner = objectMapper.readTree(apiResultDataStr);
                routeResps = inner.path("msgData").path("routeResps");
            } else {
                JsonNode msgDataNode = root.path("msgData");
                routeResps = msgDataNode.path("routeResps");
            }
            if (routeResps.isArray()) {
                for (JsonNode routeResp : routeResps) {
                    JsonNode routes = routeResp.path("routes");
                    if (routes.isArray()) {
                        for (JsonNode route : routes) {
                            LogisticsTrace.TraceItem item = new LogisticsTrace.TraceItem();
                            item.setTime(route.path("acceptTime").asText(""));
                            item.setDesc(route.path("remark").asText(""));
                            item.setStatus(mapOpCodeToStatus(
                                    route.path("opCode").asText(""),
                                    route.path("remark").asText("")
                            ));
                            traces.add(item);
                        }
                    }
                }
            }
            Collections.reverse(traces);
            result.setTraces(traces);
            if (!traces.isEmpty()) result.setStatus(traces.get(0).getStatus());
        } catch (Exception e) {
            log.error("顺丰路由查询失败", e);
            result.setIsSuccess(false);
            result.setMessage("顺丰查询失败: " + e.getMessage());
        }
        return result;
    }

    /** 服务网点查询 EXP_RECE_QUERY_GIS_DEPARTMENT，按地址查周边网点；可选 x,y,opt,deptType,servType,distance */
    public Map<String, Object> queryGisDepartment(String address, String language,
                                                   String x, String y, String opt,
                                                   String deptType, String servType, Integer distance) {
        Map<String, Object> out = new HashMap<>();
        try {
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("language", language != null && !language.isEmpty() ? language : "0");
            if (address != null && !address.trim().isEmpty()) {
                msgData.put("address", address.trim());
            }
            if (x != null && !x.isEmpty()) msgData.put("x", x);
            if (y != null && !y.isEmpty()) msgData.put("y", y);
            if (opt != null && !opt.isEmpty()) msgData.put("opt", opt);
            if (deptType != null && !deptType.isEmpty()) msgData.put("deptType", deptType);
            if (servType != null && !servType.isEmpty()) msgData.put("servType", servType);
            if (distance != null) msgData.put("distance", distance);
            JsonNode root = post("EXP_RECE_QUERY_GIS_DEPARTMENT", msgData);
            String code = root.path("apiResultCode").asText("");
            String errMsg = root.path("apiErrorMsg").asText("");

            out.put("apiResultCode", code);
            out.put("apiErrorMsg", errMsg);
            out.put("success", "A1000".equals(code));
            if (root.has("msgData")) {
                out.put("msgData", objectMapper.convertValue(root.get("msgData"), Map.class));
            }
        } catch (Exception e) {
            log.error("顺丰网点查询失败", e);
            out.put("success", false);
            out.put("apiErrorMsg", e.getMessage());
        }
        return out;
    }

    private static String mapOpCodeToStatus(String opCode, String remark) {
        if (opCode == null) return "在途";
        String desc = remark == null ? "" : remark.trim();
        if (desc.contains("已签收") || desc.contains("本人签收")
                || desc.contains("已由本人签收")
                || desc.contains("派送至本人")
                || desc.contains("已派送至本人")
                || desc.contains("投递至本人")
                || desc.contains("已投递至本人")
                || desc.contains("已妥投")
                || desc.contains("已代收")) {
            return "已签收";
        }
        switch (opCode) {
            case "50": return "已签收";
            case "54": return "已揽收";
            case "56": return "派件中";
            case "80": return "派件中";
            case "70": return "到达网点";
            case "44": return "运输中";
            case "40": case "30": return "已揽收";
            case "655": return "待揽收"; // 合作点代收等
            default: return "在途";
        }
    }
}
