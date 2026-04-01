package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.entity.SalesOutputInvoice;
import com.oms.entity.SalesOutputInvoiceItem;
import com.oms.entity.PurchaseInputInvoiceItem;
import com.oms.entity.PurchaseInputInvoiceRecord;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.PurchaseReconciliation;
import com.oms.entity.PurchaseReconciliationItem;
import com.oms.entity.SalesReconciliation;
import com.oms.entity.SalesReconciliationItem;
import com.oms.entity.SalesSettlementItem;
import com.oms.entity.SalesSettlementRecord;
import com.oms.entity.User;
import com.oms.repository.PurchaseInputInvoiceItemRepository;
import com.oms.repository.PurchaseInputInvoiceRecordRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.PurchaseReconciliationRepository;
import com.oms.repository.PurchaseReconciliationItemRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.SalesOutputInvoiceItemRepository;
import com.oms.repository.SalesOutputInvoiceRepository;
import com.oms.repository.SalesReconciliationItemRepository;
import com.oms.repository.SalesReconciliationRepository;
import com.oms.repository.SalesSettlementItemRepository;
import com.oms.repository.SalesSettlementRecordRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesAggregationService {
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    @Autowired
    private SalesReconciliationRepository salesReconciliationRepository;
    @Autowired
    private SalesReconciliationItemRepository salesReconciliationItemRepository;
    @Autowired
    private SalesOutputInvoiceRepository salesOutputInvoiceRepository;
    @Autowired
    private SalesOutputInvoiceItemRepository salesOutputInvoiceItemRepository;
    @Autowired
    private SalesSettlementRecordRepository salesSettlementRecordRepository;
    @Autowired
    private SalesSettlementItemRepository salesSettlementItemRepository;
    @Autowired
    private OperationLogService operationLogService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DingTalkService dingTalkService;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private PurchaseReconciliationRepository purchaseReconciliationRepository;
    @Autowired
    private PurchaseReconciliationItemRepository purchaseReconciliationItemRepository;
    @Autowired
    private PurchaseInputInvoiceItemRepository purchaseInputInvoiceItemRepository;
    @Autowired
    private PurchaseInputInvoiceRecordRepository purchaseInputInvoiceRecordRepository;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    @Autowired
    private SalesOrderService salesOrderService;

    public Page<SalesReconciliation> searchReconciliations(String billNo, String omsOrderNo, String platformName, String status, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        if (!canBrowseFinanceDocs(currentUser, visibleOwnerIds)) {
            return Page.empty(pageable);
        }
        Page<SalesReconciliation> page = salesReconciliationRepository.findAll((Specification<SalesReconciliation>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(billNo)) {
                String pattern = "%" + billNo.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("billNo"), pattern),
                        cb.like(root.get("platformReconciliationNo"), pattern)
                ));
            }
            if (hasText(omsOrderNo)) {
                Subquery<Integer> itemSubquery = query.subquery(Integer.class);
                var itemRoot = itemSubquery.from(SalesReconciliationItem.class);
                itemSubquery.select(cb.literal(1))
                        .where(
                                cb.equal(itemRoot.get("reconciliation").get("id"), root.get("id")),
                                cb.like(itemRoot.get("omsOrderNo"), "%" + omsOrderNo.trim() + "%")
                        );
                predicates.add(cb.exists(itemSubquery));
            }
            if (hasText(platformName)) {
                predicates.add(cb.like(root.get("platformName"), "%" + platformName.trim() + "%"));
            }
            if (hasText(status)) {
                List<String> statusList = splitStatuses(status);
                if (statusList.size() == 1) {
                    predicates.add(cb.equal(root.get("status"), statusList.get(0)));
                } else {
                    predicates.add(root.get("status").in(statusList));
                }
            }
            if (!isAdmin(currentUser)) {
                Subquery<Integer> visibilitySubquery = query.subquery(Integer.class);
                var itemRoot = visibilitySubquery.from(SalesReconciliationItem.class);
                var orderRoot = visibilitySubquery.from(SalesOrder.class);
                visibilitySubquery.select(cb.literal(1))
                        .where(
                                cb.equal(itemRoot.get("reconciliation").get("id"), root.get("id")),
                                cb.equal(orderRoot.get("id"), itemRoot.get("salesOrderId")),
                                orderRoot.get("createdBy").in(visibleOwnerIds)
                        );
                predicates.add(cb.exists(visibilitySubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
        enrichReconciliationSummaries(page.getContent());
        return page;
    }

    public Page<SalesOutputInvoice> searchOutputInvoices(String billNo, String platformName, String status, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        if (!canBrowseFinanceDocs(currentUser, visibleOwnerIds)) {
            return Page.empty(pageable);
        }
        return salesOutputInvoiceRepository.findAll((Specification<SalesOutputInvoice>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(billNo)) {
                predicates.add(cb.like(root.get("billNo"), "%" + billNo.trim() + "%"));
            }
            if (hasText(platformName)) {
                predicates.add(cb.like(root.get("platformName"), "%" + platformName.trim() + "%"));
            }
            if (hasText(status)) {
                List<String> statusList = splitStatuses(status);
                if (statusList.size() == 1) {
                    predicates.add(cb.equal(root.get("status"), statusList.get(0)));
                } else {
                    predicates.add(root.get("status").in(statusList));
                }
            }
            if (!isAdmin(currentUser)) {
                Subquery<Integer> visibilitySubquery = query.subquery(Integer.class);
                var invoiceItemRoot = visibilitySubquery.from(SalesOutputInvoiceItem.class);
                var reconciliationItemRoot = visibilitySubquery.from(SalesReconciliationItem.class);
                var orderRoot = visibilitySubquery.from(SalesOrder.class);
                visibilitySubquery.select(cb.literal(1))
                        .where(
                                cb.equal(invoiceItemRoot.get("invoice").get("id"), root.get("id")),
                                cb.equal(reconciliationItemRoot.get("reconciliation").get("id"), invoiceItemRoot.get("reconciliationId")),
                                cb.equal(orderRoot.get("id"), reconciliationItemRoot.get("salesOrderId")),
                                orderRoot.get("createdBy").in(visibleOwnerIds)
                        );
                predicates.add(cb.exists(visibilitySubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public Page<SalesSettlementRecord> searchSettlements(String billNo, String platformName, String status, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        if (!canBrowseFinanceDocs(currentUser, visibleOwnerIds)) {
            return Page.empty(pageable);
        }
        return salesSettlementRecordRepository.findAll((Specification<SalesSettlementRecord>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(billNo)) {
                predicates.add(cb.like(root.get("billNo"), "%" + billNo.trim() + "%"));
            }
            if (hasText(platformName)) {
                predicates.add(cb.like(root.get("platformName"), "%" + platformName.trim() + "%"));
            }
            if (hasText(status)) {
                List<String> statusList = splitStatuses(status);
                if (statusList.size() == 1) {
                    predicates.add(cb.equal(root.get("status"), statusList.get(0)));
                } else {
                    predicates.add(root.get("status").in(statusList));
                }
            }
            if (!isAdmin(currentUser)) {
                Subquery<Integer> visibilitySubquery = query.subquery(Integer.class);
                var settlementItemRoot = visibilitySubquery.from(SalesSettlementItem.class);
                var reconciliationItemRoot = visibilitySubquery.from(SalesReconciliationItem.class);
                var orderRoot = visibilitySubquery.from(SalesOrder.class);
                visibilitySubquery.select(cb.literal(1))
                        .where(
                                cb.equal(settlementItemRoot.get("settlement").get("id"), root.get("id")),
                                cb.equal(reconciliationItemRoot.get("reconciliation").get("id"), settlementItemRoot.get("reconciliationId")),
                                cb.equal(orderRoot.get("id"), reconciliationItemRoot.get("salesOrderId")),
                                orderRoot.get("createdBy").in(visibleOwnerIds)
                        );
                predicates.add(cb.exists(visibilitySubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public SalesReconciliation getReconciliation(Long id) {
        SalesReconciliation entity = salesReconciliationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("销售对账单不存在"));
        entity.setItems(salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(id));
        assertCurrentUserCanViewSalesReconciliation(entity);
        entity.setOmsOrderNoSummary(joinDistinct(entity.getItems().stream().map(SalesReconciliationItem::getOmsOrderNo).toList(), "/"));
        entity.setOrderNoSummary(joinDistinct(entity.getItems().stream()
                .map(item -> joinDistinct(Arrays.asList(item.getOmsOrderNo(), item.getPlatformOrderNo()), "/"))
                .toList(), " / "));
        return entity;
    }

    public SalesOutputInvoice getOutputInvoice(Long id) {
        SalesOutputInvoice entity = salesOutputInvoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("销项发票不存在"));
        entity.setItems(salesOutputInvoiceItemRepository.findByInvoiceIdOrderByIdAsc(id));
        assertCurrentUserCanViewSalesOutputInvoice(entity);
        return entity;
    }

    public SalesSettlementRecord getSettlement(Long id) {
        SalesSettlementRecord entity = salesSettlementRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("销售结算单不存在"));
        entity.setItems(salesSettlementItemRepository.findBySettlementIdOrderByIdAsc(id));
        assertCurrentUserCanViewSalesSettlement(entity);
        return entity;
    }

    @Transactional
    public SalesReconciliation createReconciliation(List<Long> orderIds,
                                                    LocalDate periodStart,
                                                    LocalDate periodEnd,
                                                    LocalDate reconciliationDate,
                                                    String platformReconciliationNo,
                                                    String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销售对账单生成");
        List<SalesOrder> orders = loadSalesOrders(orderIds);
        ensureSamePlatform(orders);
        ensureOrdersEligibleForReconciliation(orders);

        SalesOrder first = orders.get(0);
        SalesReconciliation reconciliation = new SalesReconciliation();
        reconciliation.setBillNo(generateBillNo("SR"));
        reconciliation.setPlatformName(trim(first.getPlatformName()));
        reconciliation.setPartyATitle(trim(first.getPartyATitle()));
        reconciliation.setOperationEntityTitle(trim(first.getOperationEntityTitle()));
        reconciliation.setPeriodStart(periodStart);
        reconciliation.setPeriodEnd(periodEnd);
        reconciliation.setReconciliationDate(reconciliationDate == null ? LocalDate.now() : reconciliationDate);
        reconciliation.setStatus("已对账");
        reconciliation.setRemark(trim(remark));
        reconciliation.setTotalAmount(sumSalesAmounts(orders));
        reconciliation.setPlatformReconciliationNo(resolveInitialPlatformReconciliationNo(platformReconciliationNo, orders));
        reconciliation.setAttachmentUrl(resolveDefaultReconciliationAttachmentUrl(orders));
        SalesReconciliation saved = salesReconciliationRepository.save(reconciliation);
        if (!hasText(saved.getPlatformReconciliationNo())) {
            saved.setPlatformReconciliationNo(saved.getBillNo());
            saved = salesReconciliationRepository.save(saved);
        }

        List<SalesReconciliationItem> items = new ArrayList<>();
        for (SalesOrder order : orders) {
            SalesReconciliationItem item = new SalesReconciliationItem();
            item.setReconciliation(saved);
            item.setSalesOrderId(order.getId());
            item.setOmsOrderNo(order.getOmsOrderNo());
            item.setPlatformOrderNo(order.getPlatformOrderNo());
            item.setPlatformName(order.getPlatformName());
            item.setOrderStatus(order.getStatus());
            item.setQuantity(order.getQuantity());
            item.setModel(order.getModel());
            item.setProductName(order.getProductName());
            item.setLineAmount(safeAmount(order.getTaxIncludedTotal()));
            items.add(item);
            applyReconciliationWriteback(order, saved);
        }
        salesReconciliationItemRepository.saveAll(items);
        saved.setItems(items);
        syncReconciliationToUpstreamPurchaseChain(saved, new ArrayList<>(orders));
        operationLogService.log("SYSTEM", "生成销售对账单", "SALES_RECONCILIATION", saved.getBillNo(),
                "订单数=" + orders.size());
        return saved;
    }

    @Transactional
    public SalesOutputInvoice createOutputInvoice(List<Long> reconciliationIds,
                                                  LocalDate invoiceDate,
                                                  String invoiceNumber,
                                                  String attachmentUrl,
                                                  String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销项发票生成");
        List<SalesReconciliation> reconciliations = loadReconciliations(reconciliationIds);
        ensureSamePlatformByReconciliation(reconciliations);
        ensureReconciliationsEligibleForInvoice(reconciliations);
        Map<Long, SalesOrder> orderMap = loadOrdersByReconciliationIds(reconciliationIds);
        ensurePurchasePrerequisitesForOutputInvoice(new ArrayList<>(orderMap.values()));

        SalesReconciliation first = reconciliations.get(0);
        SalesOutputInvoice invoice = new SalesOutputInvoice();
        invoice.setBillNo(generateBillNo("SOI"));
        invoice.setInvoiceNumber(hasText(invoiceNumber) ? invoiceNumber.trim() : null);
        invoice.setPlatformName(trim(first.getPlatformName()));
        invoice.setPartyATitle(trim(first.getPartyATitle()));
        invoice.setInvoiceDate(invoiceDate == null ? LocalDate.now() : invoiceDate);
        invoice.setStatus("已开票");
        invoice.setAttachmentUrl(trim(attachmentUrl));
        invoice.setRemark(trim(remark));
        invoice.setTotalAmount(reconciliations.stream()
                .map(SalesReconciliation::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        SalesOutputInvoice saved = salesOutputInvoiceRepository.save(invoice);

        List<SalesOutputInvoiceItem> items = new ArrayList<>();
        for (SalesReconciliation reconciliation : reconciliations) {
            SalesOutputInvoiceItem item = new SalesOutputInvoiceItem();
            item.setInvoice(saved);
            item.setReconciliationId(reconciliation.getId());
            item.setReconciliationBillNo(reconciliation.getBillNo());
            item.setPlatformName(reconciliation.getPlatformName());
            item.setLineAmount(safeAmount(reconciliation.getTotalAmount()));
            items.add(item);
            reconciliation.setStatus("已开票");
            salesReconciliationRepository.save(reconciliation);
        }
        salesOutputInvoiceItemRepository.saveAll(items);
        saved.setItems(items);

        for (SalesOrder order : orderMap.values()) {
            applyInvoiceWriteback(order, saved);
        }
        syncOutputInvoiceToUpstreamPurchaseChain(saved, new ArrayList<>(orderMap.values()));
        sendMergedInvoiceIssuedDingTalk(saved, new ArrayList<>(orderMap.values()));
        operationLogService.log("SYSTEM", "生成销项发票", "SALES_OUTPUT_INVOICE", saved.getBillNo(),
                "关联销售对账单=" + reconciliations.stream().map(SalesReconciliation::getBillNo).collect(Collectors.joining(",")));
        return saved;
    }

    @Transactional
    public SalesReconciliation updateReconciliation(Long reconciliationId,
                                                    String platformReconciliationNo,
                                                    String attachmentUrl,
                                                    String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销售对账单更新");
        SalesReconciliation reconciliation = salesReconciliationRepository.findById(reconciliationId)
                .orElseThrow(() -> new RuntimeException("销售对账单不存在"));
        if ("已作废".equals(trim(reconciliation.getStatus()))) {
            throw new RuntimeException("已作废的销售对账单不能再编辑");
        }
        reconciliation.setPlatformReconciliationNo(trim(platformReconciliationNo));
        reconciliation.setAttachmentUrl(trim(attachmentUrl));
        if (remark != null) {
            reconciliation.setRemark(remark.trim());
        }
        SalesReconciliation saved = salesReconciliationRepository.save(reconciliation);
        syncReconciliationWritebackToOrders(saved);
        saved.setItems(salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliationId));
        Map<Long, SalesOrder> orderMap = loadOrdersByReconciliationIds(List.of(reconciliationId));
        syncReconciliationToUpstreamPurchaseChain(saved, new ArrayList<>(orderMap.values()));
        operationLogService.log("SYSTEM", "更新销售对账单", "SALES_RECONCILIATION", saved.getBillNo(),
                "甲方对账单号=" + nullToDash(saved.getPlatformReconciliationNo()));
        return saved;
    }

    @Transactional
    public SalesSettlementRecord createSettlement(List<Long> invoiceIds,
                                                  List<Long> reconciliationIds,
                                                  LocalDate settlementDate,
                                                  String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销售结算单生成");
        List<SalesOutputInvoice> invoices = loadInvoices(invoiceIds);
        List<SalesReconciliation> directReconciliations = loadReconciliations(reconciliationIds);
        if (invoices.isEmpty() && directReconciliations.isEmpty()) {
            throw new RuntimeException("请至少选择一个销项发票或销售对账单");
        }

        String platformName = !invoices.isEmpty() ? trim(invoices.get(0).getPlatformName()) : trim(directReconciliations.get(0).getPlatformName());
        SalesSettlementRecord settlement = new SalesSettlementRecord();
        settlement.setBillNo(generateBillNo("SST"));
        settlement.setPlatformName(platformName);
        settlement.setPartyATitle(!invoices.isEmpty() ? trim(invoices.get(0).getPartyATitle()) : trim(directReconciliations.get(0).getPartyATitle()));
        settlement.setSettlementDate(settlementDate == null ? LocalDate.now() : settlementDate);
        settlement.setStatus("待回款");
        settlement.setRefundStatus("未回款");
        settlement.setRemark(trim(remark));
        settlement.setTotalAmount(resolveSettlementAmount(invoices, directReconciliations));
        SalesSettlementRecord saved = salesSettlementRecordRepository.save(settlement);

        Map<Long, SalesReconciliation> reconciliationMap = new LinkedHashMap<>();
        List<SalesSettlementItem> items = new ArrayList<>();
        for (SalesOutputInvoice invoice : invoices) {
            invoice.setStatus("待回款");
            salesOutputInvoiceRepository.save(invoice);
            for (SalesOutputInvoiceItem invoiceItem : salesOutputInvoiceItemRepository.findByInvoiceIdOrderByIdAsc(invoice.getId())) {
                SalesSettlementItem item = new SalesSettlementItem();
                item.setSettlement(saved);
                item.setInvoiceId(invoice.getId());
                item.setInvoiceBillNo(invoice.getBillNo());
                item.setReconciliationId(invoiceItem.getReconciliationId());
                item.setReconciliationBillNo(invoiceItem.getReconciliationBillNo());
                item.setLineAmount(safeAmount(invoiceItem.getLineAmount()));
                items.add(item);
                salesReconciliationRepository.findById(invoiceItem.getReconciliationId())
                        .ifPresent(reconciliation -> reconciliationMap.put(reconciliation.getId(), reconciliation));
            }
        }
        for (SalesReconciliation reconciliation : directReconciliations) {
            reconciliationMap.put(reconciliation.getId(), reconciliation);
        }
        for (SalesReconciliation reconciliation : reconciliationMap.values()) {
            if (items.stream().noneMatch(item -> Objects.equals(item.getReconciliationId(), reconciliation.getId()))) {
                SalesSettlementItem item = new SalesSettlementItem();
                item.setSettlement(saved);
                item.setReconciliationId(reconciliation.getId());
                item.setReconciliationBillNo(reconciliation.getBillNo());
                item.setLineAmount(safeAmount(reconciliation.getTotalAmount()));
                items.add(item);
            }
            reconciliation.setStatus("待回款");
            salesReconciliationRepository.save(reconciliation);
        }
        salesSettlementItemRepository.saveAll(items);
        saved.setItems(items);

        Map<Long, SalesOrder> orderMap = loadOrdersByReconciliationIds(new ArrayList<>(reconciliationMap.keySet()));
        for (SalesOrder order : orderMap.values()) {
            applySettlementWriteback(order, saved);
        }
        operationLogService.log("SYSTEM", "生成销售结算单", "SALES_SETTLEMENT", saved.getBillNo(),
                "来源条目=" + items.size());
        return saved;
    }

    @Transactional
    public SalesSettlementRecord updateSettlementRefund(Long settlementId,
                                                        String refundStatus,
                                                        LocalDate refundDate,
                                                        String attachmentUrl,
                                                        String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销售结算回款维护");
        SalesSettlementRecord settlement = salesSettlementRecordRepository.findById(settlementId)
                .orElseThrow(() -> new RuntimeException("销售结算单不存在"));
        String normalizedRefundStatus = hasText(refundStatus) ? refundStatus.trim() : settlement.getRefundStatus();
        settlement.setRefundStatus(normalizedRefundStatus);
        settlement.setRefundDate(refundDate);
        if (hasText(attachmentUrl)) {
            settlement.setAttachmentUrl(attachmentUrl.trim());
        }
        if (remark != null) {
            settlement.setRemark(remark.trim());
        }
        settlement.setStatus(resolveSalesSettlementStatus(normalizedRefundStatus));
        SalesSettlementRecord saved = salesSettlementRecordRepository.save(settlement);

        List<SalesSettlementItem> items = salesSettlementItemRepository.findBySettlementIdOrderByIdAsc(settlementId);
        saved.setItems(items);
        Set<Long> reconciliationIds = items.stream()
                .map(SalesSettlementItem::getReconciliationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> invoiceIds = items.stream()
                .map(SalesSettlementItem::getInvoiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Long invoiceId : invoiceIds) {
            salesOutputInvoiceRepository.findById(invoiceId).ifPresent(invoice -> {
                invoice.setStatus(saved.getStatus());
                salesOutputInvoiceRepository.save(invoice);
            });
        }
        for (Long reconciliationId : reconciliationIds) {
            salesReconciliationRepository.findById(reconciliationId).ifPresent(reconciliation -> {
                reconciliation.setStatus(saved.getStatus());
                salesReconciliationRepository.save(reconciliation);
            });
        }

        Map<Long, SalesOrder> orderMap = loadOrdersByReconciliationIds(new ArrayList<>(reconciliationIds));
        for (SalesOrder order : orderMap.values()) {
            order.setPlatformRefundStatus(normalizedRefundStatus);
            if ("已回款".equals(normalizedRefundStatus)) {
                order.setStatus("已结算");
            } else {
                order.setStatus("已开票待结算");
            }
            salesOrderRepository.save(order);
        }
        operationLogService.log("SYSTEM", "更新销售结算回款状态", "SALES_SETTLEMENT", saved.getBillNo(),
                "回款状态=" + normalizedRefundStatus);
        return saved;
    }

    @Transactional
    public SalesReconciliation voidReconciliation(Long reconciliationId) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销售对账单作废");
        SalesReconciliation reconciliation = getReconciliation(reconciliationId);
        boolean hasDownstream = hasActiveInvoiceReferenceForReconciliation(reconciliationId)
                || hasActiveSettlementReferenceForReconciliation(reconciliationId);
        if (hasDownstream) {
            throw new RuntimeException("该销售对账单已被销项发票或销售结算单引用，不能直接作废");
        }
        for (SalesReconciliationItem item : reconciliation.getItems()) {
            salesOrderRepository.findById(item.getSalesOrderId()).ifPresent(order -> {
                order.setPlatformReconciliationNo(null);
                order.setPlatformReconciliationUrl(null);
                order.setReconciliationDate(null);
                if ("已对账未开票".equals(order.getStatus())) {
                    order.setStatus(resolveOrderStatusBeforeReconciliation(order, item));
                }
                salesOrderRepository.save(order);
            });
        }
        reconciliation.setStatus("已作废");
        SalesReconciliation saved = salesReconciliationRepository.save(reconciliation);
        operationLogService.log("SYSTEM", "作废销售对账单", "SALES_RECONCILIATION", saved.getBillNo(), "作废成功");
        return saved;
    }

    @Transactional
    public SalesOutputInvoice voidOutputInvoice(Long invoiceId) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销项发票作废");
        SalesOutputInvoice invoice = getOutputInvoice(invoiceId);
        boolean hasDownstream = hasActiveSettlementReferenceForInvoice(invoiceId);
        if (hasDownstream) {
            throw new RuntimeException("该销项发票已被销售结算单引用，不能直接作废");
        }
        for (SalesOutputInvoiceItem item : invoice.getItems()) {
            salesReconciliationRepository.findById(item.getReconciliationId()).ifPresent(reconciliation -> {
                reconciliation.setStatus("已对账");
                salesReconciliationRepository.save(reconciliation);
                for (SalesReconciliationItem reconItem : salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliation.getId())) {
                    salesOrderRepository.findById(reconItem.getSalesOrderId()).ifPresent(order -> {
                        order.setInvoiceNumber(null);
                        order.setInvoiceIssuedDate(null);
                        order.setInvoiceUrl(null);
                        if (!"已结算".equals(order.getStatus())) {
                            order.setStatus("已对账未开票");
                        }
                        salesOrderRepository.save(order);
                    });
                }
            });
        }
        invoice.setStatus("已作废");
        SalesOutputInvoice saved = salesOutputInvoiceRepository.save(invoice);
        operationLogService.log("SYSTEM", "作废销项发票", "SALES_OUTPUT_INVOICE", saved.getBillNo(), "作废成功");
        return saved;
    }

    @Transactional
    public SalesSettlementRecord voidSettlement(Long settlementId) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "销售结算单作废");
        SalesSettlementRecord settlement = getSettlement(settlementId);
        for (SalesSettlementItem item : settlement.getItems()) {
            if (item.getInvoiceId() != null) {
                salesOutputInvoiceRepository.findById(item.getInvoiceId()).ifPresent(invoice -> {
                    invoice.setStatus("已开票");
                    salesOutputInvoiceRepository.save(invoice);
                });
            }
            if (item.getReconciliationId() != null) {
                salesReconciliationRepository.findById(item.getReconciliationId()).ifPresent(reconciliation -> {
                    reconciliation.setStatus(item.getInvoiceId() != null ? "已开票" : "已对账");
                    salesReconciliationRepository.save(reconciliation);
                    for (SalesReconciliationItem reconItem : salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliation.getId())) {
                        salesOrderRepository.findById(reconItem.getSalesOrderId()).ifPresent(order -> {
                            order.setSettlementNo(null);
                            order.setPlatformRefundStatus(null);
                            order.setStatus(item.getInvoiceId() != null ? "已开票待结算" : "已对账未开票");
                            salesOrderRepository.save(order);
                        });
                    }
                });
            }
        }
        settlement.setStatus("已作废");
        settlement.setRefundStatus("已作废");
        SalesSettlementRecord saved = salesSettlementRecordRepository.save(settlement);
        operationLogService.log("SYSTEM", "作废销售结算单", "SALES_SETTLEMENT", saved.getBillNo(), "作废成功");
        return saved;
    }

    private List<SalesOrder> loadSalesOrders(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new RuntimeException("请选择销售订单");
        }
        List<SalesOrder> orders = salesOrderRepository.findAllById(orderIds);
        if (orders.size() != orderIds.size()) {
            throw new RuntimeException("存在已失效的销售订单，请刷新后重试");
        }
        return orders;
    }

    private void ensureOrdersEligibleForReconciliation(List<SalesOrder> orders) {
        List<String> invalidOrders = orders.stream()
                .filter(order -> !isEligibleSalesOrderStatusForReconciliation(order.getStatus()))
                .map(order -> nullToDash(firstNonBlank(order.getOmsOrderNo(), order.getPlatformOrderNo())) + "(" + nullToDash(order.getStatus()) + ")")
                .toList();
        if (!invalidOrders.isEmpty()) {
            throw new RuntimeException("仅已签收/已妥投/已到货的销售订单可生成销售对账单，当前不满足：" + String.join("，", invalidOrders));
        }
    }

    private void ensureReconciliationsEligibleForInvoice(List<SalesReconciliation> reconciliations) {
        List<String> invalidBills = reconciliations.stream()
                .filter(item -> !"已对账".equals(trim(item.getStatus())))
                .map(item -> nullToDash(item.getBillNo()) + "(" + nullToDash(item.getStatus()) + ")")
                .toList();
        if (!invalidBills.isEmpty()) {
            throw new RuntimeException("仅“已对账”状态的销售对账单可合并生成销项发票，当前不满足：" + String.join("，", invalidBills));
        }
    }

    private void enrichReconciliationSummaries(List<SalesReconciliation> reconciliations) {
        if (reconciliations == null || reconciliations.isEmpty()) {
            return;
        }
        for (SalesReconciliation reconciliation : reconciliations) {
            if (reconciliation == null || reconciliation.getId() == null) {
                continue;
            }
            List<SalesReconciliationItem> items = salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliation.getId());
            reconciliation.setOmsOrderNoSummary(joinDistinct(items.stream().map(SalesReconciliationItem::getOmsOrderNo).toList(), "/"));
            reconciliation.setOrderNoSummary(joinDistinct(items.stream()
                    .map(item -> joinDistinct(Arrays.asList(item.getOmsOrderNo(), item.getPlatformOrderNo()), "/"))
                    .toList(), " / "));
        }
    }

    private boolean hasActiveInvoiceReferenceForReconciliation(Long reconciliationId) {
        return salesOutputInvoiceItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                .map(SalesOutputInvoiceItem::getInvoice)
                .filter(Objects::nonNull)
                .anyMatch(invoice -> !"已作废".equals(trim(invoice.getStatus())));
    }

    private boolean hasActiveSettlementReferenceForReconciliation(Long reconciliationId) {
        return salesSettlementItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                .map(SalesSettlementItem::getSettlement)
                .filter(Objects::nonNull)
                .anyMatch(settlement -> !"已作废".equals(trim(settlement.getStatus())));
    }

    private boolean hasActiveSettlementReferenceForInvoice(Long invoiceId) {
        return salesSettlementItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getInvoiceId(), invoiceId))
                .map(SalesSettlementItem::getSettlement)
                .filter(Objects::nonNull)
                .anyMatch(settlement -> !"已作废".equals(trim(settlement.getStatus())));
    }

    private List<SalesReconciliation> loadReconciliations(List<Long> reconciliationIds) {
        if (reconciliationIds == null || reconciliationIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SalesReconciliation> reconciliations = salesReconciliationRepository.findAllById(reconciliationIds);
        if (reconciliations.size() != reconciliationIds.size()) {
            throw new RuntimeException("存在已失效的销售对账单，请刷新后重试");
        }
        return reconciliations;
    }

    private List<SalesOutputInvoice> loadInvoices(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SalesOutputInvoice> invoices = salesOutputInvoiceRepository.findAllById(invoiceIds);
        if (invoices.size() != invoiceIds.size()) {
            throw new RuntimeException("存在已失效的销项发票，请刷新后重试");
        }
        return invoices;
    }

    private void ensureSamePlatform(List<SalesOrder> orders) {
        String platformName = trim(orders.get(0).getPlatformName());
        boolean same = orders.stream().allMatch(order -> Objects.equals(platformName, trim(order.getPlatformName())));
        if (!same) {
            throw new RuntimeException("销售对账单仅支持同一甲方抬头合并");
        }
    }

    private void ensureSamePlatformByReconciliation(List<SalesReconciliation> reconciliations) {
        String platformName = trim(reconciliations.get(0).getPlatformName());
        boolean same = reconciliations.stream().allMatch(item -> Objects.equals(platformName, trim(item.getPlatformName())));
        if (!same) {
            throw new RuntimeException("销项发票仅支持同一甲方抬头的销售对账单合并");
        }
    }

    private String resolveDefaultPlatformReconciliationNo(List<SalesOrder> orders) {
        return joinDistinct(orders.stream().map(SalesOrder::getPlatformReconciliationNo).toList());
    }

    private String resolveInitialPlatformReconciliationNo(String platformReconciliationNo, List<SalesOrder> orders) {
        String manualValue = normalizeBlankToNull(platformReconciliationNo);
        if (manualValue != null) {
            return manualValue;
        }
        return normalizeBlankToNull(resolveDefaultPlatformReconciliationNo(orders));
    }

    private String resolveDefaultReconciliationAttachmentUrl(List<SalesOrder> orders) {
        LinkedHashSet<String> urls = orders.stream()
                .map(SalesOrder::getPlatformReconciliationUrl)
                .map(this::normalizeBlankToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (urls.size() == 1) {
            return urls.iterator().next();
        }
        return null;
    }

    private BigDecimal sumSalesAmounts(List<SalesOrder> orders) {
        return orders.stream()
                .map(SalesOrder::getTaxIncludedTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, SalesOrder> loadOrdersByReconciliationIds(List<Long> reconciliationIds) {
        Map<Long, SalesOrder> result = new LinkedHashMap<>();
        for (Long reconciliationId : reconciliationIds) {
            for (SalesReconciliationItem item : salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliationId)) {
                salesOrderRepository.findById(item.getSalesOrderId()).ifPresent(order -> result.put(order.getId(), order));
            }
        }
        return result;
    }

    private void applyReconciliationWriteback(SalesOrder order, SalesReconciliation reconciliation) {
        order.setPlatformReconciliationNo(normalizeBlankToNull(reconciliation.getPlatformReconciliationNo()));
        order.setPlatformReconciliationUrl(normalizeBlankToNull(reconciliation.getAttachmentUrl()));
        order.setReconciliationDate(reconciliation.getReconciliationDate());
        if (!hasText(order.getInvoiceNumber())) {
            order.setStatus("已对账未开票");
        }
        salesOrderRepository.save(order);
    }

    private void syncReconciliationWritebackToOrders(SalesReconciliation reconciliation) {
        if (reconciliation == null || reconciliation.getId() == null) {
            return;
        }
        List<SalesReconciliationItem> items = salesReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliation.getId());
        for (SalesReconciliationItem item : items) {
            if (item.getSalesOrderId() == null) {
                continue;
            }
            salesOrderRepository.findById(item.getSalesOrderId()).ifPresent(order -> applyReconciliationWriteback(order, reconciliation));
        }
    }

    private boolean isEligibleSalesOrderStatusForReconciliation(String status) {
        String normalized = trim(status);
        if (!hasText(normalized)) {
            return false;
        }
        return "已签收".equals(normalized)
                || "已妥投".equals(normalized)
                || "已到货".equals(normalized)
                || "已到货待回单".equals(normalized);
    }

    private String resolveOrderStatusBeforeReconciliation(SalesOrder order, SalesReconciliationItem item) {
        String originalStatus = item == null ? null : normalizeBlankToNull(item.getOrderStatus());
        if (originalStatus != null) {
            return originalStatus;
        }
        if (order == null) {
            return "已发货";
        }
        String receiptStatus = trim(order.getReceiptStatus());
        String receiptFlowStage = trim(order.getReceiptFlowStage());
        if ((hasText(order.getReceiptUrl()) && ("已上传".equals(receiptStatus) || "妥投结束".equals(receiptStatus)))
                || "FLOW_COMPLETED".equals(receiptFlowStage)) {
            return "已签收";
        }
        if ("RETURN_DELIVERED".equals(receiptFlowStage)
                || "MOTHER_DELIVERED".equals(receiptFlowStage)
                || order.getMotherDeliveredAt() != null
                || order.getReturnDeliveredAt() != null) {
            return "已到货";
        }
        return "已发货";
    }

    private void applyInvoiceWriteback(SalesOrder order, SalesOutputInvoice invoice) {
        order.setInvoiceNumber(invoice.getInvoiceNumber());
        order.setInvoiceIssuedDate(invoice.getInvoiceDate());
        order.setInvoiceUrl(normalizeBlankToNull(invoice.getAttachmentUrl()));
        order.setStatus("已开票待结算");
        salesOrderRepository.save(order);
    }

    private void ensurePurchasePrerequisitesForOutputInvoice(List<SalesOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        LinkedHashSet<String> blockedOmsOrders = new LinkedHashSet<>();
        for (SalesOrder order : orders) {
            if (order == null) {
                continue;
            }
            String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
            if (omsOrderNo == null) {
                continue;
            }
            List<PurchaseOrder> purchaseOrders = resolveRelevantPurchaseOrdersForOutputInvoice(order);
            if (purchaseOrders.isEmpty()) {
                continue;
            }
            boolean ready = purchaseOrders.stream().allMatch(this::hasCompletedPurchaseInvoiceChain);
            if (!ready) {
                blockedOmsOrders.add(omsOrderNo);
            }
        }
        if (!blockedOmsOrders.isEmpty()) {
            throw new RuntimeException("生成销项发票前，请先完成对应采购对账，并补齐进项发票号和附件：" + String.join("，", blockedOmsOrders));
        }
    }

    private List<PurchaseOrder> resolveRelevantPurchaseOrdersForOutputInvoice(SalesOrder order) {
        if (order == null) {
            return List.of();
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo == null) {
            return List.of();
        }
        List<PurchaseOrder> candidates = purchaseOrderRepository.findByOmsOrderNo(omsOrderNo);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, PurchaseOrder> matched = new LinkedHashMap<>();
        Long salesOrderId = order.getId();
        if (salesOrderId != null) {
            for (PurchaseOrder candidate : candidates) {
                if (candidate == null || candidate.getId() == null) {
                    continue;
                }
                if (Objects.equals(candidate.getSourceSalesOrderId(), salesOrderId)
                        || parseMergedSalesOrderIds(candidate.getMergedSalesOrderIds()).contains(salesOrderId)) {
                    matched.put(candidate.getId(), candidate);
                }
            }
        }
        if (!matched.isEmpty()) {
            return new ArrayList<>(matched.values());
        }

        String expectedSupplier = firstNonBlank(order.getDeliveryParty(), order.getShippingParty());
        if (expectedSupplier == null) {
            return List.of();
        }
        for (PurchaseOrder candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (expectedSupplier.equals(normalizeBlankToNull(candidate.getSupplier()))) {
                matched.put(candidate.getId(), candidate);
            }
        }
        return new ArrayList<>(matched.values());
    }

    private boolean hasCompletedPurchaseInvoiceChain(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getId() == null) {
            return false;
        }
        List<PurchaseReconciliationItem> reconciliationItems = purchaseReconciliationItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getPurchaseOrderId(), purchaseOrder.getId()))
                .toList();
        if (reconciliationItems.isEmpty()) {
            return false;
        }
        for (PurchaseReconciliationItem reconciliationItem : reconciliationItems) {
            if (reconciliationItem == null
                    || reconciliationItem.getReconciliation() == null
                    || reconciliationItem.getReconciliation().getId() == null
                    || "已作废".equals(trim(reconciliationItem.getReconciliation().getStatus()))) {
                continue;
            }
            Long reconciliationId = reconciliationItem.getReconciliation().getId();
            List<PurchaseInputInvoiceItem> invoiceItems = purchaseInputInvoiceItemRepository.findAll().stream()
                    .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                    .toList();
            for (PurchaseInputInvoiceItem invoiceItem : invoiceItems) {
                if (invoiceItem == null || invoiceItem.getInvoice() == null || invoiceItem.getInvoice().getId() == null) {
                    continue;
                }
                PurchaseInputInvoiceRecord invoice = purchaseInputInvoiceRecordRepository.findById(invoiceItem.getInvoice().getId()).orElse(null);
                if (invoice == null || "已作废".equals(trim(invoice.getStatus()))) {
                    continue;
                }
                if (hasActualPurchaseInvoiceNumber(invoice) && hasText(invoice.getAttachmentUrl())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasActualPurchaseInvoiceNumber(PurchaseInputInvoiceRecord invoice) {
        if (invoice == null) {
            return false;
        }
        String invoiceNumber = normalizeBlankToNull(invoice.getInvoiceNumber());
        if (invoiceNumber == null) {
            return false;
        }
        String billNo = normalizeBlankToNull(invoice.getBillNo());
        return billNo == null || !invoiceNumber.equalsIgnoreCase(billNo);
    }

    private List<Long> parseMergedSalesOrderIds(String mergedSalesOrderIds) {
        if (mergedSalesOrderIds == null || mergedSalesOrderIds.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : mergedSalesOrderIds.split(",")) {
            String normalized = normalizeBlankToNull(part);
            if (normalized == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(normalized));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private void sendMergedInvoiceIssuedDingTalk(SalesOutputInvoice invoice, List<SalesOrder> orders) {
        try {
            if (invoice == null || orders == null || orders.isEmpty()) {
                return;
            }

            List<SalesOrder> normalizedOrders = orders.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(SalesOrder::getId, item -> item, (left, right) -> left, LinkedHashMap::new),
                            map -> new ArrayList<>(map.values())
                    ));
            if (normalizedOrders.isEmpty()) {
                return;
            }

            List<User> allUsers = userRepository.findAll();
            LinkedHashSet<String> atPhones = new LinkedHashSet<>();
            List<String> financeNames = new ArrayList<>();
            List<String> salesOwnerNames = new ArrayList<>();

            for (User user : allUsers) {
                if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                    continue;
                }
                String display = userDisplay(user);
                if (hasFinancePermission(user)) {
                    if (!financeNames.contains(display)) {
                        financeNames.add(display);
                    }
                    collectAtPhone(atPhones, user.getPhone());
                }

                if (normalizedOrders.stream().anyMatch(order -> matchesOrderSalesOwner(user, order))) {
                    if (!salesOwnerNames.contains(display)) {
                        salesOwnerNames.add(display);
                    }
                    collectAtPhone(atPhones, user.getPhone());
                }
            }

            String contractNos = joinDistinct(normalizedOrders.stream().map(SalesOrder::getOfflineContractNo).toList());
            String omsOrderNos = joinDistinct(normalizedOrders.stream().map(SalesOrder::getOmsOrderNo).toList());
            String platformOrderNos = joinDistinct(normalizedOrders.stream().map(SalesOrder::getPlatformOrderNo).toList());
            String reconciliationNos = joinDistinct(normalizedOrders.stream().map(SalesOrder::getPlatformReconciliationNo).toList());
            String salesNames = joinDistinct(normalizedOrders.stream().map(SalesOrder::getEcommerceSalesName).toList());
            String buyerTitles = joinDistinct(normalizedOrders.stream().map(this::resolveNotifyPartyATitle).toList());
            String sellerTitles = joinDistinct(normalizedOrders.stream().map(this::resolveNotifySalesSellerTitle).toList());
            String deliveryPartyTitles = joinDistinct(normalizedOrders.stream().map(this::resolveNotifySalesDeliveryPartyTitle).toList());

            String title = "🧾 销项发票已生成，待客户结算";
            String text = "## 🧾 销项发票已生成，待客户结算\n\n"
                    + "**销项发票单号：** " + nullToDash(invoice.getBillNo()) + "\n\n"
                    + "**发票号码：** " + nullToDash(invoice.getInvoiceNumber()) + "\n\n"
                    + "**购买方抬头：** " + nullToDash(firstNonBlank(invoice.getPartyATitle(), invoice.getPlatformName(), buyerTitles)) + "\n\n"
                    + "**销售方抬头：** " + nullToDash(sellerTitles) + "\n\n"
                    + "**交付方：** " + nullToDash(deliveryPartyTitles) + "\n\n"
                    + "**开票日期：** " + (invoice.getInvoiceDate() == null ? "-" : invoice.getInvoiceDate().toString()) + "\n\n"
                    + "**合同号：** " + contractNos + "\n\n"
                    + "**OMS订单号：** " + omsOrderNos + "\n\n"
                    + "**甲方订单号：** " + platformOrderNos + "\n\n"
                    + "**甲方对账单号：** " + reconciliationNos + "\n\n"
                    + "**工业电商业务员：** " + salesNames + "\n\n"
                    + "**关联订单数：** " + normalizedOrders.size() + "\n\n"
                    + "**当前状态：** 已开票待结算\n\n"
                    + "---\n*来自 OMS 订单系统*";

            dingTalkService.sendMarkdownMessage(title, text,
                    atPhones.isEmpty() ? null : new ArrayList<>(atPhones));
        } catch (Exception e) {
            System.err.println("发送合并已开票待结算钉钉通知失败: " + e.getMessage());
        }
    }

    private void syncReconciliationToUpstreamPurchaseChain(SalesReconciliation salesReconciliation, List<SalesOrder> orders) {
        if (salesReconciliation == null || orders == null || orders.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> upstreamSalesOrderIds = new LinkedHashSet<>();
        for (SalesOrder order : orders) {
            if (order == null) {
                continue;
            }
            List<PurchaseOrder> upstreamPurchaseOrders = resolveUpstreamPurchaseOrdersForInputSync(order);
            for (PurchaseOrder purchaseOrder : upstreamPurchaseOrders) {
                if (purchaseOrder == null || purchaseOrder.getId() == null) {
                    continue;
                }
                upsertSyncedPurchaseReconciliation(purchaseOrder, salesReconciliation);
            }
            Long upstreamSalesOrderId = extractUpstreamSalesOrderId(order);
            if (upstreamSalesOrderId != null) {
                upstreamSalesOrderIds.add(upstreamSalesOrderId);
            }
        }
        if (!upstreamSalesOrderIds.isEmpty()) {
            salesOrderService.notifyReadyForInvoiceIfPurchaseChainCompleted(new ArrayList<>(upstreamSalesOrderIds));
        }
    }

    private void syncOutputInvoiceToUpstreamPurchaseChain(SalesOutputInvoice outputInvoice, List<SalesOrder> orders) {
        if (outputInvoice == null || orders == null || orders.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> upstreamSalesOrderIds = new LinkedHashSet<>();
        for (SalesOrder order : orders) {
            if (order == null) {
                continue;
            }
            List<PurchaseOrder> upstreamPurchaseOrders = resolveUpstreamPurchaseOrdersForInputSync(order);
            for (PurchaseOrder purchaseOrder : upstreamPurchaseOrders) {
                if (purchaseOrder == null || purchaseOrder.getId() == null) {
                    continue;
                }
                PurchaseReconciliation reconciliation = findOrCreateSyncedPurchaseReconciliation(purchaseOrder, outputInvoice);
                PurchaseInputInvoiceRecord inputInvoice = findActivePurchaseInputInvoiceByReconciliationId(reconciliation.getId());
                if (inputInvoice == null) {
                    inputInvoice = createSyncedPurchaseInputInvoice(reconciliation, purchaseOrder, outputInvoice);
                } else {
                    inputInvoice = updateSyncedPurchaseInputInvoice(inputInvoice, purchaseOrder, outputInvoice);
                }
                reconciliation.setStatus("已收票");
                purchaseReconciliationRepository.save(reconciliation);
                purchaseOrder.setInvoiceNumber(inputInvoice.getInvoiceNumber());
                if (!hasText(purchaseOrder.getReconciliationStatus())) {
                    purchaseOrder.setReconciliationStatus("已对账");
                }
                purchaseOrderRepository.save(purchaseOrder);
            }
            Long upstreamSalesOrderId = extractUpstreamSalesOrderId(order);
            if (upstreamSalesOrderId != null) {
                upstreamSalesOrderIds.add(upstreamSalesOrderId);
            }
        }
        if (!upstreamSalesOrderIds.isEmpty()) {
            salesOrderService.notifyReadyForInvoiceIfPurchaseChainCompleted(new ArrayList<>(upstreamSalesOrderIds));
        }
    }

    private List<PurchaseOrder> resolveUpstreamPurchaseOrdersForInputSync(SalesOrder order) {
        Long upstreamSalesOrderId = extractUpstreamSalesOrderId(order);
        if (order == null || upstreamSalesOrderId == null) {
            return List.of();
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo == null) {
            return List.of();
        }
        return purchaseOrderRepository.findByOmsOrderNo(omsOrderNo).stream()
                .filter(Objects::nonNull)
                .filter(candidate -> Objects.equals(candidate.getSourceSalesOrderId(), upstreamSalesOrderId)
                        || parseMergedSalesOrderIds(candidate.getMergedSalesOrderIds()).contains(upstreamSalesOrderId))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PurchaseOrder::getId, item -> item, (left, right) -> left, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private Long extractUpstreamSalesOrderId(SalesOrder order) {
        if (order == null) {
            return null;
        }
        String chainMarker = normalizeBlankToNull(order.getPurchaseOrderNo());
        if (chainMarker == null || !chainMarker.startsWith("CHAIN_FROM:")) {
            return null;
        }
        try {
            return Long.parseLong(chainMarker.substring("CHAIN_FROM:".length()).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private PurchaseReconciliation upsertSyncedPurchaseReconciliation(PurchaseOrder purchaseOrder, SalesReconciliation salesReconciliation) {
        PurchaseReconciliation reconciliation = findActivePurchaseReconciliationByPurchaseOrderId(purchaseOrder.getId());
        if (reconciliation == null) {
            reconciliation = new PurchaseReconciliation();
            reconciliation.setBillNo(generateBillNo("PR"));
        }
        reconciliation.setSupplier(trim(purchaseOrder.getSupplier()));
        reconciliation.setReconciliationDate(salesReconciliation.getReconciliationDate() == null ? LocalDate.now() : salesReconciliation.getReconciliationDate());
        String currentStatus = trim(reconciliation.getStatus());
        if (!Set.of("已收票", "待付款", "部分付款", "已付款").contains(currentStatus)) {
            reconciliation.setStatus("已对账");
        }
        reconciliation.setAttachmentUrl(normalizeBlankToNull(salesReconciliation.getAttachmentUrl()));
        String existingRemark = trim(reconciliation.getRemark());
        if (!existingRemark.contains("AUTO_SYNC_FROM_RECON:")) {
            reconciliation.setRemark(("AUTO_SYNC_FROM_RECON:" + nullToDash(salesReconciliation.getBillNo()) + " " + existingRemark).trim());
        }
        reconciliation.setTotalAmount(safeAmount(purchaseOrder.getTaxIncludedPurchaseTotal()));
        PurchaseReconciliation saved = purchaseReconciliationRepository.save(reconciliation);

        boolean linked = purchaseReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(saved.getId()).stream()
                .anyMatch(item -> Objects.equals(item.getPurchaseOrderId(), purchaseOrder.getId()));
        if (!linked) {
            PurchaseReconciliationItem item = new PurchaseReconciliationItem();
            item.setReconciliation(saved);
            item.setPurchaseOrderId(purchaseOrder.getId());
            item.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
            item.setOmsOrderNo(purchaseOrder.getOmsOrderNo());
            item.setSupplier(purchaseOrder.getSupplier());
            item.setOrderStatus(purchaseOrder.getStatus());
            item.setQuantity(purchaseOrder.getQuantity());
            item.setModel(purchaseOrder.getModel());
            item.setLineAmount(safeAmount(purchaseOrder.getTaxIncludedPurchaseTotal()));
            purchaseReconciliationItemRepository.save(item);
        }

        purchaseOrder.setReconciliationStatus("已对账");
        purchaseOrderRepository.save(purchaseOrder);
        return saved;
    }

    private PurchaseReconciliation findOrCreateSyncedPurchaseReconciliation(PurchaseOrder purchaseOrder, SalesOutputInvoice outputInvoice) {
        PurchaseReconciliation existing = findActivePurchaseReconciliationByPurchaseOrderId(purchaseOrder.getId());
        if (existing != null) {
            return existing;
        }
        PurchaseReconciliation reconciliation = new PurchaseReconciliation();
        reconciliation.setBillNo(generateBillNo("PR"));
        reconciliation.setSupplier(trim(purchaseOrder.getSupplier()));
        reconciliation.setReconciliationDate(outputInvoice.getInvoiceDate() == null ? LocalDate.now() : outputInvoice.getInvoiceDate());
        reconciliation.setStatus("已对账");
        reconciliation.setRemark("AUTO_SYNC_FROM_OUTPUT:" + nullToDash(outputInvoice.getBillNo()));
        reconciliation.setTotalAmount(safeAmount(purchaseOrder.getTaxIncludedPurchaseTotal()));
        PurchaseReconciliation saved = purchaseReconciliationRepository.save(reconciliation);

        PurchaseReconciliationItem item = new PurchaseReconciliationItem();
        item.setReconciliation(saved);
        item.setPurchaseOrderId(purchaseOrder.getId());
        item.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
        item.setOmsOrderNo(purchaseOrder.getOmsOrderNo());
        item.setSupplier(purchaseOrder.getSupplier());
        item.setOrderStatus(purchaseOrder.getStatus());
        item.setQuantity(purchaseOrder.getQuantity());
        item.setModel(purchaseOrder.getModel());
        item.setLineAmount(safeAmount(purchaseOrder.getTaxIncludedPurchaseTotal()));
        purchaseReconciliationItemRepository.save(item);

        purchaseOrder.setReconciliationStatus("已对账");
        purchaseOrderRepository.save(purchaseOrder);
        return saved;
    }

    private PurchaseReconciliation findActivePurchaseReconciliationByPurchaseOrderId(Long purchaseOrderId) {
        if (purchaseOrderId == null) {
            return null;
        }
        return purchaseReconciliationItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getPurchaseOrderId(), purchaseOrderId))
                .map(PurchaseReconciliationItem::getReconciliation)
                .filter(Objects::nonNull)
                .filter(reconciliation -> !"已作废".equals(trim(reconciliation.getStatus())))
                .max(java.util.Comparator.comparing(PurchaseReconciliation::getId))
                .orElse(null);
    }

    private PurchaseInputInvoiceRecord findActivePurchaseInputInvoiceByReconciliationId(Long reconciliationId) {
        if (reconciliationId == null) {
            return null;
        }
        return purchaseInputInvoiceItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                .map(PurchaseInputInvoiceItem::getInvoice)
                .filter(Objects::nonNull)
                .filter(invoice -> !"已作废".equals(trim(invoice.getStatus())))
                .max(java.util.Comparator.comparing(PurchaseInputInvoiceRecord::getId))
                .orElse(null);
    }

    private PurchaseInputInvoiceRecord createSyncedPurchaseInputInvoice(PurchaseReconciliation reconciliation,
                                                                        PurchaseOrder purchaseOrder,
                                                                        SalesOutputInvoice outputInvoice) {
        PurchaseInputInvoiceRecord invoice = new PurchaseInputInvoiceRecord();
        invoice.setBillNo(generateBillNo("PII"));
        invoice.setInvoiceNumber(normalizeBlankToNull(outputInvoice.getInvoiceNumber()));
        invoice.setSupplier(trim(purchaseOrder.getSupplier()));
        invoice.setInvoiceDate(outputInvoice.getInvoiceDate() == null ? LocalDate.now() : outputInvoice.getInvoiceDate());
        invoice.setStatus("已收票");
        invoice.setAttachmentUrl(normalizeBlankToNull(outputInvoice.getAttachmentUrl()));
        invoice.setRemark("AUTO_SYNC_FROM_OUTPUT:" + nullToDash(outputInvoice.getBillNo()));
        invoice.setTotalAmount(safeAmount(reconciliation.getTotalAmount()));
        PurchaseInputInvoiceRecord saved = purchaseInputInvoiceRecordRepository.save(invoice);

        PurchaseInputInvoiceItem item = new PurchaseInputInvoiceItem();
        item.setInvoice(saved);
        item.setReconciliationId(reconciliation.getId());
        item.setReconciliationBillNo(reconciliation.getBillNo());
        item.setSupplier(reconciliation.getSupplier());
        item.setLineAmount(safeAmount(reconciliation.getTotalAmount()));
        purchaseInputInvoiceItemRepository.save(item);
        return saved;
    }

    private PurchaseInputInvoiceRecord updateSyncedPurchaseInputInvoice(PurchaseInputInvoiceRecord invoice,
                                                                        PurchaseOrder purchaseOrder,
                                                                        SalesOutputInvoice outputInvoice) {
        invoice.setInvoiceNumber(normalizeBlankToNull(outputInvoice.getInvoiceNumber()));
        invoice.setSupplier(trim(purchaseOrder.getSupplier()));
        invoice.setInvoiceDate(outputInvoice.getInvoiceDate() == null ? LocalDate.now() : outputInvoice.getInvoiceDate());
        if (!"待付款".equals(trim(invoice.getStatus())) && !"已付款".equals(trim(invoice.getStatus()))) {
            invoice.setStatus("已收票");
        }
        invoice.setAttachmentUrl(normalizeBlankToNull(outputInvoice.getAttachmentUrl()));
        String existingRemark = trim(invoice.getRemark());
        if (!existingRemark.contains("AUTO_SYNC_FROM_OUTPUT:")) {
            invoice.setRemark(("AUTO_SYNC_FROM_OUTPUT:" + nullToDash(outputInvoice.getBillNo()) + " " + existingRemark).trim());
        }
        invoice.setTotalAmount(safeAmount(invoice.getTotalAmount()).max(safeAmount(outputInvoice.getTotalAmount())));
        return purchaseInputInvoiceRecordRepository.save(invoice);
    }

    private boolean hasFinancePermission(User user) {
        if (user == null || user.getPermissions() == null) {
            return false;
        }
        for (String permission : user.getPermissions().split(",")) {
            String value = permission == null ? "" : permission.trim();
            if ("settlement_finance".equalsIgnoreCase(value) || "platform_refund".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOrderSalesOwner(User currentUser, SalesOrder order) {
        if (currentUser == null || order == null) {
            return false;
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo != null) {
            return salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(omsOrderNo).stream()
                    .anyMatch(item -> matchesErpSalesOwner(currentUser, item.getCreatedBy(), item.getEcommerceSalesName()));
        }
        return matchesErpSalesOwner(currentUser, order.getCreatedBy(), order.getEcommerceSalesName());
    }

    private boolean matchesErpSalesOwner(User currentUser, Long createdBy, String ecommerceSalesName) {
        if (currentUser == null) {
            return false;
        }
        if (createdBy != null && createdBy.equals(currentUser.getId())) {
            return true;
        }
        String currentRealName = normalizeBlankToNull(currentUser.getRealName());
        String currentUsername = normalizeBlankToNull(currentUser.getUsername());
        String salesName = normalizeBlankToNull(ecommerceSalesName);
        if (salesName == null) {
            return false;
        }
        return salesName.equalsIgnoreCase(currentRealName) || salesName.equalsIgnoreCase(currentUsername);
    }

    private void collectAtPhone(Set<String> atPhones, String rawPhone) {
        if (atPhones == null) {
            return;
        }
        String normalized = normalizeDingTalkMobile(rawPhone);
        if (!normalized.isEmpty()) {
            atPhones.add(normalized);
        }
    }

    private String normalizeDingTalkMobile(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() > 11) {
            digits = digits.substring(digits.length() - 11);
        }
        return digits.matches("^1\\d{10}$") ? digits : "";
    }

    private String userDisplay(User user) {
        if (user == null) {
            return "?";
        }
        String displayName = normalizeBlankToNull(user.getRealName());
        if (displayName == null) {
            displayName = normalizeBlankToNull(user.getUsername());
        }
        String username = normalizeBlankToNull(user.getUsername());
        if (displayName == null) {
            return "?";
        }
        return username == null ? displayName : displayName + "(" + username + ")";
    }

    private String joinDistinct(List<String> values) {
        return joinDistinct(values, "，");
    }

    private String joinDistinct(List<String> values, String delimiter) {
        LinkedHashSet<String> items = values == null ? new LinkedHashSet<>() : values.stream()
                .map(this::normalizeBlankToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return items.isEmpty() ? "-" : String.join(delimiter, items);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeBlankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String resolveNotifyPartyATitle(SalesOrder order) {
        if (order == null) {
            return null;
        }
        return firstNonBlank(order.getPartyATitle(), order.getPlatformName());
    }

    private String resolveNotifyPartyBTitle(SalesOrder order) {
        if (order == null) {
            return null;
        }
        String direct = firstNonBlank(order.getOperationEntityTitle(), order.getDeliveryParty());
        if (direct != null) {
            return direct;
        }
        if (order.getCreatedBy() != null) {
            User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
            if (creator != null) {
                return normalizeBlankToNull(creator.getCompanyTitle());
            }
        }
        return null;
    }

    private String resolveNotifySalesSellerTitle(SalesOrder order) {
        if (order == null) {
            return null;
        }
        return firstNonBlank(
                normalizeBlankToNull(order.getOperationEntityTitle()),
                resolveCreatorCompanyTitle(order)
        );
    }

    private String resolveNotifySalesDeliveryPartyTitle(SalesOrder order) {
        if (order == null) {
            return null;
        }
        return firstNonBlank(
                normalizeBlankToNull(order.getDeliveryParty()),
                normalizeBlankToNull(order.getShippingParty())
        );
    }

    private String resolveCreatorCompanyTitle(SalesOrder order) {
        if (order == null || order.getCreatedBy() == null) {
            return null;
        }
        User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
        if (creator == null) {
            return null;
        }
        return normalizeBlankToNull(creator.getCompanyTitle());
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String nullToDash(String value) {
        String normalized = normalizeBlankToNull(value);
        return normalized == null ? "-" : normalized;
    }

    private void applySettlementWriteback(SalesOrder order, SalesSettlementRecord settlement) {
        order.setSettlementNo(settlement.getBillNo());
        order.setPlatformRefundStatus(settlement.getRefundStatus());
        order.setStatus("已开票待结算");
        salesOrderRepository.save(order);
    }

    private BigDecimal resolveSettlementAmount(List<SalesOutputInvoice> invoices, List<SalesReconciliation> reconciliations) {
        BigDecimal amount = invoices.stream()
                .map(SalesOutputInvoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!reconciliations.isEmpty()) {
            amount = amount.add(reconciliations.stream()
                    .map(SalesReconciliation::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return amount;
    }

    private String resolveSalesSettlementStatus(String refundStatus) {
        if ("已回款".equals(refundStatus)) {
            return "已结算";
        }
        if ("部分回款".equals(refundStatus)) {
            return "部分回款";
        }
        return "待回款";
    }

    private String generateBillNo(String prefix) {
        return prefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + System.currentTimeMillis();
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private User getCurrentUser() {
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username.trim()).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && "ROLE_ADMIN".equals(user.getRole());
    }

    private List<Long> resolveVisibleFinanceOwnerIds(User currentUser) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (currentUser == null) {
            return new ArrayList<>();
        }
        if (currentUser.getId() != null) {
            ids.add(currentUser.getId());
        }
        ids.addAll(subjectAccountGroupService.sharedUserIds(currentUser.getUsername()));
        String companyTitle = normalizeBlankToNull(currentUser.getCompanyTitle());
        if (companyTitle != null) {
            for (User user : userRepository.findByCompanyTitle(companyTitle)) {
                if (user != null && user.getId() != null) {
                    ids.add(user.getId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean canBrowseFinanceDocs(User currentUser, List<Long> visibleOwnerIds) {
        return isAdmin(currentUser) || (currentUser != null && !visibleOwnerIds.isEmpty());
    }

    private void assertCurrentUserCanViewSalesReconciliation(SalesReconciliation reconciliation) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return;
        }
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        boolean allowed = reconciliation != null
                && reconciliation.getItems() != null
                && reconciliation.getItems().stream()
                .map(SalesReconciliationItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .map(id -> salesOrderRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(order -> visibleOwnerIds.contains(order.getCreatedBy()));
        if (!allowed) {
            throw new RuntimeException("无权访问该销售对账单");
        }
    }

    private void assertCurrentUserCanViewSalesOutputInvoice(SalesOutputInvoice invoice) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return;
        }
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        boolean allowed = invoice != null
                && invoice.getItems() != null
                && invoice.getItems().stream()
                .map(SalesOutputInvoiceItem::getReconciliationId)
                .filter(Objects::nonNull)
                .map(salesReconciliationItemRepository::findByReconciliationIdOrderByIdAsc)
                .flatMap(List::stream)
                .map(SalesReconciliationItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .map(id -> salesOrderRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(order -> visibleOwnerIds.contains(order.getCreatedBy()));
        if (!allowed) {
            throw new RuntimeException("无权访问该销项发票");
        }
    }

    private void assertCurrentUserCanViewSalesSettlement(SalesSettlementRecord settlement) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return;
        }
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        boolean allowed = settlement != null
                && settlement.getItems() != null
                && settlement.getItems().stream()
                .map(SalesSettlementItem::getReconciliationId)
                .filter(Objects::nonNull)
                .map(salesReconciliationItemRepository::findByReconciliationIdOrderByIdAsc)
                .flatMap(List::stream)
                .map(SalesReconciliationItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .map(id -> salesOrderRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(order -> visibleOwnerIds.contains(order.getCreatedBy()));
        if (!allowed) {
            throw new RuntimeException("无权访问该销售结算单");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<String> splitStatuses(String status) {
        return java.util.Arrays.stream(status.split(",\\s*"))
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }
}
