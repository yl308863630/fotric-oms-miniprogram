package com.oms.service;

import com.oms.entity.Contract;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.ContractRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContractSignReminderService {
    private static final String FEICHUKE_TITLE = "飞础科智慧科技（上海）有限公司";
    private static final String REXIANG_TITLE = "上海热像科技股份有限公司";

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private OperationLogService operationLogService;

    @Scheduled(cron = "${oms.contract-sign.pending-reminder-cron:0 0 9 * * ?}")
    @Transactional(readOnly = true)
    public void sendPendingPartyBSignReminder() {
        List<Contract> pendingContracts = new ArrayList<>(contractRepository.findByStatusAndPartyBSignedFalse("待签署"));
        pendingContracts.removeIf(contract -> contract == null
                || contract.getSalesOrderId() == null
                || !contractInvolvesWatchlistParty(contract));
        pendingContracts.sort(Comparator.comparing(this::resolvePendingStartTime, Comparator.nullsLast(Comparator.naturalOrder())));
        if (pendingContracts.isEmpty()) {
            return;
        }

        List<Long> salesOrderIds = pendingContracts.stream()
                .map(Contract::getSalesOrderId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SalesOrder> salesOrderMap = salesOrderRepository.findAllById(salesOrderIds).stream()
                .collect(Collectors.toMap(SalesOrder::getId, item -> item));

        Map<Long, List<PendingContractItem>> grouped = new HashMap<>();
        for (Contract contract : pendingContracts) {
            SalesOrder order = salesOrderMap.get(contract.getSalesOrderId());
            if (order == null || order.getCreatedBy() == null) {
                continue;
            }
            grouped.computeIfAbsent(order.getCreatedBy(), key -> new ArrayList<>())
                    .add(new PendingContractItem(contract, order));
        }

        for (Map.Entry<Long, List<PendingContractItem>> entry : grouped.entrySet()) {
            User operator = userRepository.findById(entry.getKey()).orElse(null);
            if (operator == null || !Boolean.TRUE.equals(operator.getEnabled())) {
                continue;
            }
            String mobile = operator.getPhone();
            if (mobile == null || mobile.isBlank()) {
                continue;
            }
            List<PendingContractItem> items = entry.getValue();
            items.sort(Comparator.comparing(item -> resolvePendingStartTime(item.contract), Comparator.nullsLast(Comparator.naturalOrder())));
            sendOperatorReminder(operator, items, mobile.trim());
        }
    }

    private void sendOperatorReminder(User operator, List<PendingContractItem> items, String mobile) {
        String operatorName = resolveUserDisplayName(operator);
        String title = "📝 待乙方签署合同跟进提醒";
        StringBuilder text = new StringBuilder();
        text.append("## 📝 待乙方签署合同跟进提醒\n\n")
                .append("**操作人：** ").append(operatorName).append("\n\n")
                .append("**待跟进数量：** ").append(items.size()).append(" 份\n\n")
                .append("**说明：** 以下合同已生成带甲方章，仍待被指派方（乙方）签署，请及时跟进。\n\n");
        int index = 1;
        for (PendingContractItem item : items) {
            Contract contract = item.contract;
            SalesOrder order = item.order;
            text.append(index++)
                    .append(". **合同编号：** ").append(safe(contract.getContractNo())).append("\n")
                    .append("   **乙方/被指派方：** ").append(safe(contract.getPartyBName())).append("\n")
                    .append("   **OMS订单号：** ").append(safe(order.getOmsOrderNo())).append("\n")
                    .append("   **甲方订单号：** ").append(safe(order.getPlatformOrderNo())).append("\n")
                    .append("   **待签天数：** ").append(resolvePendingDays(contract)).append(" 天\n\n");
        }
        text.append("---\n*来自 OMS 订单系统*");
        dingTalkService.sendMarkdownMessage(title, text.toString(), List.of(mobile));
        operationLogService.log(operatorName, "合同待签署跟进提醒", "CONTRACT",
                String.valueOf(items.size()), "发送待乙方签署清单提醒");
    }

    private boolean contractInvolvesWatchlistParty(Contract contract) {
        return containsWatchlistCompany(contract != null ? contract.getPartyAName() : null)
                || containsWatchlistCompany(contract != null ? contract.getPartyBName() : null);
    }

    private boolean containsWatchlistCompany(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String value = name.trim();
        return value.contains(FEICHUKE_TITLE) || value.contains(REXIANG_TITLE);
    }

    private LocalDateTime resolvePendingStartTime(Contract contract) {
        if (contract == null) {
            return null;
        }
        if (contract.getPartyASignedTime() != null) {
            return contract.getPartyASignedTime();
        }
        if (contract.getUpdateTime() != null) {
            return contract.getUpdateTime();
        }
        return contract.getCreateTime();
    }

    private long resolvePendingDays(Contract contract) {
        LocalDateTime start = resolvePendingStartTime(contract);
        if (start == null) {
            return 0L;
        }
        long days = ChronoUnit.DAYS.between(start.toLocalDate(), LocalDateTime.now().toLocalDate());
        return Math.max(days, 0L);
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return "系统";
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName().trim();
        }
        return safe(user.getUsername());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static final class PendingContractItem {
        private final Contract contract;
        private final SalesOrder order;

        private PendingContractItem(Contract contract, SalesOrder order) {
            this.contract = contract;
            this.order = order;
        }
    }
}
