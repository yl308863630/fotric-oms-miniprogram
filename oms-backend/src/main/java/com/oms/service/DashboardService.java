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
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SalesReconciliationRepository salesReconciliationRepository;
    @Autowired
    private SalesOutputInvoiceRepository salesOutputInvoiceRepository;
    @Autowired
    private SalesSettlementRecordRepository salesSettlementRecordRepository;
    @Autowired
    private PurchaseReconciliationRepository purchaseReconciliationRepository;
    @Autowired
    private PurchaseInputInvoiceRecordRepository purchaseInputInvoiceRecordRepository;
    @Autowired
    private PurchaseSettlementRecordRepository purchaseSettlementRecordRepository;

    @Autowired
    private ContractService contractService;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    private boolean hasPermission(User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) {
            return false;
        }
        String permissions = user.getPermissions();
        if (permissions == null || permissions.isBlank()) {
            return false;
        }
        for (String item : permissions.split(",")) {
            if (permission.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFeichukeCompany(User user) {
        return user != null && user.getCompanyTitle() != null && user.getCompanyTitle().contains("飞础科智慧科技（上海）有限公司");
    }

    private boolean isRexiangCompany(User user) {
        return user != null && user.getCompanyTitle() != null && user.getCompanyTitle().contains("上海热像科技股份有限公司");
    }

    private Set<Long> feichukeUserIds() {
        return userRepository.findAll().stream()
                .filter(this::isFeichukeCompany)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 与 SalesOrderService.searchOrders 一致的可见订单条件：非管理员仅 createdBy 或 assignedUsername */
    private Specification<SalesOrder> visibleSalesOrderSpec(String status, Boolean onlyCreator) {
        User current = getCurrentUser();
        List<String> sharedUsernames = current != null ? subjectAccountGroupService.manuallySharedUsernames(current.getUsername()) : List.of();
        List<Long> sharedUserIds = current != null ? subjectAccountGroupService.manuallySharedUserIds(current.getUsername()) : List.of();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isEmpty()) {
                List<String> statusList = expandSalesOrderStatusFilter(status);
                if (statusList.size() == 1) {
                    predicates.add(cb.equal(root.get("status"), statusList.get(0)));
                } else {
                    predicates.add(root.get("status").in(statusList));
                }
                if (containsStatusFilter(status, "已开票待结算")) {
                    predicates.add(pendingSettlementRefundPredicate(root, cb));
                }
            }
            if (current != null && !"ROLE_ADMIN".equals(current.getRole())) {
                Predicate createdBy = !sharedUserIds.isEmpty()
                        ? root.get("createdBy").in(sharedUserIds)
                        : cb.equal(root.get("createdBy"), current.getId());
                Predicate assigned = !sharedUsernames.isEmpty()
                        ? root.get("assignedUsername").in(sharedUsernames)
                        : cb.equal(root.get("assignedUsername"), current.getUsername());
                if (Boolean.TRUE.equals(onlyCreator)) {
                    predicates.add(createdBy);
                } else {
                    predicates.add(cb.or(createdBy, assigned));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<String> expandSalesOrderStatusFilter(String status) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String raw : status.split(",\\s*")) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            expanded.add(value);
            if ("已开票待结算".equals(value)) {
                expanded.add("已开票");
            } else if ("已结算".equals(value)) {
                expanded.add("已回款");
            }
        }
        return new ArrayList<>(expanded);
    }

    private boolean containsStatusFilter(String status, String expected) {
        if (status == null || status.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        return Arrays.stream(status.split(",\\s*"))
                .map(value -> value == null ? "" : value.trim())
                .anyMatch(expected::equals);
    }

    private Predicate pendingSettlementRefundPredicate(jakarta.persistence.criteria.Root<SalesOrder> root,
                                                      jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.or(
                cb.isNull(root.get("platformRefundStatus")),
                cb.equal(root.get("platformRefundStatus"), ""),
                cb.equal(root.get("platformRefundStatus"), "未回款"),
                cb.equal(root.get("platformRefundStatus"), "部分回款")
        );
    }

    private long countSalesOrders(Specification<SalesOrder> spec) {
        return salesOrderRepository.count(spec);
    }

    private long countDistinctPendingAssignMasters(Specification<SalesOrder> spec) {
        return salesOrderRepository.findAll(spec, PageRequest.of(0, 50000))
                .getContent()
                .stream()
                .map(this::pendingAssignMasterKey)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private String pendingAssignMasterKey(SalesOrder order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        return order.getMasterId() != null ? "M:" + order.getMasterId() : "O:" + order.getId();
    }

    private long countReceiptFilter(String filterValue) {
        Specification<SalesOrder> spec = visibleSalesOrderSpec(null, false);
        Specification<SalesOrder> receiptSpec = SalesOrderReceiptFlowHelper.receiptFilterSpec(filterValue);
        if (receiptSpec != null) {
            spec = spec.and(receiptSpec);
        }
        return salesOrderRepository.count(spec);
    }

    private List<Long> visibleSalesOrderIds() {
        User current = getCurrentUser();
        List<String> sharedUsernames = current != null ? subjectAccountGroupService.manuallySharedUsernames(current.getUsername()) : List.of();
        List<Long> sharedUserIds = current != null ? subjectAccountGroupService.manuallySharedUserIds(current.getUsername()) : List.of();
        Specification<SalesOrder> spec = (root, query, cb) -> {
            if (current == null || "ROLE_ADMIN".equals(current.getRole())) {
                return cb.conjunction();
            }
            return cb.or(
                    !sharedUserIds.isEmpty() ? root.get("createdBy").in(sharedUserIds) : cb.equal(root.get("createdBy"), current.getId()),
                    !sharedUsernames.isEmpty() ? root.get("assignedUsername").in(sharedUsernames) : cb.equal(root.get("assignedUsername"), current.getUsername())
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

        stats.put("pendingInvoiceCount",
                countSalesReconciliationsByStatuses("已对账")
                        + countPurchaseReconciliationsByStatuses("已对账"));
        stats.put("settledCount",
                countSalesSettlementsByStatuses("已结算")
                        + countPurchaseSettlementsByStatuses("已付款"));

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
        LocalDate today = LocalDate.now();
        LocalDate approachingEnd = today.plusDays(3);

        Map<String, Object> todos = new HashMap<>();

        // 待接单：status=待指派，且当前用户为创建人
        Specification<SalesOrder> pendingAcceptSpec = visibleSalesOrderSpec("待指派", true);
        long pendingAccept = countDistinctPendingAssignMasters(pendingAcceptSpec);
        todos.put("pendingAccept", pendingAccept);
        todos.put("pendingAcceptApproaching", countDistinctPendingAssignMasters(withDeliveryDate(pendingAcceptSpec, today, approachingEnd, false)));
        todos.put("pendingAcceptOverdue", countDistinctPendingAssignMasters(withDeliveryDate(pendingAcceptSpec, null, today, true)));

        // 待确认订单：被指派方（如坚领）接到的指派订单，需确认后再进入合同盖章
        Specification<SalesOrder> pendingConfirmSpec = (root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            list.add(cb.equal(root.get("status"), "待确认订单"));
            if (current != null && !"ROLE_ADMIN".equals(current.getRole())) {
                List<String> sharedUsernames = subjectAccountGroupService.manuallySharedUsernames(current.getUsername());
                list.add(!sharedUsernames.isEmpty()
                        ? root.get("assignedUsername").in(sharedUsernames)
                        : cb.equal(root.get("assignedUsername"), current.getUsername()));
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

        // 需签收单（待上传）：与销售列表 pending_upload 保持一致
        long pendingDelivery = countReceiptFilter(SalesOrderReceiptFlowHelper.FILTER_PENDING_UPLOAD);
        todos.put("pendingDelivery", pendingDelivery);
        todos.put("pendingDeliveryApproaching", 0);
        todos.put("pendingDeliveryOverdue", 0);

        // 已发货等待签收：与销售列表 waiting_sign 保持一致
        long pendingReceipt = countReceiptFilter(SalesOrderReceiptFlowHelper.FILTER_WAITING_SIGN);
        todos.put("pendingReceipt", pendingReceipt);
        todos.put("pendingReceiptApproaching", 0);
        todos.put("pendingReceiptOverdue", 0);

        long pendingSelfVehicleReceipt = countReceiptFilter(SalesOrderReceiptFlowHelper.FILTER_SELF_VEHICLE_PENDING_RECEIPT);
        todos.put("pendingSelfVehicleReceipt", pendingSelfVehicleReceipt);
        todos.put("pendingSelfVehicleReceiptApproaching", 0);
        todos.put("pendingSelfVehicleReceiptOverdue", 0);

        // 妥投结束：与销售列表 completed 保持一致
        long completedDelivery = countReceiptFilter(SalesOrderReceiptFlowHelper.FILTER_COMPLETED);
        todos.put("completedDelivery", completedDelivery);

        // 其他待办占位
        todos.put("pendingAfterSales", 0L);
        todos.put("pendingWorkOrder", 0L);
        todos.put("pendingClaim", 0L);
        todos.put("pendingClaimAppeal", 0L);
        todos.put("pendingClaimPayment", 0L);
        todos.put("rejectedReceipt", 0L);
        todos.put("pendingProduct", countPendingProducts());
        todos.put("pendingFiling", 0L);
        todos.put("pendingContractSign", countPendingContractSign(current));
        todos.put("pendingFeichukeErpEntry", countPendingFeichukeErpEntry(current));
        todos.put("pendingRexiangErpEntry", countPendingRexiangErpEntry(current));

        // 客户结算：待客户回款/即将逾期/已逾期 共用同一套口径，统一排除待指派、已退回。
        Specification<SalesOrder> refundPending = pendingCustomerPaymentSpec();
        long pendingCustomerPayment = salesOrderRepository.count(refundPending);
        todos.put("pendingCustomerPayment", pendingCustomerPayment);
        todos.put("pendingCustomerPaymentApproaching", countWithExpectedRefundDate(refundPending, today, approachingEnd, false));
        todos.put("pendingCustomerPaymentOverdue", countWithExpectedRefundDate(refundPending, null, today, true));

        // 销售订单：已填甲方对账单号、待财务开票（与 SalesOrder.status=已对账未开票 一致）
        Specification<SalesOrder> reconciledNeedInvoiceSpec = visibleSalesOrderSpec("已对账未开票", false);
        todos.put("reconciledNeedInvoice", countSalesOrders(reconciledNeedInvoiceSpec));
        todos.put("reconciledNeedInvoiceApproaching", 0);
        todos.put("reconciledNeedInvoiceOverdue", 0);

        // 销售订单：已开票、待客户结算（新流程与 SalesOrder.status=已开票待结算 一致）
        Specification<SalesOrder> invoicedPendingSettlementSpec = visibleSalesOrderSpec("已开票待结算", false);
        todos.put("invoicedPendingSettlement", countSalesOrders(invoicedPendingSettlementSpec));
        todos.put("invoicedPendingSettlementApproaching", countWithExpectedRefundDate(invoicedPendingSettlementSpec, today, approachingEnd, false));
        todos.put("invoicedPendingSettlementOverdue", countWithExpectedRefundDate(invoicedPendingSettlementSpec, null, today, true));

        // 新聚合单据待办
        todos.put("pendingSalesReconciliation", countSalesReconciliationsByStatuses("已对账"));
        todos.put("pendingSalesOutputInvoice", countSalesOutputInvoicesByStatuses("已开票"));
        todos.put("pendingSalesSettlement", countSalesSettlementsByStatuses("待回款", "部分回款"));
        todos.put("pendingPurchaseReconciliation", countPurchaseReconciliationsByStatuses("已对账"));
        todos.put("pendingPurchaseInputInvoice", countPurchaseInputInvoicesByStatuses("已收票"));
        todos.put("pendingPurchaseSettlement", countPurchaseSettlementsByStatuses("待付款", "部分付款"));

        // 合作商结算（兼容旧卡片，同时映射到新采购发票链路）
        todos.put("pendingInboundInvoice", countPurchaseInputInvoicesByStatuses("已收票"));
        todos.put("pendingInboundReturn", 0L);

        return todos;
    }

    private long countPendingFeichukeErpEntry(User current) {
        Set<Long> feichukeIds = feichukeUserIds();
        if (feichukeIds.isEmpty()) {
            return 0L;
        }
        Specification<SalesOrder> pendingSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("erpEntryStatus"), "待系统录单"));
            if (current == null) {
                predicates.add(cb.disjunction());
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if ("ROLE_ADMIN".equals(current.getRole()) || hasPermission(current, "sales_all_orders") || isRexiangCompany(current)) {
                predicates.add(root.get("createdBy").in(feichukeIds));
            } else {
                List<Long> sharedUserIds = subjectAccountGroupService.manuallySharedUserIds(current.getUsername());
                predicates.add(!sharedUserIds.isEmpty()
                        ? root.get("createdBy").in(sharedUserIds)
                        : cb.equal(root.get("createdBy"), current.getId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return salesOrderRepository.count(pendingSpec);
    }

    private long countPendingRexiangErpEntry(User current) {
        Set<Long> feichukeIds = feichukeUserIds();
        List<Long> sharedUserIds = current != null ? subjectAccountGroupService.manuallySharedUserIds(current.getUsername()) : List.of();
        return purchaseOrderRepository.findAll().stream()
                .filter(po -> "待系统录单".equals(po.getErpEntryStatus()))
                .filter(po -> {
                    if (current == null) {
                        return false;
                    }
                    if ("ROLE_ADMIN".equals(current.getRole())) {
                        return true;
                    }
                    if (isRexiangCompany(current)) {
                        return po.getCreatedBy() != null && (((!sharedUserIds.isEmpty() && sharedUserIds.contains(po.getCreatedBy()))
                                || po.getCreatedBy().equals(current.getId())) || feichukeIds.contains(po.getCreatedBy()));
                    }
                    return po.getCreatedBy() != null && (((!sharedUserIds.isEmpty() && sharedUserIds.contains(po.getCreatedBy()))
                            || po.getCreatedBy().equals(current.getId())));
                })
                .count();
    }

    /**
     * 待签署合同：当前账号在合同列表中可见、当前公司正好是乙方、且乙方尚未签署。
     * 与合同管理页的 canSignPartyB 判定保持一致，方便被指派对象直接从 Dashboard 跳转处理。
     */
    private long countPendingContractSign(User current) {
        if (current == null) {
            return 0L;
        }
        String companyTitle = current.getCompanyTitle();
        if (companyTitle == null || companyTitle.isBlank()) {
            return 0L;
        }
        String myCompany = companyTitle.trim();
        return contractRepository.findAll().stream()
                .filter(c -> "待签署".equals(c.getStatus()))
                .filter(c -> !Boolean.TRUE.equals(c.getPartyBSigned()))
                .filter(c -> myCompany.equals(c.getPartyBName()))
                .filter(c -> contractService.canCurrentUserViewContract(current, c))
                .count();
    }

    private Specification<SalesOrder> pendingCustomerPaymentSpec() {
        return visibleSalesOrderSpec(null, false)
                .and((root, q, cb) -> root.get("platformRefundStatus").in("未回款", "部分回款"))
                .and((root, q, cb) -> cb.not(root.get("status").in("已退回", "待指派")));
    }

    private Specification<SalesOrder> withDeliveryDate(Specification<SalesOrder> base, LocalDate from, LocalDate to, boolean overdue) {
        return base.and((root, q, cb) -> {
            if (overdue) {
                return cb.and(cb.isNotNull(root.get("deliveryDate")), cb.lessThan(root.get("deliveryDate"), to));
            }
            return cb.and(cb.isNotNull(root.get("deliveryDate")),
                    cb.greaterThanOrEqualTo(root.get("deliveryDate"), from),
                    cb.lessThanOrEqualTo(root.get("deliveryDate"), to));
        });
    }

    private long countWithDeliveryDate(Specification<SalesOrder> base, LocalDate from, LocalDate to, boolean overdue) {
        return salesOrderRepository.count(withDeliveryDate(base, from, to, overdue));
    }

    private long countWithExpectedRefundDate(Specification<SalesOrder> base, LocalDate from, LocalDate to, boolean overdue) {
        return salesOrderRepository.count(base.and((root, q, cb) -> {
            Predicate notSettled = cb.or(
                    cb.isNull(root.get("platformRefundStatus")),
                    cb.equal(root.get("platformRefundStatus"), ""),
                    cb.equal(root.get("platformRefundStatus"), "未回款"),
                    cb.equal(root.get("platformRefundStatus"), "部分回款")
            );
            if (overdue) {
                return cb.and(
                        cb.isNotNull(root.get("expectedRefundDate")),
                        cb.lessThan(root.get("expectedRefundDate"), to),
                        notSettled,
                        cb.not(root.get("status").in("已取消", "已退回", "已结算"))
                );
            }
            return cb.and(
                    cb.isNotNull(root.get("expectedRefundDate")),
                    cb.greaterThanOrEqualTo(root.get("expectedRefundDate"), from),
                    cb.lessThanOrEqualTo(root.get("expectedRefundDate"), to),
                    notSettled,
                    cb.not(root.get("status").in("已取消", "已退回", "已结算"))
            );
        }));
    }

    private long countPendingProducts() {
        return productRepository.count((root, q, cb) ->
                cb.or(cb.isNull(root.get("isActive")), cb.equal(root.get("isActive"), false)));
    }

    private Map<String, Object> getTrendData() {
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

        Map<LocalDate, BigDecimal> reconciliationAmountByDate = salesReconciliationRepository.findAll().stream()
                .filter(item -> item.getReconciliationDate() != null && item.getTotalAmount() != null)
                .collect(Collectors.groupingBy(
                        com.oms.entity.SalesReconciliation::getReconciliationDate,
                        Collectors.reducing(BigDecimal.ZERO, com.oms.entity.SalesReconciliation::getTotalAmount, BigDecimal::add)
                ));

        List<Double> invoiceAmounts = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            invoiceAmounts.add(reconciliationAmountByDate.getOrDefault(d, BigDecimal.ZERO).doubleValue());
        }

        Map<String, Object> trend = new HashMap<>();
        trend.put("dates", dates);
        trend.put("orderAmounts", orderAmounts);
        trend.put("invoiceAmounts", invoiceAmounts);
        return trend;
    }

    private long countSalesReconciliationsByStatuses(String... statuses) {
        Set<String> statusSet = new HashSet<>(Arrays.asList(statuses));
        return salesReconciliationRepository.findAll().stream()
                .filter(item -> statusSet.contains(item.getStatus()))
                .count();
    }

    private long countSalesOutputInvoicesByStatuses(String... statuses) {
        Set<String> statusSet = new HashSet<>(Arrays.asList(statuses));
        return salesOutputInvoiceRepository.findAll().stream()
                .filter(item -> statusSet.contains(item.getStatus()))
                .count();
    }

    private long countSalesSettlementsByStatuses(String... statuses) {
        Set<String> statusSet = new HashSet<>(Arrays.asList(statuses));
        return salesSettlementRecordRepository.findAll().stream()
                .filter(item -> statusSet.contains(item.getStatus()))
                .count();
    }

    private long countPurchaseReconciliationsByStatuses(String... statuses) {
        Set<String> statusSet = new HashSet<>(Arrays.asList(statuses));
        return purchaseReconciliationRepository.findAll().stream()
                .filter(item -> statusSet.contains(item.getStatus()))
                .count();
    }

    private long countPurchaseInputInvoicesByStatuses(String... statuses) {
        Set<String> statusSet = new HashSet<>(Arrays.asList(statuses));
        return purchaseInputInvoiceRecordRepository.findAll().stream()
                .filter(item -> statusSet.contains(item.getStatus()))
                .count();
    }

    private long countPurchaseSettlementsByStatuses(String... statuses) {
        Set<String> statusSet = new HashSet<>(Arrays.asList(statuses));
        return purchaseSettlementRecordRepository.findAll().stream()
                .filter(item -> statusSet.contains(item.getStatus()))
                .count();
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
