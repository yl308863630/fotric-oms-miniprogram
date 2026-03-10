package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    /** 与 SalesOrderService.searchOrders 一致的可见订单条件：非管理员仅 createdBy 或 assignedUsername */
    private Specification<SalesOrder> visibleSalesOrderSpec(String status, Boolean onlyCreator) {
        User current = getCurrentUser();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isEmpty()) {
                if (status.contains(",")) {
                    List<String> statusList = Arrays.asList(status.split(",\\s*"));
                    predicates.add(root.get("status").in(statusList));
                } else {
                    predicates.add(cb.equal(root.get("status"), status));
                }
            }
            if (current != null && !"ROLE_ADMIN".equals(current.getRole())) {
                Predicate createdBy = cb.equal(root.get("createdBy"), current.getId());
                Predicate assigned = cb.equal(root.get("assignedUsername"), current.getUsername());
                if (Boolean.TRUE.equals(onlyCreator)) {
                    predicates.add(createdBy);
                } else {
                    predicates.add(cb.or(createdBy, assigned));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private long countSalesOrders(Specification<SalesOrder> spec) {
        return salesOrderRepository.count(spec);
    }

    private List<Long> visibleSalesOrderIds() {
        User current = getCurrentUser();
        Specification<SalesOrder> spec = (root, query, cb) -> {
            if (current == null || "ROLE_ADMIN".equals(current.getRole())) {
                return cb.conjunction();
            }
            return cb.or(
                    cb.equal(root.get("createdBy"), current.getId()),
                    cb.equal(root.get("assignedUsername"), current.getUsername())
            );
        };
        return salesOrderRepository.findAll(spec, PageRequest.of(0, 50000))
                .getContent().stream().map(SalesOrder::getId).collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        User current = getCurrentUser();
        Map<String, Object> stats = new HashMap<>();

        // 用户隔离的订单数
        Specification<SalesOrder> allVisible = visibleSalesOrderSpec(null, false);
        stats.put("orderCount", countSalesOrders(allVisible));

        stats.put("pendingInvoiceCount", invoiceRepository.findAll().stream()
                .filter(i -> "DRAFT".equals(i.getStatus()) || "PENDING".equals(i.getStatus())).count());
        stats.put("settledCount", settlementRepository.findAll().stream()
                .filter(s -> "SETTLED".equals(s.getStatus())).count());

        List<Long> visibleIds = visibleSalesOrderIds();
        if (visibleIds.isEmpty() && current != null && !"ROLE_ADMIN".equals(current.getRole())) {
            stats.put("contractCount", 0L);
        } else {
            long contractCount = visibleIds.isEmpty()
                    ? contractRepository.count()
                    : contractRepository.count((root, q, cb) -> root.get("salesOrderId").in(visibleIds));
            stats.put("contractCount", contractCount);
        }

        stats.put("trend", getTrendData());
        stats.put("todos", getTodoCounts());
        return stats;
    }

    public Map<String, Object> getTodoCounts() {
        User current = getCurrentUser();
        String username = current != null ? current.getUsername() : null;
        LocalDate today = LocalDate.now();
        LocalDate approachingEnd = today.plusDays(3);

        Map<String, Object> todos = new HashMap<>();

        // 待接单：status=待指派，且当前用户为创建人
        Specification<SalesOrder> pendingAcceptSpec = visibleSalesOrderSpec("待指派", true);
        long pendingAccept = countSalesOrders(pendingAcceptSpec);
        todos.put("pendingAccept", pendingAccept);
        todos.put("pendingAcceptApproaching", countWithDeliveryDate(pendingAcceptSpec, today, approachingEnd, false));
        todos.put("pendingAcceptOverdue", countWithDeliveryDate(pendingAcceptSpec, null, today, true));

        // 待确认订单：被指派方（如坚领）接到的指派订单，需确认后再进入合同盖章
        Specification<SalesOrder> pendingConfirmSpec = (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            list.add(cb.equal(root.get("status"), "待确认订单"));
            if (current != null && !"ROLE_ADMIN".equals(current.getRole())) {
                list.add(cb.equal(root.get("assignedUsername"), current.getUsername()));
            }
            return cb.and(list.toArray(new Predicate[0]));
        };
        long pendingConfirmOrder = salesOrderRepository.count(pendingConfirmSpec);
        todos.put("pendingConfirmOrder", pendingConfirmOrder);

        // 待发货：status=待发货
        Specification<SalesOrder> pendingShipSpec = visibleSalesOrderSpec("待发货", false);
        todos.put("pendingShip", countSalesOrders(pendingShipSpec));
        todos.put("pendingShipApproaching", countWithDeliveryDate(pendingShipSpec, today, approachingEnd, false));
        todos.put("pendingShipOverdue", countWithDeliveryDate(pendingShipSpec, null, today, true));

        // 待妥投：status=已发货 且 receiptUrl 为空
        Specification<SalesOrder> pendingDeliverySpec = visibleSalesOrderSpec("已发货", false);
        long pendingDelivery = salesOrderRepository.count(pendingDeliverySpec.and((root, q, cb) ->
                cb.or(cb.isNull(root.get("receiptUrl")), cb.equal(root.get("receiptUrl"), ""))));
        todos.put("pendingDelivery", pendingDelivery);
        todos.put("pendingDeliveryApproaching", 0);
        todos.put("pendingDeliveryOverdue", 0);

        // 待签收：已发货且已上传签收单（或与待妥投合并，这里简化为 0 或与待妥投一致）
        todos.put("pendingReceipt", 0L);
        todos.put("pendingReceiptApproaching", 0);
        todos.put("pendingReceiptOverdue", 0);

        // 其他待办占位
        todos.put("pendingAfterSales", 0L);
        todos.put("pendingWorkOrder", 0L);
        todos.put("pendingClaim", 0L);
        todos.put("pendingClaimAppeal", 0L);
        todos.put("pendingClaimPayment", 0L);
        todos.put("rejectedReceipt", 0L);
        todos.put("pendingProduct", countPendingProducts());
        todos.put("pendingFiling", 0L);

        // 客户结算：待客户回款 = 当前用户可见订单中 platformRefundStatus 未回款/部分回款
        Specification<SalesOrder> refundPending = visibleSalesOrderSpec(null, false).and((root, q, cb) ->
                root.get("platformRefundStatus").in("未回款", "部分回款", null));
        long pendingCustomerPayment = salesOrderRepository.count(refundPending.and((root, q, cb) ->
                cb.not(root.get("status").in("已退回", "待指派"))));
        todos.put("pendingCustomerPayment", pendingCustomerPayment);
        todos.put("pendingCustomerPaymentApproaching", 0);
        todos.put("pendingCustomerPaymentOverdue", 0);

        long pendingInvoiceConfirm = invoiceRepository.findAll().stream()
                .filter(i -> "PENDING".equals(i.getStatus()) || "DRAFT".equals(i.getStatus())).count();
        todos.put("pendingInvoiceConfirm", pendingInvoiceConfirm);
        todos.put("pendingInvoiceConfirmApproaching", 0);
        todos.put("pendingInvoiceConfirmOverdue", 0);

        // 合作商结算
        todos.put("pendingInboundInvoice", 0L);
        todos.put("pendingInboundReturn", 0L);

        return todos;
    }

    private long countWithDeliveryDate(Specification<SalesOrder> base, LocalDate from, LocalDate to, boolean overdue) {
        return salesOrderRepository.count(base.and((root, q, cb) -> {
            if (overdue) {
                return cb.and(cb.isNotNull(root.get("deliveryDate")), cb.lessThan(root.get("deliveryDate"), to));
            }
            return cb.and(cb.isNotNull(root.get("deliveryDate")),
                    cb.greaterThanOrEqualTo(root.get("deliveryDate"), from),
                    cb.lessThanOrEqualTo(root.get("deliveryDate"), to));
        }));
    }

    private long countPendingProducts() {
        return productRepository.count((root, q, cb) ->
                cb.or(cb.isNull(root.get("isActive")), cb.equal(root.get("isActive"), false)));
    }

    private Map<String, Object> getTrendData() {
        User current = getCurrentUser();
        Specification<SalesOrder> spec = visibleSalesOrderSpec(null, false);
        List<SalesOrder> orders = salesOrderRepository.findAll(spec, PageRequest.of(0, 10000)).getContent();

        Map<LocalDate, BigDecimal> amountByDate = orders.stream()
                .filter(o -> o.getOrderDate() != null && o.getTaxIncludedTotal() != null)
                .collect(Collectors.groupingBy(SalesOrder::getOrderDate,
                        Collectors.reducing(BigDecimal.ZERO, SalesOrder::getTaxIncludedTotal, BigDecimal::add)));

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);
        List<String> dates = new ArrayList<>();
        List<Double> orderAmounts = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d.format(DateTimeFormatter.ofPattern("MM-dd")));
            orderAmounts.add(amountByDate.getOrDefault(d, BigDecimal.ZERO).doubleValue());
        }

        Map<String, Object> trend = new HashMap<>();
        trend.put("dates", dates);
        trend.put("orderAmounts", orderAmounts);
        // 对账金额暂无按订单关联，沿用简化
        trend.put("invoiceAmounts", orderAmounts.stream().map(a -> a * 0.9).collect(Collectors.toList()));
        return trend;
    }

    /** 线下销售汇总：当前用户可见订单中 offlineSales 不为空的笔数、金额、最近几条 */
    public Map<String, Object> getOfflineSummary() {
        Specification<SalesOrder> spec = visibleSalesOrderSpec(null, false).and((root, q, cb) ->
                cb.and(cb.isNotNull(root.get("offlineSales")), cb.notEqual(root.get("offlineSales"), "")));
        List<SalesOrder> list = salesOrderRepository.findAll(spec, PageRequest.of(0, 20)).getContent();
        long count = salesOrderRepository.count(spec);
        BigDecimal total = list.stream()
                .map(SalesOrder::getTaxIncludedTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> out = new HashMap<>();
        out.put("count", count);
        out.put("totalAmount", total != null ? total.doubleValue() : 0);
        out.put("recent", list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("omsOrderNo", o.getOmsOrderNo());
            m.put("platformOrderNo", o.getPlatformOrderNo());
            m.put("amount", o.getTaxIncludedTotal());
            m.put("offlineContractNo", o.getOfflineContractNo());
            m.put("orderDate", o.getOrderDate() != null ? o.getOrderDate().toString() : null);
            return m;
        }).collect(Collectors.toList()));
        return out;
    }
}
