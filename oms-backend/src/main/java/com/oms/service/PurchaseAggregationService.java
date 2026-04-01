package com.oms.service;

import com.oms.entity.PurchaseInputInvoiceItem;
import com.oms.entity.PurchaseInputInvoiceRecord;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.PurchaseReconciliation;
import com.oms.entity.PurchaseReconciliationItem;
import com.oms.entity.PurchaseSettlementItem;
import com.oms.entity.PurchaseSettlementRecord;
import com.oms.entity.User;
import com.oms.repository.PurchaseInputInvoiceItemRepository;
import com.oms.repository.PurchaseInputInvoiceRecordRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.PurchaseReconciliationItemRepository;
import com.oms.repository.PurchaseReconciliationRepository;
import com.oms.repository.PurchaseSettlementItemRepository;
import com.oms.repository.PurchaseSettlementRecordRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseAggregationService {
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private PurchaseReconciliationRepository purchaseReconciliationRepository;
    @Autowired
    private PurchaseReconciliationItemRepository purchaseReconciliationItemRepository;
    @Autowired
    private PurchaseInputInvoiceRecordRepository purchaseInputInvoiceRecordRepository;
    @Autowired
    private PurchaseInputInvoiceItemRepository purchaseInputInvoiceItemRepository;
    @Autowired
    private PurchaseSettlementRecordRepository purchaseSettlementRecordRepository;
    @Autowired
    private PurchaseSettlementItemRepository purchaseSettlementItemRepository;
    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    @Autowired
    private SalesOrderService salesOrderService;

    public Page<PurchaseReconciliation> searchReconciliations(String billNo, String supplier, String status, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        if (!canBrowseFinanceDocs(currentUser, visibleOwnerIds)) {
            return Page.empty(pageable);
        }
        return purchaseReconciliationRepository.findAll((Specification<PurchaseReconciliation>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(billNo)) {
                predicates.add(cb.like(root.get("billNo"), "%" + billNo.trim() + "%"));
            }
            if (hasText(supplier)) {
                predicates.add(cb.like(root.get("supplier"), "%" + supplier.trim() + "%"));
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
                var visibilitySubquery = query.subquery(Integer.class);
                var itemRoot = visibilitySubquery.from(PurchaseReconciliationItem.class);
                var orderRoot = visibilitySubquery.from(PurchaseOrder.class);
                visibilitySubquery.select(cb.literal(1))
                        .where(
                                cb.equal(itemRoot.get("reconciliation").get("id"), root.get("id")),
                                cb.equal(orderRoot.get("id"), itemRoot.get("purchaseOrderId")),
                                orderRoot.get("createdBy").in(visibleOwnerIds)
                        );
                predicates.add(cb.exists(visibilitySubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public Page<PurchaseInputInvoiceRecord> searchInputInvoices(String billNo, String supplier, String status, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        if (!canBrowseFinanceDocs(currentUser, visibleOwnerIds)) {
            return Page.empty(pageable);
        }
        return purchaseInputInvoiceRecordRepository.findAll((Specification<PurchaseInputInvoiceRecord>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(billNo)) {
                predicates.add(cb.like(root.get("billNo"), "%" + billNo.trim() + "%"));
            }
            if (hasText(supplier)) {
                predicates.add(cb.like(root.get("supplier"), "%" + supplier.trim() + "%"));
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
                var visibilitySubquery = query.subquery(Integer.class);
                var invoiceItemRoot = visibilitySubquery.from(PurchaseInputInvoiceItem.class);
                var reconciliationItemRoot = visibilitySubquery.from(PurchaseReconciliationItem.class);
                var orderRoot = visibilitySubquery.from(PurchaseOrder.class);
                visibilitySubquery.select(cb.literal(1))
                        .where(
                                cb.equal(invoiceItemRoot.get("invoice").get("id"), root.get("id")),
                                cb.equal(reconciliationItemRoot.get("reconciliation").get("id"), invoiceItemRoot.get("reconciliationId")),
                                cb.equal(orderRoot.get("id"), reconciliationItemRoot.get("purchaseOrderId")),
                                orderRoot.get("createdBy").in(visibleOwnerIds)
                        );
                predicates.add(cb.exists(visibilitySubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public Page<PurchaseSettlementRecord> searchSettlements(String billNo, String supplier, String status, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        if (!canBrowseFinanceDocs(currentUser, visibleOwnerIds)) {
            return Page.empty(pageable);
        }
        return purchaseSettlementRecordRepository.findAll((Specification<PurchaseSettlementRecord>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(billNo)) {
                predicates.add(cb.like(root.get("billNo"), "%" + billNo.trim() + "%"));
            }
            if (hasText(supplier)) {
                predicates.add(cb.like(root.get("supplier"), "%" + supplier.trim() + "%"));
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
                var visibilitySubquery = query.subquery(Integer.class);
                var settlementItemRoot = visibilitySubquery.from(PurchaseSettlementItem.class);
                var reconciliationItemRoot = visibilitySubquery.from(PurchaseReconciliationItem.class);
                var orderRoot = visibilitySubquery.from(PurchaseOrder.class);
                visibilitySubquery.select(cb.literal(1))
                        .where(
                                cb.equal(settlementItemRoot.get("settlement").get("id"), root.get("id")),
                                cb.equal(reconciliationItemRoot.get("reconciliation").get("id"), settlementItemRoot.get("reconciliationId")),
                                cb.equal(orderRoot.get("id"), reconciliationItemRoot.get("purchaseOrderId")),
                                orderRoot.get("createdBy").in(visibleOwnerIds)
                        );
                predicates.add(cb.exists(visibilitySubquery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public PurchaseReconciliation getReconciliation(Long id) {
        PurchaseReconciliation entity = purchaseReconciliationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("采购对账单不存在"));
        entity.setItems(purchaseReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(id));
        assertCurrentUserCanViewPurchaseReconciliation(entity);
        return entity;
    }

    public PurchaseInputInvoiceRecord getInputInvoice(Long id) {
        PurchaseInputInvoiceRecord entity = purchaseInputInvoiceRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("进项发票不存在"));
        entity.setItems(purchaseInputInvoiceItemRepository.findByInvoiceIdOrderByIdAsc(id));
        assertCurrentUserCanViewPurchaseInputInvoice(entity);
        return entity;
    }

    public PurchaseSettlementRecord getSettlement(Long id) {
        PurchaseSettlementRecord entity = purchaseSettlementRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("采购结算单不存在"));
        entity.setItems(purchaseSettlementItemRepository.findBySettlementIdOrderByIdAsc(id));
        assertCurrentUserCanViewPurchaseSettlement(entity);
        return entity;
    }

    @Transactional
    public PurchaseReconciliation createReconciliation(List<Long> purchaseOrderIds,
                                                       LocalDate periodStart,
                                                       LocalDate periodEnd,
                                                       LocalDate reconciliationDate,
                                                       String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "采购对账单生成");
        List<PurchaseOrder> orders = loadPurchaseOrders(purchaseOrderIds);
        ensureSameSupplier(orders);

        PurchaseOrder first = orders.get(0);
        PurchaseReconciliation reconciliation = new PurchaseReconciliation();
        reconciliation.setBillNo(generateBillNo("PR"));
        reconciliation.setSupplier(trim(first.getSupplier()));
        reconciliation.setPeriodStart(periodStart);
        reconciliation.setPeriodEnd(periodEnd);
        reconciliation.setReconciliationDate(reconciliationDate == null ? LocalDate.now() : reconciliationDate);
        reconciliation.setStatus("已对账");
        reconciliation.setRemark(trim(remark));
        reconciliation.setTotalAmount(sumPurchaseAmounts(orders));
        PurchaseReconciliation saved = purchaseReconciliationRepository.save(reconciliation);

        List<PurchaseReconciliationItem> items = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            PurchaseReconciliationItem item = new PurchaseReconciliationItem();
            item.setReconciliation(saved);
            item.setPurchaseOrderId(order.getId());
            item.setPurchaseOrderNo(order.getPurchaseOrderNo());
            item.setOmsOrderNo(order.getOmsOrderNo());
            item.setSupplier(order.getSupplier());
            item.setOrderStatus(order.getStatus());
            item.setQuantity(order.getQuantity());
            item.setModel(order.getModel());
            item.setLineAmount(safeAmount(order.getTaxIncludedPurchaseTotal()));
            items.add(item);
            order.setReconciliationStatus("已对账");
            purchaseOrderRepository.save(order);
        }
        purchaseReconciliationItemRepository.saveAll(items);
        saved.setItems(items);
        operationLogService.log("SYSTEM", "生成采购对账单", "PURCHASE_RECONCILIATION", saved.getBillNo(),
                "采购单数=" + orders.size());
        return saved;
    }

    @Transactional
    public PurchaseInputInvoiceRecord createInputInvoice(List<Long> reconciliationIds,
                                                         LocalDate invoiceDate,
                                                         String invoiceNumber,
                                                         String attachmentUrl,
                                                         String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "进项发票生成");
        List<PurchaseReconciliation> reconciliations = loadReconciliations(reconciliationIds);
        ensureSameSupplierByReconciliation(reconciliations);

        PurchaseReconciliation first = reconciliations.get(0);
        PurchaseInputInvoiceRecord invoice = new PurchaseInputInvoiceRecord();
        invoice.setBillNo(generateBillNo("PII"));
        invoice.setInvoiceNumber(hasText(invoiceNumber) ? invoiceNumber.trim() : null);
        invoice.setSupplier(trim(first.getSupplier()));
        invoice.setInvoiceDate(invoiceDate == null ? LocalDate.now() : invoiceDate);
        invoice.setStatus("已收票");
        invoice.setAttachmentUrl(hasText(attachmentUrl) ? attachmentUrl.trim() : null);
        invoice.setRemark(trim(remark));
        invoice.setTotalAmount(reconciliations.stream()
                .map(PurchaseReconciliation::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        PurchaseInputInvoiceRecord saved = purchaseInputInvoiceRecordRepository.save(invoice);

        List<PurchaseInputInvoiceItem> items = new ArrayList<>();
        for (PurchaseReconciliation reconciliation : reconciliations) {
            PurchaseInputInvoiceItem item = new PurchaseInputInvoiceItem();
            item.setInvoice(saved);
            item.setReconciliationId(reconciliation.getId());
            item.setReconciliationBillNo(reconciliation.getBillNo());
            item.setSupplier(reconciliation.getSupplier());
            item.setLineAmount(safeAmount(reconciliation.getTotalAmount()));
            items.add(item);
            reconciliation.setStatus("已收票");
            purchaseReconciliationRepository.save(reconciliation);
        }
        purchaseInputInvoiceItemRepository.saveAll(items);
        saved.setItems(items);

        Map<Long, PurchaseOrder> orderMap = loadOrdersByReconciliationIds(reconciliationIds);
        for (PurchaseOrder order : orderMap.values()) {
            order.setInvoiceNumber(saved.getInvoiceNumber());
            purchaseOrderRepository.save(order);
        }
        LinkedHashSet<Long> relatedSalesOrderIds = collectRelatedSalesOrderIds(orderMap.values());
        if (!relatedSalesOrderIds.isEmpty()) {
            salesOrderService.notifyReadyForInvoiceIfPurchaseChainCompleted(new ArrayList<>(relatedSalesOrderIds));
        }
        operationLogService.log("SYSTEM", "生成进项发票", "PURCHASE_INPUT_INVOICE", saved.getBillNo(),
                "关联采购对账单=" + reconciliations.stream().map(PurchaseReconciliation::getBillNo).collect(Collectors.joining(",")));
        return saved;
    }

    @Transactional
    public PurchaseSettlementRecord createSettlement(List<Long> invoiceIds,
                                                     List<Long> reconciliationIds,
                                                     LocalDate settlementDate,
                                                     String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "采购结算单生成");
        List<PurchaseInputInvoiceRecord> invoices = loadInvoices(invoiceIds);
        List<PurchaseReconciliation> directReconciliations = loadReconciliations(reconciliationIds);
        if (invoices.isEmpty() && directReconciliations.isEmpty()) {
            throw new RuntimeException("请至少选择一个进项发票或采购对账单");
        }

        String supplier = !invoices.isEmpty() ? trim(invoices.get(0).getSupplier()) : trim(directReconciliations.get(0).getSupplier());
        PurchaseSettlementRecord settlement = new PurchaseSettlementRecord();
        settlement.setBillNo(generateBillNo("PST"));
        settlement.setSupplier(supplier);
        settlement.setSettlementDate(settlementDate == null ? LocalDate.now() : settlementDate);
        settlement.setStatus("待付款");
        settlement.setPaymentStatus("待付款");
        settlement.setRemark(trim(remark));
        settlement.setTotalAmount(resolveSettlementAmount(invoices, directReconciliations));
        PurchaseSettlementRecord saved = purchaseSettlementRecordRepository.save(settlement);

        Map<Long, PurchaseReconciliation> reconciliationMap = new LinkedHashMap<>();
        List<PurchaseSettlementItem> items = new ArrayList<>();
        for (PurchaseInputInvoiceRecord invoice : invoices) {
            invoice.setStatus("待付款");
            purchaseInputInvoiceRecordRepository.save(invoice);
            for (PurchaseInputInvoiceItem invoiceItem : purchaseInputInvoiceItemRepository.findByInvoiceIdOrderByIdAsc(invoice.getId())) {
                PurchaseSettlementItem item = new PurchaseSettlementItem();
                item.setSettlement(saved);
                item.setInvoiceId(invoice.getId());
                item.setInvoiceBillNo(invoice.getBillNo());
                item.setReconciliationId(invoiceItem.getReconciliationId());
                item.setReconciliationBillNo(invoiceItem.getReconciliationBillNo());
                item.setLineAmount(safeAmount(invoiceItem.getLineAmount()));
                items.add(item);
                purchaseReconciliationRepository.findById(invoiceItem.getReconciliationId())
                        .ifPresent(reconciliation -> reconciliationMap.put(reconciliation.getId(), reconciliation));
            }
        }
        for (PurchaseReconciliation reconciliation : directReconciliations) {
            reconciliationMap.put(reconciliation.getId(), reconciliation);
        }
        for (PurchaseReconciliation reconciliation : reconciliationMap.values()) {
            if (items.stream().noneMatch(item -> Objects.equals(item.getReconciliationId(), reconciliation.getId()))) {
                PurchaseSettlementItem item = new PurchaseSettlementItem();
                item.setSettlement(saved);
                item.setReconciliationId(reconciliation.getId());
                item.setReconciliationBillNo(reconciliation.getBillNo());
                item.setLineAmount(safeAmount(reconciliation.getTotalAmount()));
                items.add(item);
            }
            reconciliation.setStatus("待付款");
            purchaseReconciliationRepository.save(reconciliation);
        }
        purchaseSettlementItemRepository.saveAll(items);
        saved.setItems(items);

        Map<Long, PurchaseOrder> orderMap = loadOrdersByReconciliationIds(new ArrayList<>(reconciliationMap.keySet()));
        for (PurchaseOrder order : orderMap.values()) {
            order.setPaymentStatus("待付款");
            purchaseOrderRepository.save(order);
        }
        operationLogService.log("SYSTEM", "生成采购结算单", "PURCHASE_SETTLEMENT", saved.getBillNo(),
                "来源条目=" + items.size());
        return saved;
    }

    @Transactional
    public PurchaseSettlementRecord updateSettlementPayment(Long settlementId,
                                                            String paymentStatus,
                                                            LocalDate paymentDate,
                                                            String attachmentUrl,
                                                            String remark) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "采购结算付款维护");
        PurchaseSettlementRecord settlement = purchaseSettlementRecordRepository.findById(settlementId)
                .orElseThrow(() -> new RuntimeException("采购结算单不存在"));
        String normalizedPaymentStatus = hasText(paymentStatus) ? paymentStatus.trim() : settlement.getPaymentStatus();
        settlement.setPaymentStatus(normalizedPaymentStatus);
        settlement.setPaymentDate(paymentDate);
        if (hasText(attachmentUrl)) {
            settlement.setAttachmentUrl(attachmentUrl.trim());
        }
        if (remark != null) {
            settlement.setRemark(remark.trim());
        }
        settlement.setStatus(normalizedPaymentStatus);
        PurchaseSettlementRecord saved = purchaseSettlementRecordRepository.save(settlement);

        List<PurchaseSettlementItem> items = purchaseSettlementItemRepository.findBySettlementIdOrderByIdAsc(settlementId);
        saved.setItems(items);
        Set<Long> reconciliationIds = items.stream()
                .map(PurchaseSettlementItem::getReconciliationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> invoiceIds = items.stream()
                .map(PurchaseSettlementItem::getInvoiceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Long invoiceId : invoiceIds) {
            purchaseInputInvoiceRecordRepository.findById(invoiceId).ifPresent(invoice -> {
                invoice.setStatus(normalizedPaymentStatus);
                purchaseInputInvoiceRecordRepository.save(invoice);
            });
        }
        for (Long reconciliationId : reconciliationIds) {
            purchaseReconciliationRepository.findById(reconciliationId).ifPresent(reconciliation -> {
                reconciliation.setStatus(normalizedPaymentStatus);
                purchaseReconciliationRepository.save(reconciliation);
            });
        }
        Map<Long, PurchaseOrder> orderMap = loadOrdersByReconciliationIds(new ArrayList<>(reconciliationIds));
        for (PurchaseOrder order : orderMap.values()) {
            order.setPaymentStatus(normalizedPaymentStatus);
            purchaseOrderRepository.save(order);
        }
        operationLogService.log("SYSTEM", "更新采购结算付款状态", "PURCHASE_SETTLEMENT", saved.getBillNo(),
                "付款状态=" + normalizedPaymentStatus);
        return saved;
    }

    @Transactional
    public PurchaseReconciliation voidReconciliation(Long reconciliationId) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "采购对账单作废");
        PurchaseReconciliation reconciliation = getReconciliation(reconciliationId);
        boolean hasDownstream = hasActiveInvoiceReferenceForReconciliation(reconciliationId)
                || hasActiveSettlementReferenceForReconciliation(reconciliationId);
        if (hasDownstream) {
            throw new RuntimeException("该采购对账单已被进项发票或采购结算单引用，不能直接作废");
        }
        for (PurchaseReconciliationItem item : reconciliation.getItems()) {
            purchaseOrderRepository.findById(item.getPurchaseOrderId()).ifPresent(order -> {
                order.setReconciliationStatus("未对账");
                purchaseOrderRepository.save(order);
            });
        }
        reconciliation.setStatus("已作废");
        PurchaseReconciliation saved = purchaseReconciliationRepository.save(reconciliation);
        operationLogService.log("SYSTEM", "作废采购对账单", "PURCHASE_RECONCILIATION", saved.getBillNo(), "作废成功");
        return saved;
    }

    @Transactional
    public PurchaseInputInvoiceRecord voidInputInvoice(Long invoiceId) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "进项发票作废");
        PurchaseInputInvoiceRecord invoice = getInputInvoice(invoiceId);
        boolean hasDownstream = hasActiveSettlementReferenceForInvoice(invoiceId);
        if (hasDownstream) {
            throw new RuntimeException("该进项发票已被采购结算单引用，不能直接作废");
        }
        for (PurchaseInputInvoiceItem item : invoice.getItems()) {
            purchaseReconciliationRepository.findById(item.getReconciliationId()).ifPresent(reconciliation -> {
                reconciliation.setStatus("已对账");
                purchaseReconciliationRepository.save(reconciliation);
                for (PurchaseReconciliationItem reconItem : purchaseReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliation.getId())) {
                    purchaseOrderRepository.findById(reconItem.getPurchaseOrderId()).ifPresent(order -> {
                        if (Objects.equals(trim(order.getInvoiceNumber()), trim(invoice.getInvoiceNumber()))) {
                            order.setInvoiceNumber(null);
                        }
                        purchaseOrderRepository.save(order);
                    });
                }
            });
        }
        invoice.setStatus("已作废");
        PurchaseInputInvoiceRecord saved = purchaseInputInvoiceRecordRepository.save(invoice);
        operationLogService.log("SYSTEM", "作废进项发票", "PURCHASE_INPUT_INVOICE", saved.getBillNo(), "作废成功");
        return saved;
    }

    @Transactional
    public PurchaseSettlementRecord voidSettlement(Long settlementId) {
        subjectAccountGroupService.assertPrimaryActor(getCurrentUser(), "采购结算单作废");
        PurchaseSettlementRecord settlement = getSettlement(settlementId);
        for (PurchaseSettlementItem item : settlement.getItems()) {
            if (item.getInvoiceId() != null) {
                purchaseInputInvoiceRecordRepository.findById(item.getInvoiceId()).ifPresent(invoice -> {
                    invoice.setStatus("已收票");
                    purchaseInputInvoiceRecordRepository.save(invoice);
                });
            }
            if (item.getReconciliationId() != null) {
                purchaseReconciliationRepository.findById(item.getReconciliationId()).ifPresent(reconciliation -> {
                    reconciliation.setStatus(item.getInvoiceId() != null ? "已收票" : "已对账");
                    purchaseReconciliationRepository.save(reconciliation);
                    for (PurchaseReconciliationItem reconItem : purchaseReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliation.getId())) {
                        purchaseOrderRepository.findById(reconItem.getPurchaseOrderId()).ifPresent(order -> {
                            order.setPaymentStatus("未付款");
                            purchaseOrderRepository.save(order);
                        });
                    }
                });
            }
        }
        settlement.setStatus("已作废");
        settlement.setPaymentStatus("已作废");
        PurchaseSettlementRecord saved = purchaseSettlementRecordRepository.save(settlement);
        operationLogService.log("SYSTEM", "作废采购结算单", "PURCHASE_SETTLEMENT", saved.getBillNo(), "作废成功");
        return saved;
    }

    private List<PurchaseOrder> loadPurchaseOrders(List<Long> purchaseOrderIds) {
        if (purchaseOrderIds == null || purchaseOrderIds.isEmpty()) {
            throw new RuntimeException("请选择采购订单");
        }
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllById(purchaseOrderIds);
        if (orders.size() != purchaseOrderIds.size()) {
            throw new RuntimeException("存在已失效的采购订单，请刷新后重试");
        }
        return orders;
    }

    private boolean hasActiveInvoiceReferenceForReconciliation(Long reconciliationId) {
        return purchaseInputInvoiceItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                .map(PurchaseInputInvoiceItem::getInvoice)
                .filter(Objects::nonNull)
                .anyMatch(invoice -> !"已作废".equals(trim(invoice.getStatus())));
    }

    private boolean hasActiveSettlementReferenceForReconciliation(Long reconciliationId) {
        return purchaseSettlementItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                .map(PurchaseSettlementItem::getSettlement)
                .filter(Objects::nonNull)
                .anyMatch(settlement -> !"已作废".equals(trim(settlement.getStatus())));
    }

    private boolean hasActiveSettlementReferenceForInvoice(Long invoiceId) {
        return purchaseSettlementItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getInvoiceId(), invoiceId))
                .map(PurchaseSettlementItem::getSettlement)
                .filter(Objects::nonNull)
                .anyMatch(settlement -> !"已作废".equals(trim(settlement.getStatus())));
    }

    private List<PurchaseReconciliation> loadReconciliations(List<Long> reconciliationIds) {
        if (reconciliationIds == null || reconciliationIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<PurchaseReconciliation> reconciliations = purchaseReconciliationRepository.findAllById(reconciliationIds);
        if (reconciliations.size() != reconciliationIds.size()) {
            throw new RuntimeException("存在已失效的采购对账单，请刷新后重试");
        }
        return reconciliations;
    }

    private List<PurchaseInputInvoiceRecord> loadInvoices(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<PurchaseInputInvoiceRecord> invoices = purchaseInputInvoiceRecordRepository.findAllById(invoiceIds);
        if (invoices.size() != invoiceIds.size()) {
            throw new RuntimeException("存在已失效的进项发票，请刷新后重试");
        }
        return invoices;
    }

    private void ensureSameSupplier(List<PurchaseOrder> orders) {
        String supplier = trim(orders.get(0).getSupplier());
        boolean same = orders.stream().allMatch(order -> Objects.equals(supplier, trim(order.getSupplier())));
        if (!same) {
            throw new RuntimeException("采购对账单仅支持同一供应商合并");
        }
    }

    private void ensureSameSupplierByReconciliation(List<PurchaseReconciliation> reconciliations) {
        String supplier = trim(reconciliations.get(0).getSupplier());
        boolean same = reconciliations.stream().allMatch(item -> Objects.equals(supplier, trim(item.getSupplier())));
        if (!same) {
            throw new RuntimeException("进项发票仅支持同一供应商的采购对账单合并");
        }
    }

    private BigDecimal sumPurchaseAmounts(List<PurchaseOrder> orders) {
        return orders.stream()
                .map(PurchaseOrder::getTaxIncludedPurchaseTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, PurchaseOrder> loadOrdersByReconciliationIds(List<Long> reconciliationIds) {
        Map<Long, PurchaseOrder> result = new LinkedHashMap<>();
        for (Long reconciliationId : reconciliationIds) {
            for (PurchaseReconciliationItem item : purchaseReconciliationItemRepository.findByReconciliationIdOrderByIdAsc(reconciliationId)) {
                purchaseOrderRepository.findById(item.getPurchaseOrderId()).ifPresent(order -> result.put(order.getId(), order));
            }
        }
        return result;
    }

    private LinkedHashSet<Long> collectRelatedSalesOrderIds(java.util.Collection<PurchaseOrder> orders) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (orders == null || orders.isEmpty()) {
            return ids;
        }
        for (PurchaseOrder order : orders) {
            if (order == null) {
                continue;
            }
            if (order.getSourceSalesOrderId() != null) {
                ids.add(order.getSourceSalesOrderId());
            }
            ids.addAll(parseMergedSalesOrderIds(order.getMergedSalesOrderIds()));
        }
        return ids;
    }

    private List<Long> parseMergedSalesOrderIds(String mergedSalesOrderIds) {
        if (!hasText(mergedSalesOrderIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : mergedSalesOrderIds.split(",")) {
            String normalized = trim(part);
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(normalized));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private BigDecimal resolveSettlementAmount(List<PurchaseInputInvoiceRecord> invoices, List<PurchaseReconciliation> reconciliations) {
        BigDecimal amount = invoices.stream()
                .map(PurchaseInputInvoiceRecord::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!reconciliations.isEmpty()) {
            amount = amount.add(reconciliations.stream()
                    .map(PurchaseReconciliation::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return amount;
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
        String companyTitle = trim(currentUser.getCompanyTitle());
        if (!companyTitle.isEmpty()) {
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

    private void assertCurrentUserCanViewPurchaseReconciliation(PurchaseReconciliation reconciliation) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return;
        }
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        boolean allowed = reconciliation != null
                && reconciliation.getItems() != null
                && reconciliation.getItems().stream()
                .map(PurchaseReconciliationItem::getPurchaseOrderId)
                .filter(Objects::nonNull)
                .map(id -> purchaseOrderRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(order -> visibleOwnerIds.contains(order.getCreatedBy()));
        if (!allowed) {
            throw new RuntimeException("无权访问该采购对账单");
        }
    }

    private void assertCurrentUserCanViewPurchaseInputInvoice(PurchaseInputInvoiceRecord invoice) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return;
        }
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        boolean allowed = invoice != null
                && invoice.getItems() != null
                && invoice.getItems().stream()
                .map(PurchaseInputInvoiceItem::getReconciliationId)
                .filter(Objects::nonNull)
                .map(purchaseReconciliationItemRepository::findByReconciliationIdOrderByIdAsc)
                .flatMap(List::stream)
                .map(PurchaseReconciliationItem::getPurchaseOrderId)
                .filter(Objects::nonNull)
                .map(id -> purchaseOrderRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(order -> visibleOwnerIds.contains(order.getCreatedBy()));
        if (!allowed) {
            throw new RuntimeException("无权访问该进项发票");
        }
    }

    private void assertCurrentUserCanViewPurchaseSettlement(PurchaseSettlementRecord settlement) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return;
        }
        List<Long> visibleOwnerIds = resolveVisibleFinanceOwnerIds(currentUser);
        boolean allowed = settlement != null
                && settlement.getItems() != null
                && settlement.getItems().stream()
                .map(PurchaseSettlementItem::getReconciliationId)
                .filter(Objects::nonNull)
                .map(purchaseReconciliationItemRepository::findByReconciliationIdOrderByIdAsc)
                .flatMap(List::stream)
                .map(PurchaseReconciliationItem::getPurchaseOrderId)
                .filter(Objects::nonNull)
                .map(id -> purchaseOrderRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(order -> visibleOwnerIds.contains(order.getCreatedBy()));
        if (!allowed) {
            throw new RuntimeException("无权访问该采购结算单");
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
