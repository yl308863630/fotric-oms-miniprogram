package com.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.config.KuaiDi100Config;
import com.oms.entity.LogisticsTrace;
import com.oms.entity.SalesOrder;
import com.oms.repository.SalesOrderRepository;
import com.oms.util.LogisticsCompanyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
public class LogisticsService {

    @Autowired
    private KuaiDi100Config kuaiDi100Config;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SfExpressApiService sfExpressApiService;

    @Autowired
    private JdLogisticsClient jdLogisticsClient;

    private final RestTemplate restTemplate = new RestTemplate();

    public LogisticsTrace queryLogistics(String companyName, String trackingNumber, String checkPhoneNo) {
        LogisticsTrace result = new LogisticsTrace();
        result.setCompany(companyName);
        result.setTrackingNumber(trackingNumber);

        if (kuaiDi100Config.getEnableMock()) {
            return getMockData(companyName, trackingNumber);
        }

        try {
            if (LogisticsCompanyCode.isShunfeng(companyName)) {
                String phoneNo = checkPhoneNo;
                if ((phoneNo == null || phoneNo.trim().isEmpty()) && trackingNumber != null && !trackingNumber.trim().isEmpty()) {
                    String tn = trackingNumber.trim();
                    List<SalesOrder> orders = salesOrderRepository.findByTrackingNumber(tn);
                    if (!orders.isEmpty()) {
                        SalesOrder ord = orders.get(0);
                        String rp = ord.getReceiverPhone();
                        if (rp == null || rp.trim().isEmpty()) {
                            try {
                                String details = ord.getOrderDetails();
                                if (details != null && !details.isEmpty()) {
                                    JsonNode detailsNode = objectMapper.readTree(details);
                                    JsonNode logistics = detailsNode.path("logistics");
                                    if (logistics.isArray() && logistics.size() > 0) {
                                        String fromLogistics = logistics.get(0).path("receiverPhone").asText("");
                                        if (fromLogistics != null && !fromLogistics.trim().isEmpty()) rp = fromLogistics;
                                    }
                                }
                            } catch (Exception ignored) { }
                        }
                        if (rp != null && !rp.trim().isEmpty()) phoneNo = rp;
                    }
                    // 回单运单号存在 return_receipt_tracking_number，与母单不同；优先回单收件手机
                    if ((phoneNo == null || phoneNo.trim().isEmpty())) {
                        List<SalesOrder> retOrders = salesOrderRepository.findByReturnReceiptTrackingNumber(tn);
                        if (!retOrders.isEmpty()) {
                            SalesOrder ord = retOrders.get(0);
                            String rr = ord.getReturnReceiptReceiverPhone();
                            if (rr != null && !rr.trim().isEmpty()) {
                                phoneNo = rr;
                            } else {
                                String rp = ord.getReceiverPhone();
                                if (rp == null || rp.trim().isEmpty()) {
                                    try {
                                        String details = ord.getOrderDetails();
                                        if (details != null && !details.isEmpty()) {
                                            JsonNode detailsNode = objectMapper.readTree(details);
                                            JsonNode logistics = detailsNode.path("logistics");
                                            if (logistics.isArray() && logistics.size() > 0) {
                                                String fromLogistics = logistics.get(0).path("receiverPhone").asText("");
                                                if (fromLogistics != null && !fromLogistics.trim().isEmpty()) rp = fromLogistics;
                                            }
                                        }
                                    } catch (Exception ignored) { }
                                }
                                if (rp != null && !rp.trim().isEmpty()) phoneNo = rp;
                            }
                        }
                    }
                }
                return sfExpressApiService.searchRoutes(trackingNumber, phoneNo);
            } else if (LogisticsCompanyCode.isJD(companyName)) {
                LogisticsTrace jdResult = jdLogisticsClient.queryOrderStatus(companyName, trackingNumber);
                boolean hasOnlineTraces = jdResult != null
                        && Boolean.TRUE.equals(jdResult.getIsSuccess())
                        && jdResult.getTraces() != null
                        && !jdResult.getTraces().isEmpty();
                if (hasOnlineTraces) {
                    return jdResult;
                }

                LogisticsTrace pushed = queryPushedJdTraces(companyName, trackingNumber);
                if (pushed != null) {
                    if (jdResult != null && jdResult.getMessage() != null && !jdResult.getMessage().isBlank()) {
                        pushed.setMessage(jdResult.getMessage() + "；已回退显示京东推送轨迹");
                    }
                    return pushed;
                }
                return jdResult;
            } else {
                return queryKuaiDi100(companyName, trackingNumber);
            }
        } catch (Exception e) {
            log.error("物流查询失败", e);
            result.setIsSuccess(false);
            result.setMessage("查询失败: " + e.getMessage());
            return result;
        }
    }

