package com.oms.service;

import com.oms.entity.Contract;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.ContractRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PurchaseOrderService {
    private static final String FEICHUKE_TITLE = "飞础科智慧科技（上海）有限公司";
    private static final String REXIANG_TITLE = "上海热像科技股份有限公司";

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private SalesOrderMasterService salesOrderMasterService;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    public Page<PurchaseOrder> searchOrders(String keyword, String status, String erpEntryStatus, Pageable pageable) {
        User currentUser = getCurrentUser();
        List<Long> sharedUserIds = currentUser != null ? subjectAccountGroupService.manuallySharedUserIds(currentUser.getUsername()) : List.of();
        Set<Long> feichukeCreatorIds = getFeichukeCreatorIds();
        
        Specification<PurchaseOrder> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                List<Predicate> keywordPredicates = new ArrayList<>();
                keywordPredicates.add(cb.like(root.get("purchaseOrderNo"), pattern));
                keywordPredicates.add(cb.like(root.get("omsOrderNo"), pattern));
                keywordPredicates.add(cb.like(root.get("supplier"), pattern));

                Subquery<Long> matchedContractSalesOrderIds = query.subquery(Long.class);
                var contractBySalesOrder = matchedContractSalesOrderIds.from(Contract.class);
                matchedContractSalesOrderIds.select(contractBySalesOrder.get("salesOrderId"))
                        .where(
                                cb.like(contractBySalesOrder.get("contractNo"), pattern),
                                cb.isNotNull(contractBySalesOrder.get("salesOrderId"))
                        );
                keywordPredicates.add(root.get("sourceSalesOrderId").in(matchedContractSalesOrderIds));

                Subquery<String> matchedMergeSelectionKeys = query.subquery(String.class);
                var contractByMergeKey = matchedMergeSelectionKeys.from(Contract.class);
                matchedMergeSelectionKeys.select(contractByMergeKey.get("mergeSelectionKey"))
                        .where(
                                cb.like(contractByMergeKey.get("contractNo"), pattern),
                                cb.isNotNull(contractByMergeKey.get("mergeSelectionKey"))
                        );
                keywordPredicates.add(root.get("mergeSelectionKey").in(matchedMergeSelectionKeys));

                predicates.add(cb.or(keywordPredicates.toArray(new Predicate[0])));
            }
            
            // 状态过滤：兼容前端英文 tab 值（pending/confirmed/shipped/received）与中文库值。
            String normalizedStatus = normalizeStatus(status);
            if (normalizedStatus != null && !normalizedStatus.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), normalizedStatus));
            }
            if (erpEntryStatus != null && !erpEntryStatus.isBlank()) {
                predicates.add(cb.equal(root.get("erpEntryStatus"), erpEntryStatus.trim()));
            }
            
            // 权限过滤
            if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                    List<Predicate> visible = new ArrayList<>();
                visible.add(!sharedUserIds.isEmpty()
                        ? root.get("createdBy").in(sharedUserIds)
                        : cb.equal(root.get("createdBy"), currentUser.getId()));
                if (isRexiangCompany(currentUser) && !feichukeCreatorIds.isEmpty()) {
                        visible.add(root.get("createdBy").in(feichukeCreatorIds));
                    }
                String companyTitle = normalizeBlankToNull(currentUser.getCompanyTitle());
                if (hasPurchaseFinanceScope(currentUser) && companyTitle != null) {
                    visible.add(cb.equal(root.get("supplier"), companyTitle));
                }
                predicates.add(cb.or(visible.toArray(new Predicate[0])));
            }
            
            // 下游退回的订单：仅隐藏“当前采购单锚定的源销售单”已退回的记录。
            // 不能再按 omsOrderNo 整体隐藏，否则同一链路单号下后续重新指派生成的新采购单也会被误伤。
            Subquery<Long> returnedSourceSalesOrderIds = query.subquery(Long.class);
            var soRoot = returnedSourceSalesOrderIds.from(SalesOrder.class);
            returnedSourceSalesOrderIds.select(soRoot.get("id"))
                    .where(cb.equal(soRoot.get("status"), "已退回"));
            predicates.add(cb.or(
                root.get("sourceSalesOrderId").isNull(),
                cb.not(root.get("sourceSalesOrderId").in(returnedSourceSalesOrderIds))
            ));
            
            // 列表按创建时间倒序（最新的在最前）
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<PurchaseOrder> matchedOrders = purchaseOrderRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createTime"));
        List<PurchaseOrder> displayRows = buildDisplayRows(matchedOrders, currentUser);
        int fromIndex = Math.toIntExact(pageable.getOffset());
        if (fromIndex >= displayRows.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, displayRows.size());
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), displayRows.size());
        return new PageImpl<>(displayRows.subList(fromIndex, toIndex), pageable, displayRows.size());
    }

    private List<PurchaseOrder> buildDisplayRows(List<PurchaseOrder> orders, User currentUser) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> mergedSalesOrderIds = new ArrayList<>();
        List<Long> singleSourceSalesOrderIds = new ArrayList<>();
        List<String> mergeSelectionKeys = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            mergedSalesOrderIds.addAll(parseMergedSalesOrderIds(order.getMergedSalesOrderIds()));
            if (order.getSourceSalesOrderId() != null) {
                singleSourceSalesOrderIds.add(order.getSourceSalesOrderId());
            }
            String mergeSelectionKey = normalizeBlankToNull(order.getMergeSelectionKey());
            if (mergeSelectionKey != null) {
                mergeSelectionKeys.add(mergeSelectionKey);
            }
        }

        Map<Long, SalesOrder> salesOrderById = new HashMap<>();
        LinkedHashSet<Long> allSalesOrderIds = new LinkedHashSet<>();
        allSalesOrderIds.addAll(mergedSalesOrderIds);
        allSalesOrderIds.addAll(singleSourceSalesOrderIds);
        if (!allSalesOrderIds.isEmpty()) {
            for (SalesOrder salesOrder : salesOrderRepository.findAllById(allSalesOrderIds)) {
                if (salesOrder.getId() != null) {
                    salesOrderById.put(salesOrder.getId(), salesOrder);
                }
            }
        }

        Map<String, List<Contract>> contractsByMergeSelectionKey = new HashMap<>();
        if (!mergeSelectionKeys.isEmpty()) {
            for (Contract contract : contractRepository.findAllByMergeSelectionKeyInOrderByIdDesc(new ArrayList<>(new LinkedHashSet<>(mergeSelectionKeys)))) {
                String key = normalizeBlankToNull(contract.getMergeSelectionKey());
                if (key != null) {
                    contractsByMergeSelectionKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(contract);
                }
            }
        }

        Map<Long, List<Contract>> contractsBySalesOrderId = new HashMap<>();
        if (!allSalesOrderIds.isEmpty()) {
            for (Contract contract : contractRepository.findAllBySalesOrderIdInOrderByIdDesc(new ArrayList<>(allSalesOrderIds))) {
                if (contract.getSalesOrderId() != null) {
                    contractsBySalesOrderId.computeIfAbsent(contract.getSalesOrderId(), ignored -> new ArrayList<>()).add(contract);
                }
            }
        }

        List<PurchaseOrder> displayRows = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            displayRows.addAll(expandPurchaseOrderRows(order, currentUser, salesOrderById, contractsByMergeSelectionKey, contractsBySalesOrderId));
        }
        return displayRows;
    }

    private List<PurchaseOrder> expandPurchaseOrderRows(PurchaseOrder order,
                                                        User currentUser,
                                                        Map<Long, SalesOrder> salesOrderById,
                                                        Map<String, List<Contract>> contractsByMergeSelectionKey,
                                                        Map<Long, List<Contract>> contractsBySalesOrderId) {
        if (order == null) {
            return Collections.emptyList();
        }

        String contractNo = resolveContractNo(order, contractsByMergeSelectionKey, contractsBySalesOrderId);
        List<Map<String, Object>> detailRows = buildDetailRows(order, salesOrderById);

        if (detailRows.isEmpty()) {
            PurchaseOrder singleRow = copyPurchaseOrder(order);
            singleRow.setContractNo(contractNo);
            singleRow.setDetailRows(buildFallbackDetailRows(order));
            return List.of(sanitizeErpEntryVisibility(singleRow, currentUser));
        }

        List<PurchaseOrder> rows = new ArrayList<>();
        for (Map<String, Object> detailRow : detailRows) {
            PurchaseOrder row = copyPurchaseOrder(order);
            row.setContractNo(contractNo);
            row.setDetailRows(detailRows);
            Object sourceSalesOrderId = detailRow.get("sourceSalesOrderId");
            if (sourceSalesOrderId instanceof Number number) {
                row.setSourceSalesOrderId(number.longValue());
            }
            row.setModel((String) detailRow.getOrDefault("model", row.getModel()));
            Object quantity = detailRow.get("quantity");
            if (quantity instanceof Number number) {
                row.setQuantity(number.intValue());
            }
            Object purchaseUnitPrice = detailRow.get("taxIncludedPurchasePrice");
            if (purchaseUnitPrice instanceof BigDecimal bigDecimal) {
                row.setTaxIncludedPurchasePrice(bigDecimal);
            }
            Object purchaseTotal = detailRow.get("taxIncludedPurchaseTotal");
            if (purchaseTotal instanceof BigDecimal bigDecimal) {
                row.setTaxIncludedPurchaseTotal(bigDecimal);
            }
            rows.add(sanitizeErpEntryVisibility(row, currentUser));
        }
        return rows;
    }

    private PurchaseOrder copyPurchaseOrder(PurchaseOrder source) {
        PurchaseOrder target = new PurchaseOrder();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private List<Map<String, Object>> buildDetailRows(PurchaseOrder order, Map<Long, SalesOrder> salesOrderById) {
        List<Long> mergedIds = parseMergedSalesOrderIds(order.getMergedSalesOrderIds());
        if (mergedIds.isEmpty()) {
            if (order.getSourceSalesOrderId() != null) {
                SalesOrder sourceSalesOrder = salesOrderById.get(order.getSourceSalesOrderId());
                if (sourceSalesOrder != null) {
                    return List.of(buildDetailRow(sourceSalesOrder));
                }
            }
            return buildFallbackDetailRows(order);
        }

        List<Map<String, Object>> detailRows = new ArrayList<>();
        for (Long salesOrderId : mergedIds) {
            SalesOrder salesOrder = salesOrderById.get(salesOrderId);
            if (salesOrder != null) {
                detailRows.add(buildDetailRow(salesOrder));
            }
        }
        return detailRows;
    }

    private List<Map<String, Object>> buildFallbackDetailRows(PurchaseOrder order) {
        Map<String, Object> row = new HashMap<>();
        row.put("sourceSalesOrderId", order.getSourceSalesOrderId());
        row.put("model", normalizeBlankToNull(order.getModel()) != null ? order.getModel() : "-");
        row.put("quantity", order.getQuantity() != null ? order.getQuantity() : 0);
        row.put("taxIncludedPurchasePrice", order.getTaxIncludedPurchasePrice() != null ? order.getTaxIncludedPurchasePrice() : BigDecimal.ZERO);
        row.put("taxIncludedPurchaseTotal", order.getTaxIncludedPurchaseTotal() != null ? order.getTaxIncludedPurchaseTotal() : BigDecimal.ZERO);
        return List.of(row);
    }

    private Map<String, Object> buildDetailRow(SalesOrder salesOrder) {
        Map<String, Object> row = new HashMap<>();
        Integer quantity = salesOrder.getQuantity() != null ? salesOrder.getQuantity() : 0;
        BigDecimal unitPrice = resolvePurchaseLineUnitPrice(salesOrder);
        BigDecimal totalPrice = quantity > 0
                ? unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        row.put("sourceSalesOrderId", salesOrder.getId());
        row.put("model", normalizeBlankToNull(salesOrder.getModel()) != null ? salesOrder.getModel().trim() : "-");
        row.put("quantity", quantity);
        row.put("taxIncludedPurchasePrice", unitPrice);
        row.put("taxIncludedPurchaseTotal", totalPrice);
        return row;
    }

    private BigDecimal resolvePurchaseLineUnitPrice(SalesOrder salesOrder) {
        if (salesOrder == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal deliveryPartyPurchasePrice = salesOrder.getDeliveryPartyPurchasePrice();
        if (deliveryPartyPurchasePrice != null && deliveryPartyPurchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            return deliveryPartyPurchasePrice.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal fallback = salesOrder.getTaxIncludedPrice();
        if (fallback != null && fallback.compareTo(BigDecimal.ZERO) > 0) {
            return fallback.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveContractNo(PurchaseOrder order,
                                     Map<String, List<Contract>> contractsByMergeSelectionKey,
                                     Map<Long, List<Contract>> contractsBySalesOrderId) {
        String mergeSelectionKey = normalizeBlankToNull(order.getMergeSelectionKey());
        if (mergeSelectionKey != null) {
            Contract contract = pickMatchingContract(contractsByMergeSelectionKey.get(mergeSelectionKey), order.getSupplier());
            if (contract != null) {
                return normalizeBlankToNull(contract.getContractNo());
            }
        }
        if (order.getSourceSalesOrderId() != null) {
            Contract contract = pickMatchingContract(contractsBySalesOrderId.get(order.getSourceSalesOrderId()), order.getSupplier());
            if (contract != null) {
                return normalizeBlankToNull(contract.getContractNo());
            }
        }
        return null;
    }

    private Contract pickMatchingContract(List<Contract> contracts, String supplier) {
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }
        String normalizedSupplier = normalizeBlankToNull(supplier);
        if (normalizedSupplier == null) {
            return contracts.get(0);
        }
        for (Contract contract : contracts) {
            if (Objects.equals(normalizeBlankToNull(contract.getPartyB()), normalizedSupplier)) {
                return contract;
            }
        }
        return contracts.get(0);
    }

    private String normalizeStatus(String status) {
        if (status == null) return null;
        String s = status.trim();
        if (s.isEmpty()) return null;
        return switch (s.toLowerCase()) {
            case "pending" -> "待确认";
            case "confirmed" -> "已确认";
            case "shipped" -> "已发货";
            case "received" -> "已到货";
            default -> s;
        };
    }

    public PurchaseOrder getOrderById(Long id) {
        User currentUser = getCurrentUser();
        
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElse(null);
        
        if (order != null && !canCurrentUserViewPurchaseOrder(currentUser, order)) {
            throw new RuntimeException("无权访问该采购订单");
        }

        return sanitizeErpEntryVisibility(order, currentUser);
    }

    @Transactional
    public PurchaseOrder saveOrder(PurchaseOrder order) {
        if (order == null) return null;
        
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        subjectAccountGroupService.assertPrimaryActor(currentUser, "采购订单写操作");
        
        // 如果是新订单，设置创建用户ID
        if (order.getId() == null && currentUser != null) {
            order.setCreatedBy(currentUser.getId());
            order.setCreator(currentUser.getRealName());
        }
        
        // 自动计算含税采购总额
        if (order.getTaxIncludedPurchasePrice() != null && order.getQuantity() != null) {
            order.setTaxIncludedPurchaseTotal(
                order.getTaxIncludedPurchasePrice().multiply(new java.math.BigDecimal(order.getQuantity()))
            );
        }

        populateAnchorFields(order);
        
        return purchaseOrderRepository.save(order);
    }

    @Transactional
    public java.util.Map<String, Object> backfillAnchors(List<Long> purchaseOrderIds, Integer limit, boolean onlyMissing) {
        List<PurchaseOrder> candidates;
        if (purchaseOrderIds != null && !purchaseOrderIds.isEmpty()) {
            candidates = purchaseOrderRepository.findAllById(purchaseOrderIds);
        } else {
            candidates = purchaseOrderRepository.findAll();
        }

        int max = limit == null || limit <= 0 ? candidates.size() : limit;
        int scanned = 0;
        int updated = 0;
        List<Long> touchedOrderIds = new ArrayList<>();

        for (PurchaseOrder order : candidates) {
            if (order == null || order.getId() == null) {
                continue;
            }
            if (scanned >= max) {
                break;
            }
            scanned++;

            boolean missingAnchors = order.getSourceSalesOrderId() == null
                    || order.getMasterId() == null
                    || order.getAllocationId() == null;
            if (onlyMissing && !missingAnchors) {
                continue;
            }

            boolean changed = populateAnchorFields(order);
            if (changed) {
                purchaseOrderRepository.save(order);
                updated++;
                touchedOrderIds.add(order.getId());
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("scannedCount", scanned);
        result.put("updatedCount", updated);
        result.put("touchedOrderIds", touchedOrderIds);
        result.put("onlyMissing", onlyMissing);
        return result;
    }

    @Transactional
    public void deleteOrder(Long id) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        subjectAccountGroupService.assertPrimaryActor(currentUser, "采购订单删除");
        
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElse(null);
        
        // 权限检查：非管理员只能删除自己创建的订单
        if (order != null && currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            if (!order.getCreatedBy().equals(currentUser.getId())) {
                throw new RuntimeException("无权删除该采购订单");
            }
        }
        
        if (order != null) {
            purchaseOrderRepository.deleteById(id);
        }
    }

    @Transactional
    public PurchaseOrder updatePaymentStatus(Long id, String paymentStatus) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        subjectAccountGroupService.assertPrimaryActor(currentUser, "采购付款状态更新");
        PurchaseOrder order = getOrderById(id);
        if (order != null) {
            String oldStatus = order.getPaymentStatus();
            order.setPaymentStatus(paymentStatus);
            PurchaseOrder saved = purchaseOrderRepository.save(order);
            String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                    ? currentUser.getRealName() : currentUsername;
            operationLogService.log(operatorName, "更新付款状态", "PURCHASE_ORDER", String.valueOf(id),
                    "付款状态：" + (oldStatus != null ? oldStatus : "未付款") + " → " + (paymentStatus != null ? paymentStatus : ""));
            return saved;
        }
        return null;
    }

    @Transactional
    public PurchaseOrder updateReconciliationStatus(Long id, String reconciliationStatus, String invoiceNumber) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        subjectAccountGroupService.assertPrimaryActor(currentUser, "采购对账状态更新");
        PurchaseOrder order = getOrderById(id);
        if (order != null) {
            String oldStatus = order.getReconciliationStatus();
            order.setReconciliationStatus(reconciliationStatus);
            if (invoiceNumber != null && !invoiceNumber.isEmpty()) {
                order.setInvoiceNumber(invoiceNumber);
            }
            PurchaseOrder saved = purchaseOrderRepository.save(order);
            String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                    ? currentUser.getRealName() : currentUsername;
            operationLogService.log(operatorName, "更新对账状态", "PURCHASE_ORDER", String.valueOf(id),
                    "对账状态：" + (oldStatus != null ? oldStatus : "未对账") + " → " + (reconciliationStatus != null ? reconciliationStatus : ""));
            return saved;
        }
        return null;
    }

    @Transactional
    public PurchaseOrder updateErpEntry(Long id, String screenshotUrl, String operator, LocalDateTime entryTime) {
        User currentUser = getCurrentUser();
        subjectAccountGroupService.assertPrimaryActor(currentUser, "采购 ERP 录单维护");
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("采购订单不存在"));
        if (!canCurrentUserMaintainErpEntry(currentUser, order)) {
            throw new RuntimeException("无权维护该采购订单的商务ERP录单信息");
        }

        String normalizedOperator = defaultOperatorName(currentUser, operator);
        LocalDateTime normalizedTime = entryTime != null ? entryTime : LocalDateTime.now();
        String normalizedScreenshot = normalizeBlankToNull(screenshotUrl);
        String normalizedStatus = normalizedScreenshot != null ? "已录单" : "待系统录单";

        applyErpEntry(order, normalizedStatus, normalizedScreenshot, normalizedOperator, normalizedTime);
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        syncErpEntryToSiblingPurchaseOrders(order.getOmsOrderNo(), saved.getId(), normalizedStatus, normalizedScreenshot, normalizedOperator, normalizedTime);

        String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                ? currentUser.getRealName() : "SYSTEM";
        operationLogService.log(operatorName, "维护商务ERP录单", "PURCHASE_ORDER", String.valueOf(id),
                "状态=" + normalizedStatus + "，录单人=" + normalizedOperator + "，录单时间=" + normalizedTime);
        return sanitizeErpEntryVisibility(saved, currentUser);
    }

    public boolean canCurrentUserMaintainErpEntry(PurchaseOrder order) {
        return canCurrentUserMaintainErpEntry(getCurrentUser(), order);
    }

    public boolean canCurrentUserViewErpEntryScreenshot(PurchaseOrder order) {
        return canCurrentUserViewErpEntryScreenshot(getCurrentUser(), order);
    }

    public boolean currentUserMayPreviewErpEntryScreenshotPath(User currentUser, String rawPath) {
        String path = normalizeUploadPath(rawPath);
        if (currentUser == null || path == null) {
            return false;
        }
        for (PurchaseOrder order : purchaseOrderRepository.findAll()) {
            String screenshotPath = normalizeUploadPath(order.getErpEntryScreenshotUrl());
            if (path.equals(screenshotPath) && canCurrentUserViewErpEntryScreenshot(currentUser, order)) {
                return true;
            }
        }
        return false;
    }

    public boolean canCurrentUserViewPurchaseOrder(PurchaseOrder order) {
        return canCurrentUserViewPurchaseOrder(getCurrentUser(), order);
    }

    private PurchaseOrder sanitizeErpEntryVisibility(PurchaseOrder order, User currentUser) {
        if (order == null) {
            return null;
        }
        boolean canEdit = canCurrentUserMaintainErpEntry(currentUser, order);
        boolean canPreview = canCurrentUserViewErpEntryScreenshot(currentUser, order);
        order.setErpEntryCanEdit(canEdit);
        order.setErpEntryCanPreviewScreenshot(canPreview);
        if (!canPreview) {
            order.setErpEntryScreenshotUrl(null);
        }
        return order;
    }

    private void syncErpEntryToSiblingPurchaseOrders(String omsOrderNo, Long currentOrderId, String status,
                                                     String screenshotUrl, String operator, LocalDateTime entryTime) {
        String oms = normalizeBlankToNull(omsOrderNo);
        if (oms == null) {
            return;
        }
        List<PurchaseOrder> siblings = purchaseOrderRepository.findByOmsOrderNo(oms);
        for (PurchaseOrder sibling : siblings) {
            if (sibling.getId() != null && sibling.getId().equals(currentOrderId)) {
                continue;
            }
            applyErpEntry(sibling, status, screenshotUrl, operator, entryTime);
            purchaseOrderRepository.save(sibling);
        }
    }

    private void applyErpEntry(PurchaseOrder order, String status, String screenshotUrl,
                               String operator, LocalDateTime entryTime) {
        order.setErpEntryStatus(status);
        order.setErpEntryScreenshotUrl(screenshotUrl);
        order.setErpEntryOperator(operator);
        order.setErpEntryTime(entryTime);
    }

    private boolean canCurrentUserViewPurchaseOrder(User currentUser, PurchaseOrder order) {
        if (order == null || currentUser == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(currentUser.getRole())) {
            return true;
        }
        List<Long> sharedUserIds = subjectAccountGroupService.manuallySharedUserIds(currentUser.getUsername());
        if (!sharedUserIds.isEmpty() && order.getCreatedBy() != null && sharedUserIds.contains(order.getCreatedBy())) {
            return true;
        }
        if (order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId())) {
            return true;
        }
        String currentCompanyTitle = normalizeBlankToNull(currentUser.getCompanyTitle());
        if (hasPurchaseFinanceScope(currentUser)
                && currentCompanyTitle != null
                && currentCompanyTitle.equals(normalizeBlankToNull(order.getSupplier()))) {
            return true;
        }
        if (isRexiangCompany(currentUser)) {
            User creator = order.getCreatedBy() != null ? userRepository.findById(order.getCreatedBy()).orElse(null) : null;
            return isFeichukeCompany(creator);
        }
        return false;
    }

    private boolean canCurrentUserMaintainErpEntry(User currentUser, PurchaseOrder order) {
        if (order == null || currentUser == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(currentUser.getRole())) {
            return true;
        }
        if (isRexiangCompany(currentUser)) {
            // 上海热像全员可查看/维护 ERP 录单截图
            return true;
        }
        return isFeichukeCompany(currentUser) && isRelatedFeichukeSalesOwner(currentUser, order);
    }

    private boolean populateAnchorFields(PurchaseOrder order) {
        if (order == null) {
            return false;
        }
        SalesOrder sourceOrder = resolveSourceSalesOrder(order);
        if (sourceOrder == null) {
            return false;
        }

        User sourceCreator = sourceOrder.getCreatedBy() == null
                ? null
                : userRepository.findById(sourceOrder.getCreatedBy()).orElse(null);
        SalesOrder anchoredSource = salesOrderMasterService.ensureAnchorsForOrder(sourceOrder, sourceCreator);

        boolean changed = false;
        if (order.getSourceSalesOrderId() == null || !order.getSourceSalesOrderId().equals(anchoredSource.getId())) {
            order.setSourceSalesOrderId(anchoredSource.getId());
            changed = true;
        }
        if (anchoredSource.getMasterId() != null && !anchoredSource.getMasterId().equals(order.getMasterId())) {
            order.setMasterId(anchoredSource.getMasterId());
            changed = true;
        }
        if (anchoredSource.getAllocationId() != null && !anchoredSource.getAllocationId().equals(order.getAllocationId())) {
            order.setAllocationId(anchoredSource.getAllocationId());
            changed = true;
        }
        if (normalizeBlankToNull(order.getOmsOrderNo()) == null && normalizeBlankToNull(anchoredSource.getOmsOrderNo()) != null) {
            order.setOmsOrderNo(anchoredSource.getOmsOrderNo().trim());
            changed = true;
        }
        return changed;
    }

    private SalesOrder resolveSourceSalesOrder(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        if (order.getSourceSalesOrderId() != null) {
            return salesOrderRepository.findById(order.getSourceSalesOrderId()).orElse(null);
        }

        Long salesOrderIdFromNo = extractSalesOrderIdFromPurchaseNo(order.getPurchaseOrderNo());
        if (salesOrderIdFromNo != null) {
            return salesOrderRepository.findById(salesOrderIdFromNo).orElse(null);
        }

        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo == null) {
            return null;
        }
        List<SalesOrder> candidates = salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(omsOrderNo);
        if (candidates.isEmpty()) {
            return null;
        }

        List<SalesOrder> exactMatches = candidates.stream()
                .filter(candidate -> java.util.Objects.equals(normalizeBlankToNull(candidate.getModel()), normalizeBlankToNull(order.getModel())))
                .filter(candidate -> java.util.Objects.equals(candidate.getQuantity(), order.getQuantity()))
                .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.get(0);
        }

        if (order.getCreatedBy() != null) {
            List<SalesOrder> creatorMatches = exactMatches.stream()
                    .filter(candidate -> order.getCreatedBy().equals(candidate.getCreatedBy()))
                    .toList();
            if (!creatorMatches.isEmpty()) {
                return creatorMatches.get(creatorMatches.size() - 1);
            }
        }

        if (!exactMatches.isEmpty()) {
            return exactMatches.get(exactMatches.size() - 1);
        }

        return candidates.get(candidates.size() - 1);
    }

    private Long extractSalesOrderIdFromPurchaseNo(String purchaseOrderNo) {
        String text = normalizeBlankToNull(purchaseOrderNo);
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-SO(\\d+)$").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean canCurrentUserViewErpEntryScreenshot(User currentUser, PurchaseOrder order) {
        return canCurrentUserMaintainErpEntry(currentUser, order);
    }

    private boolean isRelatedFeichukeSalesOwner(User currentUser, PurchaseOrder order) {
        if (currentUser == null || order == null) {
            return false;
        }
        if (order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId())) {
            return true;
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo == null) {
            return false;
        }
        List<SalesOrder> relatedSalesOrders = salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(omsOrderNo);
        for (SalesOrder salesOrder : relatedSalesOrders) {
            if (salesOrder.getCreatedBy() != null && salesOrder.getCreatedBy().equals(currentUser.getId())) {
                return true;
            }
            String salesName = normalizeBlankToNull(salesOrder.getEcommerceSalesName());
            String currentRealName = normalizeBlankToNull(currentUser.getRealName());
            String currentUsername = normalizeBlankToNull(currentUser.getUsername());
            if (salesName != null && (salesName.equals(currentRealName) || salesName.equals(currentUsername))) {
                return true;
            }
        }
        return false;
    }

    private Set<Long> getFeichukeCreatorIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (User user : userRepository.findAll()) {
            if (isFeichukeCompany(user) && user.getId() != null) {
                ids.add(user.getId());
            }
        }
        return ids;
    }

    private boolean isFeichukeCompany(User user) {
        return user != null && user.getCompanyTitle() != null && user.getCompanyTitle().contains(FEICHUKE_TITLE);
    }

    private boolean isRexiangCompany(User user) {
        return user != null && user.getCompanyTitle() != null && user.getCompanyTitle().contains(REXIANG_TITLE);
    }

    private boolean hasPurchaseFinanceScope(User user) {
        return hasPermission(user, "settlement_finance")
                || hasPermission(user, "purchase_payment_finance")
                || hasPermission(user, "purchase_payment_view_all");
    }

    private boolean hasPermission(User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        String permissions = user.getPermissions();
        if (permissions == null || permissions.isBlank()) {
            return false;
        }
        for (String item : permissions.split(",")) {
            if (permission.equalsIgnoreCase(item == null ? "" : item.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<Long> parseMergedSalesOrderIds(String mergedSalesOrderIds) {
        if (mergedSalesOrderIds == null || mergedSalesOrderIds.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : mergedSalesOrderIds.split(",")) {
            String text = part == null ? "" : part.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUploadPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String path = rawPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                path = java.net.URI.create(path).getPath();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (path.startsWith("uploads/")) {
            path = "/" + path;
        }
        return path;
    }

    private String defaultOperatorName(User currentUser, String operator) {
        String normalized = normalizeBlankToNull(operator);
        if (normalized != null) {
            return normalized;
        }
        if (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank()) {
            return currentUser.getRealName().trim();
        }
        return currentUser != null ? currentUser.getUsername() : "";
    }

    private User getCurrentUser() {
        String currentUsername = null;
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        }
        if (currentUsername == null || currentUsername.isBlank() || "anonymousUser".equals(currentUsername)) {
            return null;
        }
        return userRepository.findByUsername(currentUsername).orElse(null);
    }
}
