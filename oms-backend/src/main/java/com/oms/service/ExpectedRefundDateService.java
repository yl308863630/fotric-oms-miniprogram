package com.oms.service;

import com.oms.entity.PartyAPaymentRule;
import com.oms.entity.SalesOrder;
import com.oms.repository.PartyAPaymentRuleRepository;
import com.oms.repository.SalesOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ExpectedRefundDateService {
    @Autowired
    private PartyAPaymentRuleRepository partyAPaymentRuleRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    public void populateTransientFields(SalesOrder order) {
        if (order == null) {
            return;
        }
        ExpectedRefundCalculationResult result = calculate(order);
        applyCalculationResult(order, result);
    }

    public void refreshExpectedRefundFields(SalesOrder order) {
        if (order == null) {
            return;
        }
        ExpectedRefundCalculationResult result = calculate(order);
        order.setExpectedRefundDate(result.expectedRefundDate());
        order.setExpectedRefundRuleDescription(result.ruleDescription());
        order.setExpectedRefundPendingReason(result.pendingReason());
        order.setExpectedRefundOverdue(isOverdue(order));
    }

    public ExpectedRefundCalculationResult calculate(SalesOrder order) {
        if (order == null) {
            return ExpectedRefundCalculationResult.unmatched();
        }
        PartyAPaymentRule rule = findMatchingRule(order);
        if (rule == null) {
            return ExpectedRefundCalculationResult.unmatched();
        }
        String ruleText = buildRuleDescription(rule);
        LocalDate baseDate = resolveBaseDate(order, rule);
        if (baseDate == null) {
            return ExpectedRefundCalculationResult.pending(ruleText, buildMissingBaseReason(rule));
        }

        LocalDate shiftedDate = baseDate.plusDays(Math.max(0, rule.getOffsetDays() == null ? 0 : rule.getOffsetDays()));
        String anchorType = normalize(rule.getPaymentAnchorType());
        return switch (anchorType) {
            case "FIXED_DAY_OF_NEXT_MONTH" -> {
                Integer fixedDay = sanitizeDay(rule.getAnchorDay1());
                if (fixedDay == null) {
                    yield ExpectedRefundCalculationResult.pending(ruleText, "规则缺少付款固定日");
                }
                LocalDate expectedDate = clampToMonth(shiftedDate.plusMonths(1), fixedDay);
                yield ExpectedRefundCalculationResult.resolved(expectedDate, ruleText);
            }
            case "INTERVAL_DAY_BUCKET" -> {
                Integer firstDay = sanitizeDay(rule.getAnchorDay1());
                Integer secondDay = sanitizeDay(rule.getAnchorDay2());
                Integer nextMonthDay = sanitizeDay(rule.getAnchorDay3());
                if (firstDay == null || secondDay == null || nextMonthDay == null) {
                    yield ExpectedRefundCalculationResult.pending(ruleText, "规则缺少区间付款日");
                }
                LocalDate expectedDate;
                if (shiftedDate.getDayOfMonth() <= firstDay) {
                    expectedDate = clampToMonth(shiftedDate, firstDay);
                } else if (shiftedDate.getDayOfMonth() <= secondDay) {
                    expectedDate = clampToMonth(shiftedDate, secondDay);
                } else {
                    expectedDate = clampToMonth(shiftedDate.plusMonths(1), nextMonthDay);
                }
                yield ExpectedRefundCalculationResult.resolved(expectedDate, ruleText);
            }
            case "", "NONE" -> ExpectedRefundCalculationResult.resolved(shiftedDate, ruleText);
            default -> ExpectedRefundCalculationResult.pending(ruleText, "暂不支持的付款节点类型: " + rule.getPaymentAnchorType());
        };
    }

    public PartyAPaymentRule findMatchingRule(SalesOrder order) {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, order.getPartyATitle());
        addCandidate(candidates, order.getPlatformName());
        if (candidates.isEmpty()) {
            return null;
        }
        List<PartyAPaymentRule> enabledRules = partyAPaymentRuleRepository.findAllEnabledOrderByUpdateTimeDesc();
        return enabledRules.stream()
                .filter(rule -> {
                    String title = normalize(rule.getPartyATitle());
                    if (title.isEmpty()) {
                        return false;
                    }
                    return candidates.stream().anyMatch(candidate -> candidate.equals(title) || candidate.contains(title) || title.contains(candidate));
                })
                .max(Comparator.comparingInt(rule -> normalize(rule.getPartyATitle()).length()))
                .orElse(null);
    }

    @Transactional
    public int recalculateOrdersForTitle(String partyATitle) {
        String title = normalize(partyATitle);
        Specification<SalesOrder> spec = (root, query, cb) -> cb.or(
                cb.equal(cb.trim(root.get("partyATitle")), title),
                cb.equal(cb.trim(root.get("platformName")), title)
        );
        List<SalesOrder> orders = salesOrderRepository.findAll(spec);
        return recalculateOrders(orders);
    }

    @Transactional
    public int recalculateAllOpenOrders() {
        Specification<SalesOrder> spec = (root, query, cb) -> cb.not(root.get("status").in("已取消", "已退回", "已结算"));
        return recalculateOrders(salesOrderRepository.findAll(spec));
    }

    private int recalculateOrders(List<SalesOrder> orders) {
        int updated = 0;
        for (SalesOrder order : orders) {
            if (order == null) {
                continue;
            }
            refreshExpectedRefundFields(order);
            salesOrderRepository.save(order);
            updated++;
        }
        return updated;
    }

    private void addCandidate(List<String> candidates, String raw) {
        String text = normalize(raw);
        if (!text.isEmpty() && !candidates.contains(text)) {
            candidates.add(text);
        }
    }

    private String buildRuleDescription(PartyAPaymentRule rule) {
        String custom = normalize(rule.getDescription());
        if (!custom.isEmpty()) {
            return custom;
        }
        String example = normalize(rule.getExampleRuleText());
        if (!example.isEmpty()) {
            return example;
        }
        return "按甲方回款规则自动计算";
    }

    private String buildMissingBaseReason(PartyAPaymentRule rule) {
        return switch (normalize(rule.getBaseEventType())) {
            case "DELIVERY_DATE" -> "待发货后确认交货日期";
            case "INVOICE_DATE" -> "待补开票日期";
            case "RECONCILIATION_DATE" -> "待补对账日期";
            default -> "待补规则基准时间";
        };
    }

    private LocalDate resolveBaseDate(SalesOrder order, PartyAPaymentRule rule) {
        return switch (normalize(rule.getBaseEventType())) {
            case "DELIVERY_DATE" -> hasReachedDeliveryBasedRefundStage(order) ? adjustBaseCycle(order.getDeliveryDate(), rule) : null;
            case "INVOICE_DATE" -> adjustBaseCycle(order.getInvoiceIssuedDate(), rule);
            case "RECONCILIATION_DATE" -> adjustBaseCycle(order.getReconciliationDate(), rule);
            default -> null;
        };
    }

    /**
     * 预计回款中的 DELIVERY_DATE 基准使用的是业务真实进入发货/发货后阶段后的交货日期；
     * 若订单还停留在盖章、待发货等前置阶段，即使前端/导入已预填了计划交货日期，也不应提前推算回款时间。
     */
    private boolean hasReachedDeliveryBasedRefundStage(SalesOrder order) {
        if (order == null || order.getDeliveryDate() == null) {
            return false;
        }
        String status = normalize(order.getStatus());
        if (status.isEmpty()) {
            return false;
        }
        return switch (status) {
            case "已发货", "已出库", "运输中", "派送中", "已对账未开票", "已开票待结算", "已结算" -> true;
            default -> false;
        };
    }

    private LocalDate adjustBaseCycle(LocalDate sourceDate, PartyAPaymentRule rule) {
        if (sourceDate == null) {
            return null;
        }
        Integer baseDay = sanitizeDay(rule.getBaseDayOfMonth());
        Integer cutoffDay = sanitizeDay(rule.getCycleCutoffDay());
        if (baseDay == null) {
            return sourceDate;
        }
        YearMonth targetMonth = YearMonth.from(sourceDate);
        if (cutoffDay != null && sourceDate.getDayOfMonth() > cutoffDay) {
            if (Boolean.TRUE.equals(rule.getCarryOverToNextCycle())) {
                targetMonth = targetMonth.plusMonths(1);
            } else {
                return null;
            }
        }
        return targetMonth.atDay(Math.min(baseDay, targetMonth.lengthOfMonth()));
    }

    private LocalDate clampToMonth(LocalDate reference, int dayOfMonth) {
        YearMonth yearMonth = YearMonth.from(reference);
        return yearMonth.atDay(Math.min(dayOfMonth, yearMonth.lengthOfMonth()));
    }

    private Integer sanitizeDay(Integer day) {
        if (day == null || day < 1) {
            return null;
        }
        return Math.min(day, 31);
    }

    private void applyCalculationResult(SalesOrder order, ExpectedRefundCalculationResult result) {
        if (result == null) {
            order.setExpectedRefundDate(null);
            order.setExpectedRefundRuleDescription(null);
            order.setExpectedRefundPendingReason("未匹配甲方回款规则");
            order.setExpectedRefundOverdue(false);
            return;
        }
        order.setExpectedRefundDate(result.expectedRefundDate());
        order.setExpectedRefundRuleDescription(result.ruleDescription());
        order.setExpectedRefundPendingReason(result.pendingReason());
        order.setExpectedRefundOverdue(isOverdue(order));
    }

    private boolean isOverdue(SalesOrder order) {
        if (order == null || order.getExpectedRefundDate() == null) {
            return false;
        }
        String refundStatus = normalize(order.getPlatformRefundStatus());
        String status = normalize(order.getStatus());
        if ("已回款".equals(refundStatus) || "已结算".equals(status) || "已取消".equals(status) || "已退回".equals(status)) {
            return false;
        }
        return order.getExpectedRefundDate().isBefore(LocalDate.now());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