    private LogisticsTrace queryKuaiDi100(String companyName, String trackingNumber) throws Exception {
        LogisticsTrace result = new LogisticsTrace();
        result.setCompany(companyName);
        result.setTrackingNumber(trackingNumber);

        String companyCode = LogisticsCompanyCode.getCode(companyName);
        if (companyCode.isEmpty()) {
            result.setIsSuccess(false);
            result.setMessage("不支持的物流公司: " + companyName);
            return result;
        }

        Map<String, String> params = new HashMap<>();
        params.put("com", companyCode);
        params.put("num", trackingNumber);
        params.put("phone", "");
        params.put("from", "");
        params.put("to", "");
        params.put("resultv2", "1");
        params.put("show", "0");
        params.put("order", "desc");

        String paramJson = objectMapper.writeValueAsString(params);
        String sign = encrypt(paramJson + kuaiDi100Config.getAppKey(), kuaiDi100Config.getCustomer());

        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("customer", kuaiDi100Config.getCustomer());
        requestParams.put("sign", sign);
        requestParams.put("param", paramJson);

        String response = restTemplate.postForObject(kuaiDi100Config.getApiUrl(), requestParams, String.class);
        log.info("快递100API响应: {}", response);

        JsonNode root = objectMapper.readTree(response);
        String status = root.path("status").asText("");

        if ("200".equals(status)) {
            result.setIsSuccess(true);
            JsonNode dataNode = root.path("data");
            List<LogisticsTrace.TraceItem> traces = new ArrayList<>();

            if (dataNode.isArray()) {
                for (JsonNode traceNode : dataNode) {
                    LogisticsTrace.TraceItem item = new LogisticsTrace.TraceItem();
                    item.setTime(traceNode.path("time").asText(""));
                    item.setDesc(traceNode.path("context").asText(""));
                    item.setStatus(getStatusText(traceNode.path("status").asText("")));
                    traces.add(item);
                }
            }

            result.setTraces(traces);
            
            if (!traces.isEmpty()) {
                result.setStatus(traces.get(0).getStatus());
            }
        } else {
            result.setIsSuccess(false);
            result.setMessage(root.path("message").asText("查询失败"));
        }

        return result;
    }

