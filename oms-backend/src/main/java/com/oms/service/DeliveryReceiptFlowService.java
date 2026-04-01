package com.oms.service;

import com.oms.entity.LogisticsTrace;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
public class DeliveryReceiptFlowService {
    private static final String FEICHUKE_COMPANY_FULL = "飞础科智慧科技（上海）有限公司";
    private static final List<String> DELIVERED_KEYWORDS = List.of(
            "已送达",
            "派送成功",
            "已派送成功",
            "已妥投",
            "已签收",
            "本人签收",
            "已由本人签收",
            "派送至本人",
            "已派送至本人",
            "投递至本人",
            "已投递至本人",
            "已代收"
    );
    private static final String DEFAULT_RETURN_COMPANY = "顺丰速运";

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private LogisticsService logisticsService;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private UserRepository userRepository;

    @Value("${oms.notifications.return-delivered-at-mobiles:}")
    private String returnDeliveredAtMobiles;

    @Value("${oms.notifications.mother-delivered-extra-at-real-names:张敏芳}")
    private String motherDeliveredExtraAtRealNames;

    @Transactional
    public void refreshOrderFlowById(Long orderId) {
        if (orderId == null) return;
        salesOrderRepository.findById(orderId).ifPresent(this::refreshOrderFlow);
    }

    @Transactional
    public void refreshOrderFlow(SalesOrder order) {
        if (order == null || order.getId() == null) return;
        if (!SalesOrderReceiptFlowHelper.isShippedStatus(order.getStatus())) return;

        String currentStage = SalesOrderReceiptFlowHelper.normalizeStage(order.getReceiptFlowStage());
        String normalizedStage = SalesOrderReceiptFlowHelper.determineReceiptFlowStage(order);
        boolean changed = !equalsNullable(currentStage, normalizedStage);
        if (changed) {
            order.setReceiptFlowStage(normalizedStage);
        }

        if (SalesOrderReceiptFlowHelper.hasUploadedReceipt(order)) {
            saveIfChanged(order, changed);
            return;
        }

        if (SalesOrderReceiptFlowHelper.isSelfVehicleDelivery(order.getDeliveryMethod())) {
            saveIfChanged(order, changed);
            return;
        }

        String trackingNumber = trim(order.getTrackingNumber());
        String logisticsCompany = trim(order.getLogisticsCompany());
        if (trackingNumber.isEmpty() || logisticsCompany.isEmpty()) {
            saveIfChanged(order, changed);
            return;
        }

        LogisticsTrace motherTrace = logisticsService.queryLogistics(logisticsCompany, trackingNumber, trim(order.getReceiverPhone()));
        if (!traceIndicatesDelivered(motherTrace)) {
            saveIfChanged(order, changed);
            return;
        }

        if (Boolean.TRUE.equals(order.getNeedReceiptSlip())) {
            if (SalesOrderReceiptFlowHelper.stageRank(order.getReceiptFlowStage())
                    < SalesOrderReceiptFlowHelper.stageRank(SalesOrderReceiptFlowHelper.STAGE_MOTHER_DELIVERED)) {
                order.setReceiptFlowStage(SalesOrderReceiptFlowHelper.STAGE_MOTHER_DELIVERED);
                if (order.getMotherDeliveredAt() == null) {
                    order.setMotherDeliveredAt(LocalDateTime.now());
                }
                changed = true;
                notifyStageChanged(order, "母单已送达", trackingNumber,
                        "订单已进入“已到货待回单”，请关注回单物流并及时上传签收单。");
            }

            String returnTrackingNumber = trim(order.getReturnReceiptTrackingNumber());
            if (returnTrackingNumber.isEmpty()) {
                saveIfChanged(order, changed);
                return;
            }

            String returnLogisticsCompany = logisticsCompany.isEmpty() ? DEFAULT_RETURN_COMPANY : logisticsCompany;
            LogisticsTrace returnTrace = logisticsService.queryLogistics(
                    returnLogisticsCompany,
                    returnTrackingNumber,
                    trim(order.getReturnReceiptReceiverPhone())
            );
            if (traceIndicatesDelivered(returnTrace)
                    && SalesOrderReceiptFlowHelper.stageRank(order.getReceiptFlowStage())
                    < SalesOrderReceiptFlowHelper.stageRank(SalesOrderReceiptFlowHelper.STAGE_RETURN_DELIVERED)) {
                order.setReceiptFlowStage(SalesOrderReceiptFlowHelper.STAGE_RETURN_DELIVERED);
                if (order.getReturnDeliveredAt() == null) {
                    order.setReturnDeliveredAt(LocalDateTime.now());
                }
                changed = true;
                notifyStageChanged(order, "回单已送达", returnTrackingNumber,
                        "订单已进入“已回单待上传签收单”，请尽快上传签收单完成闭环。");
            }
        } else {
            if (SalesOrderReceiptFlowHelper.stageRank(order.getReceiptFlowStage())
                    < SalesOrderReceiptFlowHelper.stageRank(SalesOrderReceiptFlowHelper.STAGE_FLOW_COMPLETED)) {
                order.setReceiptFlowStage(SalesOrderReceiptFlowHelper.STAGE_FLOW_COMPLETED);
                changed = true;
                notifyStageChanged(order, "母单已妥投", trackingNumber,
                        "该订单无需签收单，已按物流送达自动进入“妥投结束”。");
            }
        }

        saveIfChanged(order, changed);
    }

