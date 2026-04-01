package com.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oms.entity.SalesOrder;
import com.oms.repository.SalesOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class JdTrackingPushService {

    private final SalesOrderRepository salesOrderRepository;
    private final ObjectMapper objectMapper;
    private final DeliveryReceiptFlowService deliveryReceiptFlowService;

    public JdTrackingPushService(SalesOrderRepository salesOrderRepository,
                                 ObjectMapper objectMapper,
                                 DeliveryReceiptFlowService deliveryReceiptFlowService) {
        this.salesOrderRepository = salesOrderRepository;
        this.objectMapper = objectMapper;
        this.deliveryReceiptFlowService = deliveryReceiptFlowService;
    }

    @Transactional
    public int handlePush(JsonNode root) {
        if (root == null || root.isMissingNode()) return 0;

        JsonNode payload = root.path("payload");
        JsonNode data = payload.isMissingNode() ? root : payload;

        String waybillNo = text(data, "waybillNo");
        String customerOrderNo = text(data, "customerOrderNo");
        String orderNo = text(data, "orderNo");

        List<SalesOrder> targets = findTargets(waybillNo, customerOrderNo, orderNo);
        if (targets.isEmpty()) {
            log.warn("京东轨迹推送未匹配到订单: waybillNo={}, customerOrderNo={}, orderNo={}", waybillNo, customerOrderNo, orderNo);
            return 0;
        }

        for (SalesOrder so : targets) {
            if (isBlank(so.getLogisticsCompany())) {
                so.setLogisticsCompany("京东物流");
            }
            if (isBlank(so.getTrackingNumber()) && !isBlank(waybillNo)) {
                so.setTrackingNumber(waybillNo);
            }
            appendLogistics(so, data);
        }
        salesOrderRepository.saveAll(targets);
        for (SalesOrder so : targets) {
            deliveryReceiptFlowService.refreshOrderFlowById(so.getId());
        }
        return targets.size();
    }

    private List<SalesOrder> findTargets(String waybillNo, String customerOrderNo, String orderNo) {
        Set<Long> seen = new LinkedHashSet<>();
        List<SalesOrder> result = new ArrayList<>();

        if (!isBlank(waybillNo)) {
            addAll(result, seen, salesOrderRepository.findByTrackingNumber(waybillNo));
            addAll(result, seen, salesOrderRepository.findByReturnReceiptTrackingNumber(waybillNo));
        }
        if (!isBlank(customerOrderNo)) {
            addAll(result, seen, salesOrderRepository.findByPlatformOrderNo(customerOrderNo));
            salesOrderRepository.findByOmsOrderNo(customerOrderNo).ifPresent(so -> addOne(result, seen, so));
        }
        if (!isBlank(orderNo)) {
            addAll(result, seen, salesOrderRepository.findByOrderNo(orderNo));
            addAll(result, seen, salesOrderRepository.findByPlatformOrderNo(orderNo));
            salesOrderRepository.findByOmsOrderNo(orderNo).ifPresent(so -> addOne(result, seen, so));
        }
        return result;
    }

    private void addAll(List<SalesOrder> out, Set<Long> seen, List<SalesOrder> list) {
        if (list == null) return;
        for (SalesOrder so : list) addOne(out, seen, so);
    }

    private void addOne(List<SalesOrder> out, Set<Long> seen, SalesOrder so) {
        if (so == null || so.getId() == null) return;
        if (seen.add(so.getId())) out.add(so);
    }

    private void appendLogistics(SalesOrder so, JsonNode data) {
        ObjectNode details;
        try {
            String raw = so.getOrderDetails();
            if (isBlank(raw)) {
                details = objectMapper.createObjectNode();
            } else {
                JsonNode parsed = objectMapper.readTree(raw);
                details = parsed != null && parsed.isObject()
                        ? (ObjectNode) parsed
                        : objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            details = objectMapper.createObjectNode();
        }

        ArrayNode logistics = details.has("logistics") && details.get("logistics").isArray()
                ? (ArrayNode) details.get("logistics")
                : details.putArray("logistics");

        ObjectNode item = objectMapper.createObjectNode();
        String operationCode = text(data, "operationCode");
        String operationType = text(data, "operationType");
        String remark = text(data, "remark");
        String statusText = toStatusText(operationCode, operationType, remark);
        item.put("source", "JD_PUSH");
        item.put("time", text(data, "operationTime"));
        item.put("status", statusText);
        item.put("statusDesc", statusText);
        item.put("operationCode", operationCode);
        item.put("operationType", operationType);
        item.put("desc", remark);
        item.put("waybillNo", text(data, "waybillNo"));
        item.put("operateSite", text(data, "operateSite"));
        item.put("operatorName", text(data, "operatorName"));

        // 去重：同时间+同操作码+同描述不重复入库
        String sign = item.path("time").asText("") + "|" + item.path("operationCode").asText("") + "|" + item.path("desc").asText("");
        for (JsonNode old : logistics) {
            String oldSign = old.path("time").asText("") + "|" + old.path("operationCode").asText("") + "|" + old.path("desc").asText("");
            if (sign.equals(oldSign)) {
                return;
            }
        }
        logistics.insert(0, item);
        while (logistics.size() > 200) {
            logistics.remove(logistics.size() - 1);
        }
        try {
            so.setOrderDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            log.warn("写入JD轨迹到orderDetails失败, orderId={}", so.getId(), e);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) return "";
        String v = node.path(field).asText("");
        return v == null ? "" : v.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String toStatusText(String operationCode, String operationType, String remark) {
        String code = operationCode == null ? "" : operationCode.trim();
        String type = operationType == null ? "" : operationType.trim();
        if ("1001".equals(type)) return "已揽收";
        if ("2003".equals(type)) return "运输中";
        if ("3001".equals(type) || "3002".equals(type)) return "派送中";
        if ("4001".equals(type) || "5003".equals(type) || "510".equals(code)) return "已签收";
        if ("5001".equals(type) || "5002".equals(type)) return "异常";
        if (remark != null && remark.contains("签收")) return "已签收";
        if (remark != null && (remark.contains("派送") || remark.contains("派件"))) return "派送中";
        if (remark != null && (remark.contains("运输") || remark.contains("发往下一站") || remark.contains("到达"))) return "运输中";
        return "在途中";
    }
}