    private LogisticsTrace queryJD(String companyName, String trackingNumber) {
        LogisticsTrace result = new LogisticsTrace();
        result.setCompany(companyName);
        result.setTrackingNumber(trackingNumber);

        try {
            String appKey = kuaiDi100Config.getJdAppKey();
            String appSecret = kuaiDi100Config.getJdAppSecret();
            String apiUrl = kuaiDi100Config.getJdApiUrl();

            String method = "jingdong.ldop.alpha.trace.query";
            String timestamp = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(java.time.LocalDateTime.now());
            String format = "json";
            String v = "2.0";

            Map<String, String> params = new TreeMap<>();
            params.put("method", method);
            params.put("app_key", appKey);
            params.put("timestamp", timestamp);
            params.put("format", format);
            params.put("v", v);
            params.put("waybillCode", trackingNumber);

            StringBuilder signStr = new StringBuilder(appSecret);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                signStr.append(entry.getKey()).append(entry.getValue());
            }
            signStr.append(appSecret);
            
            String sign = encryptMD5(signStr.toString()).toUpperCase();
            params.put("sign", sign);

            StringBuilder urlBuilder = new StringBuilder(apiUrl);
            urlBuilder.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey()).append("=")
                    .append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()))
                    .append("&");
            }
            
            String url = urlBuilder.toString();
            if (url.endsWith("&")) {
                url = url.substring(0, url.length() - 1);
            }

            String response = restTemplate.getForObject(url, String.class);
            log.info("京东API响应: {}", response);

            JsonNode root = objectMapper.readTree(response);
            JsonNode resultNode = root.path("jingdong_ldop_alpha_trace_query_response");
            JsonNode dataNode = resultNode.path("result");
            
            if (dataNode.has("code") && "0".equals(dataNode.path("code").asText())) {
                result.setIsSuccess(true);
                JsonNode traceListNode = dataNode.path("data").path("traceList");
                
                List<LogisticsTrace.TraceItem> traces = new ArrayList<>();
                
                if (traceListNode.isArray()) {
                    for (JsonNode trace : traceListNode) {
                        LogisticsTrace.TraceItem item = new LogisticsTrace.TraceItem();
                        item.setTime(trace.path("createTime").asText(""));
                        item.setDesc(trace.path("msg").asText(""));
                        item.setStatus(getJDStatusText(trace.path("status").asText("")));
                        traces.add(item);
                    }
                }
                
                result.setTraces(traces);
                
                if (!traces.isEmpty()) {
                    result.setStatus(traces.get(0).getStatus());
                }
            } else {
                result.setIsSuccess(false);
                result.setMessage("京东查询失败: " + dataNode.path("msg").asText("未知错误"));
            }
        } catch (Exception e) {
            log.error("京东物流查询失败", e);
            result.setIsSuccess(false);
            result.setMessage("京东查询失败: " + e.getMessage());
        }

        return result;
    }

    private String getStatusText(String status) {
        if ("0".equals(status)) {
            return "在途中";
        } else if ("1".equals(status)) {
            return "已揽收";
        } else if ("2".equals(status)) {
            return "疑难件";
        } else if ("3".equals(status)) {
            return "已签收";
        } else if ("4".equals(status)) {
            return "已退签";
        } else if ("5".equals(status)) {
            return "派件中";
        } else if ("6".equals(status)) {
            return "退回中";
        } else {
            return "在途中";
        }
    }

    private String getJDStatusText(String status) {
        if (status == null) return "在途中";
        switch (status) {
            case "1": return "已揽收";
            case "2": return "在途中";
            case "3": return "派件中";
            case "4": return "已签收";
            case "5": return "退回中";
            default: return "在途中";
        }
    }

    private String encrypt(String data, String key) throws Exception {
        String combined = data + key;
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest).toUpperCase();
    }

    private String encryptSF(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest).toUpperCase();
    }

    private String encryptMD5(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private LogisticsTrace getMockData(String companyName, String trackingNumber) {
        LogisticsTrace result = new LogisticsTrace();
        result.setCompany(companyName);
        result.setTrackingNumber(trackingNumber);
        result.setIsSuccess(true);
        result.setStatus("已签收");

        List<LogisticsTrace.TraceItem> traces = new ArrayList<>();
        
        LogisticsTrace.TraceItem item1 = new LogisticsTrace.TraceItem();
        item1.setTime("2025-11-14 17:57:35");
        item1.setStatus("已签收");
        item1.setDesc("您的包裹已签收，感谢您使用" + companyName + "，期待下一次为您服务！您专心工作，琐事我来做！");
        traces.add(item1);

        LogisticsTrace.TraceItem item2 = new LogisticsTrace.TraceItem();
        item2.setTime("2025-11-14 10:03:35");
        item2.setStatus("派件中");
        item2.setDesc("您的订单已由本人签收。感谢您在本平台购物，欢迎再次光临。");
        traces.add(item2);

        LogisticsTrace.TraceItem item3 = new LogisticsTrace.TraceItem();
        item3.setTime("2025-11-14 17:57:05");
        item3.setStatus("派件中");
        item3.setDesc("您的包裹正在派送中，请保持电话畅通以便司机联系，收到货请清点无误后请完成签收(派件人: 宋敏 电话: 18725533183)");
        traces.add(item3);

        LogisticsTrace.TraceItem item4 = new LogisticsTrace.TraceItem();
        item4.setTime("2025-11-14 09:56:14");
        item4.setStatus("在途中");
        item4.setDesc("您的订单已拣货打包完成，订单正在配送途中，请您耐心等待");
        traces.add(item4);

        LogisticsTrace.TraceItem item5 = new LogisticsTrace.TraceItem();
        item5.setTime("2025-11-10 21:10:47");
        item5.setStatus("已揽收");
        item5.setDesc("您的包裹由【集团控股】网点完成揽收，等待物流运输。");
        traces.add(item5);

        LogisticsTrace.TraceItem item6 = new LogisticsTrace.TraceItem();
        item6.setTime("2025-11-10 17:57:02");
        item6.setStatus("已揽收");
        item6.setDesc("您的包裹由【" + companyName + "】网点完成揽收，等待物流运输。");
        traces.add(item6);

        result.setTraces(traces);
        return result;
    }

    /** 京东实时查询失败时，回退到 order_details.logistics 中的 JD_PUSH 轨迹。 */
    private LogisticsTrace queryPushedJdTraces(String companyName, String trackingNumber) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            return null;
        }

        List<SalesOrder> candidates = new ArrayList<>();
        candidates.addAll(salesOrderRepository.findByTrackingNumber(trackingNumber.trim()));
        candidates.addAll(salesOrderRepository.findByReturnReceiptTrackingNumber(trackingNumber.trim()));
        if (candidates.isEmpty()) {
            return null;
        }

        for (SalesOrder so : candidates) {
            try {
                String detailsRaw = so.getOrderDetails();
                if (detailsRaw == null || detailsRaw.isBlank()) {
                    continue;
                }
                JsonNode details = objectMapper.readTree(detailsRaw);
                JsonNode logistics = details.path("logistics");
                if (!logistics.isArray() || logistics.size() == 0) {
                    continue;
                }

                List<LogisticsTrace.TraceItem> traces = new ArrayList<>();
                for (JsonNode node : logistics) {
                    String source = node.path("source").asText("");
                    if (!source.isBlank() && !"JD_PUSH".equalsIgnoreCase(source)) {
                        continue;
                    }
                    String desc = firstNonBlank(
                            node.path("desc").asText(""),
                            node.path("remark").asText(""),
                            node.path("content").asText("")
                    );
                    // 调试压测产生的“test/测试”文案不展示到生产物流轨迹
                    if ("test".equalsIgnoreCase(desc.trim()) || "测试".equals(desc.trim())) {
                        continue;
                    }
                    String status = firstNonBlank(
                            node.path("statusDesc").asText(""),
                            node.path("status").asText(""),
                            toStatusText(
                                    firstNonBlank(node.path("operationCode").asText(""), node.path("status").asText("")),
                                    node.path("operationType").asText(""),
                                    desc
                            )
                    );
                    LogisticsTrace.TraceItem item = new LogisticsTrace.TraceItem();
                    item.setTime(firstNonBlank(
                            node.path("time").asText(""),
                            node.path("operationTime").asText(""),
                            node.path("createTime").asText("")
                    ));
                    item.setStatus(status);
                    item.setDesc(desc);
                    traces.add(item);
                }

                if (!traces.isEmpty()) {
                    LogisticsTrace result = new LogisticsTrace();
                    result.setCompany(companyName);
                    result.setTrackingNumber(trackingNumber);
                    result.setTraces(traces);
                    result.setStatus(traces.get(0).getStatus());
                    result.setIsSuccess(true);
                    return result;
                }
            } catch (Exception ex) {
                log.warn("读取京东推送轨迹回退数据失败, orderId={}", so.getId(), ex);
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private String toStatusText(String operationCode, String operationType, String desc) {
        String code = operationCode == null ? "" : operationCode.trim();
        String type = operationType == null ? "" : operationType.trim();
        if ("1001".equals(type)) return "已揽收";
        if ("2003".equals(type)) return "运输中";
        if ("3001".equals(type) || "3002".equals(type)) return "派送中";
        if ("4001".equals(type) || "5003".equals(type) || "510".equals(code)) return "已签收";
        if ("5001".equals(type) || "5002".equals(type)) return "异常";
        if (desc != null && (desc.contains("已由本人签收")
                || desc.contains("本人签收")
                || desc.contains("派送至本人")
                || desc.contains("已派送至本人")
                || desc.contains("投递至本人")
                || desc.contains("已投递至本人")
                || desc.contains("已妥投")
                || desc.contains("已代收"))) {
            return "已签收";
        }
        if (desc != null && desc.contains("签收")) return "已签收";
        if (desc != null && (desc.contains("派送") || desc.contains("派件"))) return "派送中";
        if (desc != null && (desc.contains("运输") || desc.contains("发往下一站") || desc.contains("到达"))) return "运输中";
        return "在途中";
    }
}
