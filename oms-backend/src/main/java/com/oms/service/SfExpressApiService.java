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

    /** 顺丰丰桥要求：msgData + timestamp + checkword，MD5(UTF-8) 后转 Base64 字符串 */
    public String sign(String msgData, long timestamp) {
        try {
            String raw = msgData + timestamp + config.getCheckword();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            // Base64编码（与文档一致：MD5后转换为Base64字符串）
            String base64 = Base64.getEncoder().encodeToString(digest);
            return base64;
        } catch (Exception e) {
            throw new RuntimeException("顺丰签名失败", e);
        }
    }

    /** 通用 POST：顺丰要求 application/x-www-form-urlencoded 表单，组装 partnerID、requestID、serviceCode、timestamp、msgData、msgDigest */
    public JsonNode post(String serviceCode, Map<String, Object> msgDataMap) throws Exception {
        String msgData = objectMapper.writeValueAsString(msgDataMap);
        long timestamp = System.currentTimeMillis();
        String msgDigest = sign(msgData, timestamp);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("partnerID", config.getPartnerId());
        form.add("requestID", UUID.randomUUID().toString().replace("-", ""));
        form.add("serviceCode", serviceCode);
        form.add("timestamp", String.valueOf(timestamp));
        form.add("msgData", msgData);
        form.add("msgDigest", msgDigest);

        String url = config.getBaseUrl();
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
            // 尝试不同的查询参数，trackingType=1=速运类，methodType=2=查询所有（包括历史和最新）
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("language", "zh-CN");
            msgData.put("trackingType", "1");
            msgData.put("trackingNumber", Collections.singletonList(trackingNumber));
            msgData.put("methodType", "2");
            msgData.put("checkPhoneNo", phoneSuffix);
            // 添加月结卡号（解决 20028 错误：月结卡号不匹配）
            msgData.put("monthlyCard", config.getMonthlyCard());

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
                            item.setStatus(mapOpCodeToStatus(route.path("opCode").asText("")));
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

    private static String mapOpCodeToStatus(String opCode) {
        if (opCode == null) return "在途";
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