    public boolean traceIndicatesDelivered(LogisticsTrace trace) {
        if (trace == null) {
            return false;
        }
        String topStatus = trim(trace.getStatus());
        if ("已签收".equals(topStatus) || "已妥投".equals(topStatus)) {
            return true;
        }
        if (trace.getTraces() == null || trace.getTraces().isEmpty()) {
            return false;
        }
        for (LogisticsTrace.TraceItem item : trace.getTraces()) {
            String combined = trim(item.getStatus()) + " " + trim(item.getDesc());
            for (String keyword : DELIVERED_KEYWORDS) {
                if (combined.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Scheduled(cron = "${oms.receipt-flow.refresh-cron:0 */15 * * * ?}")
    @Transactional
    public void refreshShippedOrdersFlow() {
        Specification<SalesOrder> baseSpec = SalesOrderReceiptFlowHelper.shippedSpec()
                .and(SalesOrderReceiptFlowHelper.nonSelfVehicleSpec())
                .and((root, query, cb) -> cb.and(
                        cb.isNotNull(root.get("trackingNumber")),
                        cb.notEqual(root.get("trackingNumber"), "")
                ))
                .and(SalesOrderReceiptFlowHelper.notSpec(SalesOrderReceiptFlowHelper.completedSpec()));
        List<SalesOrder> orders = salesOrderRepository.findAll(baseSpec);
        for (SalesOrder order : orders) {
            try {
                refreshOrderFlow(order);
            } catch (Exception ex) {
                log.warn("刷新签收流转失败, orderId={}", order.getId(), ex);
            }
        }
    }

    private void saveIfChanged(SalesOrder order, boolean changed) {
        if (changed) {
            salesOrderRepository.save(order);
        }
    }

    private void notifyStageChanged(SalesOrder order, String title, String trackingNumber, String summary) {
        try {
            String stageText = resolveStageLabel(order.getReceiptFlowStage());
            String trackingLabel = resolveTrackingLabel(order.getReceiptFlowStage(), title);
            String summaryWithTrackingContext = appendTrackingContextToSummary(order, summary, trackingLabel);
            List<String> atMobiles = resolveAtMobilesForStage(order);
            String text = String.format(
                    "## %s\n\n**OMS订单号：** %s\n\n**%s：** %s\n\n**当前阶段：** %s\n\n**说明：** %s\n\n---\n*来自 OMS 订单系统*",
                    title,
                    trim(order.getOmsOrderNo()),
                    trackingLabel,
                    trim(trackingNumber),
                    stageText,
                    summaryWithTrackingContext
            );
            dingTalkService.sendMarkdownMessage(title, text, atMobiles.isEmpty() ? null : atMobiles);
        } catch (Exception ex) {
            log.warn("发送签收流转钉钉通知失败, orderId={}", order.getId(), ex);
        }
    }

    private String resolveStageLabel(String stage) {
        String normalized = SalesOrderReceiptFlowHelper.normalizeStage(stage);
        if (normalized == null || normalized.isBlank()) {
            return "-";
        }
        return switch (normalized) {
            case SalesOrderReceiptFlowHelper.STAGE_PENDING_MOTHER -> "待母单签收";
            case SalesOrderReceiptFlowHelper.STAGE_MOTHER_DELIVERED -> "已到货待回单";
            case SalesOrderReceiptFlowHelper.STAGE_RETURN_DELIVERED -> "已回单待上传签收单";
            case SalesOrderReceiptFlowHelper.STAGE_SELF_VEHICLE_PENDING_RECEIPT -> "自主车辆待签收单";
            case SalesOrderReceiptFlowHelper.STAGE_FLOW_COMPLETED -> "妥投结束";
            default -> normalized;
        };
    }

    private String resolveTrackingLabel(String stage, String title) {
        String normalized = SalesOrderReceiptFlowHelper.normalizeStage(stage);
        if (SalesOrderReceiptFlowHelper.STAGE_RETURN_DELIVERED.equals(normalized) || trim(title).contains("回单")) {
            return "回单物流单号";
        }
        return "母单物流单号";
    }

    private String appendTrackingContextToSummary(SalesOrder order, String summary, String primaryTrackingLabel) {
        String base = trim(summary);
        String motherTrackingNumber = trim(order != null ? order.getTrackingNumber() : null);
        String returnTrackingNumber = trim(order != null ? order.getReturnReceiptTrackingNumber() : null);
        List<String> extraParts = new java.util.ArrayList<>();
        if (!"母单物流单号".equals(primaryTrackingLabel) && !motherTrackingNumber.isEmpty()) {
            extraParts.add("母单物流单号：" + motherTrackingNumber);
        }
        if (!"回单物流单号".equals(primaryTrackingLabel) && !returnTrackingNumber.isEmpty()) {
            extraParts.add("回单物流单号：" + returnTrackingNumber);
        }
        if (extraParts.isEmpty()) return base;
        String extra = String.join("；", extraParts);
        if (base.isEmpty()) return extra;
        return base + " " + extra;
    }

    private List<String> resolveAtMobilesForStage(SalesOrder order) {
        String normalized = SalesOrderReceiptFlowHelper.normalizeStage(order != null ? order.getReceiptFlowStage() : null);
        if (SalesOrderReceiptFlowHelper.STAGE_MOTHER_DELIVERED.equals(normalized)) {
            LinkedHashSet<String> mobiles = new LinkedHashSet<>();
            mobiles.addAll(resolveFeichukeSalesOwnerAtMobiles(order));
            mobiles.addAll(resolveConfiguredUsersAtMobiles(motherDeliveredExtraAtRealNames));
            return mobiles.isEmpty() ? java.util.Collections.emptyList() : new java.util.ArrayList<>(mobiles);
        }
        if (!SalesOrderReceiptFlowHelper.STAGE_RETURN_DELIVERED.equals(normalized)) {
            return java.util.Collections.emptyList();
        }
        return parseConfiguredMobiles(returnDeliveredAtMobiles);
    }

    private List<String> resolveFeichukeSalesOwnerAtMobiles(SalesOrder order) {
        if (order == null) {
            return java.util.Collections.emptyList();
        }
        String omsOrderNo = trim(order.getOmsOrderNo());
        List<SalesOrder> relatedOrders = omsOrderNo.isEmpty()
                ? java.util.List.of(order)
                : salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(omsOrderNo);
        LinkedHashSet<String> mobiles = new LinkedHashSet<>();
        for (User user : userRepository.findAll()) {
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                continue;
            }
            String companyTitle = trim(user.getCompanyTitle());
            if (!companyTitle.contains(FEICHUKE_COMPANY_FULL)) {
                continue;
            }
            if (!matchesOmsSalesOwner(user, relatedOrders)) {
                continue;
            }
            String phone = trim(user.getPhone());
            if (!phone.isEmpty()) {
                mobiles.add(phone);
            }
        }
        return mobiles.isEmpty() ? java.util.Collections.emptyList() : new java.util.ArrayList<>(mobiles);
    }

    private boolean matchesOmsSalesOwner(User user, List<SalesOrder> relatedOrders) {
        if (user == null || relatedOrders == null || relatedOrders.isEmpty()) {
            return false;
        }
        String realName = trim(user.getRealName());
        String username = trim(user.getUsername());
        Long userId = user.getId();
        for (SalesOrder item : relatedOrders) {
            if (item == null) {
                continue;
            }
            if (userId != null && userId.equals(item.getCreatedBy())) {
                return true;
            }
            String salesName = trim(item.getEcommerceSalesName());
            if (!salesName.isEmpty() && (salesName.equals(realName) || salesName.equals(username))) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveConfiguredUsersAtMobiles(String rawRealNames) {
        LinkedHashSet<String> mobiles = new LinkedHashSet<>();
        for (String realName : parseConfiguredMobiles(rawRealNames)) {
            for (User user : userRepository.findByRealName(realName)) {
                if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                    continue;
                }
                String phone = trim(user.getPhone());
                if (!phone.isEmpty()) {
                    mobiles.add(phone);
                }
            }
        }
        return mobiles.isEmpty() ? java.util.Collections.emptyList() : new java.util.ArrayList<>(mobiles);
    }

    private List<String> parseConfiguredMobiles(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return Arrays.stream(raw.split("[,，]"))
                .map(this::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) return right == null;
        return left.equals(right);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
