package com.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.OperationLog;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.OperationLogService;
import com.oms.service.OrderShipmentService;
import com.oms.service.SalesOrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {
    @Autowired
    private SalesOrderService salesOrderService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private OrderShipmentService orderShipmentService;

    @Autowired
    private com.oms.service.DingTalkService dingTalkService;

    @Autowired
    private com.oms.service.InvoiceOcrService invoiceOcrService;

    private static final String TARGET_TYPE_SALES_ORDER = "SALES_ORDER";

    /** 含税总价与「数量×含税单价」允许偏差（元），避免浮点/四舍五入导致误拦 */
    private static final BigDecimal LINE_AMOUNT_TOLERANCE = new BigDecimal("0.02");

    private String getCurrentOperatorName() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null)
            return "SYSTEM";
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) return "SYSTEM";
        return userRepository.findByUsername(username).map(u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : username).orElse(username);
    }

    @GetMapping
    public Page<SalesOrder> list(
            @RequestParam(required = false) String omsOrderNo,
            @RequestParam(required = false) String platformOrderNo,
            @RequestParam(required = false) String partyATitleKeyword,
            @RequestParam(required = false) String deliveryPartyKeyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String excludeStatuses,
            @RequestParam(required = false) String platformRefundStatus,
            @RequestParam(required = false) String offlineSales,
            @RequestParam(required = false) String ecommerceSalesName,
            @RequestParam(required = false) String needReceiptSlip,
            @RequestParam(required = false) String receiptFilter,
            @RequestParam(required = false) String erpEntryStatus,
            @RequestParam(required = false) Boolean expectedRefundOverdue,
            @RequestParam(required = false) Boolean excludeChainOrders,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Sort pageSort = Sort.by(Sort.Direction.DESC, "createTime");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",\\s*");
            if (parts.length >= 1 && !parts[0].isEmpty()) {
                Sort.Direction dir = parts.length >= 2 && "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
                pageSort = Sort.by(dir, parts[0].trim());
            }
        }
        // 销售列表（view=sales）时强制排除他人链式单，避免派单业务员看到被指派方的销售订单/链式单
        Boolean effectiveExcludeChain = "sales".equalsIgnoreCase(view) ? Boolean.TRUE : excludeChainOrders;
        Page<SalesOrder> pageResult = salesOrderService.searchOrders(omsOrderNo, platformOrderNo, partyATitleKeyword, deliveryPartyKeyword, status, excludeStatuses, platformRefundStatus,
                offlineSales, ecommerceSalesName, needReceiptSlip, receiptFilter, erpEntryStatus, expectedRefundOverdue,
                effectiveExcludeChain, PageRequest.of(page, size, pageSort));

        // 获取当前登录用户（未登录时使用匿名，避免 NPE）；final 供 lambda 使用
        final String currentUsername;
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            String name = SecurityContextHolder.getContext().getAuthentication().getName();
            currentUsername = (name != null && !name.isBlank()) ? name : "anonymousUser";
        } else {
            currentUsername = "anonymousUser";
        }
        final Map<Long, String> unifiedPartyAByOrderId = buildUnifiedDisplayPartyAForPage(pageResult.getContent(), currentUsername);
        boolean salesOnly = "sales".equalsIgnoreCase(view);
        
        // 对被指派用户看到的订单进行数据转换（仅当非 view=sales 时做指派方视角转换）
        return pageResult.map(ord -> {
            String displayPartyA = unifiedPartyAByOrderId.getOrDefault(ord.getId(), getDisplayPartyATitleForList(ord, currentUsername));
            if (salesOnly) {
                SalesOrder copy = new SalesOrder();
                BeanUtils.copyProperties(ord, copy);
                copy.setDeductionRate(null);
                if (displayPartyA != null) {
                    copy.setPlatformName(displayPartyA);
                    copy.setOperationEntityTitle(displayPartyA);
                }
                // 仅当当前用户是该订单的「被指派方且非创建人」时，才用交付方采购价覆盖展示含税单价/总价。
                // 历史数据里存在 createdBy 与 assignedUsername 同为当前人的情况，此时销售列表应继续显示主单原始含税价。
                boolean isAssigneeOfThisOrder = ord.getAssignedUsername() != null
                        && ord.getAssignedUsername().equals(currentUsername)
                        && !isUserCreatedOrder(ord.getCreatedBy(), currentUsername);
                if (isAssigneeOfThisOrder) {
                    if (ord.getDeliveryPartyPurchasePrice() != null) {
                        copy.setTaxIncludedPrice(ord.getDeliveryPartyPurchasePrice());
                        if (ord.getQuantity() != null) {
                            copy.setTaxIncludedTotal(
                                ord.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(ord.getQuantity()))
                            );
                        }
                    }
                    if (displayPartyA != null) {
                        copy.setInvoiceTitle(displayPartyA);
                    }
                }
                mergeLogisticsFromShipmentIfMissing(copy);
                return copy;
            }
            // 被指派方（非创建人）看列表时：价格用合同价，甲方/发票按查看者展示
            if (ord.getAssignedUsername() != null && 
                ord.getAssignedUsername().equals(currentUsername) &&
                (ord.getCreatedBy() == null || !isUserCreatedOrder(ord.getCreatedBy(), currentUsername))) {
                SalesOrder transformedOrder = new SalesOrder();
                BeanUtils.copyProperties(ord, transformedOrder);
                if (displayPartyA != null) {
                    transformedOrder.setPlatformName(displayPartyA);
                    transformedOrder.setOperationEntityTitle(displayPartyA);
                    transformedOrder.setInvoiceTitle(displayPartyA);
                }
                if (ord.getDeliveryPartyPurchasePrice() != null) {
                    transformedOrder.setTaxIncludedPrice(ord.getDeliveryPartyPurchasePrice());
                    if (ord.getQuantity() != null) {
                        transformedOrder.setTaxIncludedTotal(
                            ord.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(ord.getQuantity()))
                        );
                    }
                }
                mergeLogisticsFromShipmentIfMissing(transformedOrder);
                return transformedOrder;
            }
            // 其他（创建人、转派方、管理员等）：返回副本并设置展示用甲方，避免误写的南京新测占领所有账号
            SalesOrder copy = new SalesOrder();
            BeanUtils.copyProperties(ord, copy);
            if (displayPartyA != null) {
                copy.setPlatformName(displayPartyA);
                copy.setOperationEntityTitle(displayPartyA);
            }
            mergeLogisticsFromShipmentIfMissing(copy);
            return copy;
        });
    }

    /**
     * 主表与 order_shipments 偶发不一致（例如历史数据或部分更新路径只写了其一）时，用发货单补齐母单/回单物流与物流公司，
     * 避免商务等账号列表「SN 有值、母单物流单号为空」。
     */
    private void mergeLogisticsFromShipmentIfMissing(SalesOrder target) {
        if (target == null || target.getId() == null) {
            return;
        }
        boolean needMother = target.getTrackingNumber() == null || target.getTrackingNumber().isBlank();
        boolean needReturn = target.getReturnReceiptTrackingNumber() == null || target.getReturnReceiptTrackingNumber().isBlank();
        boolean needCompany = target.getLogisticsCompany() == null || target.getLogisticsCompany().isBlank();
        boolean needReturnPhone = target.getReturnReceiptReceiverPhone() == null || target.getReturnReceiptReceiverPhone().isBlank();
        if (!needMother && !needReturn && !needCompany && !needReturnPhone) {
            return;
        }
        orderShipmentService.getShipmentBySalesOrderId(target.getId()).ifPresent(sh -> {
            if (needMother && sh.getTrackingNumber() != null && !sh.getTrackingNumber().isBlank()) {
                target.setTrackingNumber(sh.getTrackingNumber());
            }
            if (needReturn && sh.getReturnReceiptTrackingNumber() != null && !sh.getReturnReceiptTrackingNumber().isBlank()) {
                target.setReturnReceiptTrackingNumber(sh.getReturnReceiptTrackingNumber());
            }
            if (needReturnPhone && sh.getReturnReceiptReceiverPhone() != null && !sh.getReturnReceiptReceiverPhone().isBlank()) {
                target.setReturnReceiptReceiverPhone(sh.getReturnReceiptReceiverPhone());
            }
            if (needCompany && sh.getLogisticsCompany() != null && !sh.getLogisticsCompany().isBlank()) {
                target.setLogisticsCompany(sh.getLogisticsCompany());
            }
        });
    }
    
    // 辅助方法：检查用户是否是订单创建者
    private boolean isUserCreatedOrder(Long createdById, String username) {
        if (createdById == null) return false;
        User user = userRepository.findById(createdById).orElse(null);
        return user != null && user.getUsername().equals(username);
    }

    /** 列表中甲方抬头：优先使用真实业务甲方 partyATitle，兼容回退 platformName/operationEntityTitle */

    private String getDisplayPartyATitleForList(SalesOrder order, String currentUsername) {
        if (order == null) return null;
        String stored = (order.getPartyATitle() != null && !order.getPartyATitle().isBlank()) ? order.getPartyATitle().trim() : null;
        if (stored == null && order.getPlatformName() != null && !order.getPlatformName().isBlank())
            stored = order.getPlatformName().trim();
        if (stored == null && order.getOperationEntityTitle() != null && !order.getOperationEntityTitle().isBlank())
            stored = order.getOperationEntityTitle().trim();
        return stored;
    }

    /**
     * 同一页内相同 OMS 单号可能对应：多商品拆分的多条主单、或主单+链式单（CHAIN_FROM）等。
     * 列表「甲方抬头」应对创建人/上游统一为「主单」上的甲方（非 CHAIN_FROM 中 id 最小者），
     * 避免与链式单（甲方=指派方公司）混排时同一 OMS 出现飞础科/震坤行不一致。
     * 链式单创建人（被指派方）查看自己那条时仍保留该行自身展示。
     */
    private Map<Long, String> buildUnifiedDisplayPartyAForPage(List<SalesOrder> content, String currentUsername) {
        Map<Long, String> byId = new HashMap<>();
        if (content == null || content.isEmpty()) return byId;
        Long currentUserId = userRepository.findByUsername(currentUsername).map(User::getId).orElse(null);

        for (SalesOrder o : content) {
            byId.put(o.getId(), getDisplayPartyATitleForList(o, currentUsername));
        }

        Map<String, List<SalesOrder>> byOms = content.stream()
                .filter(o -> o.getOmsOrderNo() != null && !o.getOmsOrderNo().isBlank())
                .collect(Collectors.groupingBy(o -> o.getOmsOrderNo().trim()));

        for (List<SalesOrder> group : byOms.values()) {
            if (group.size() <= 1) continue;

            List<SalesOrder> mains = group.stream()
                    .filter(o -> o.getPurchaseOrderNo() == null || !o.getPurchaseOrderNo().startsWith("CHAIN_FROM:"))
                    .sorted(Comparator.comparingLong(SalesOrder::getId))
                    .collect(Collectors.toList());
            SalesOrder canonical = mains.isEmpty()
                    ? group.stream().min(Comparator.comparingLong(SalesOrder::getId)).orElse(null)
                    : mains.get(0);
            if (canonical == null) continue;
            String canonicalDisplay = getDisplayPartyATitleForList(canonical, currentUsername);

            for (SalesOrder o : group) {
                boolean chain = o.getPurchaseOrderNo() != null && o.getPurchaseOrderNo().startsWith("CHAIN_FROM:");
                boolean viewerOwnsThisChainRow = chain && currentUserId != null && o.getCreatedBy() != null
                        && o.getCreatedBy().equals(currentUserId);
                if (viewerOwnsThisChainRow) {
                    continue;
                }
                byId.put(o.getId(), canonicalDisplay);
            }
        }
        return byId;
    }

    /** 兼容旧前端：未传 partyATitle 时沿用 platformName；更新时优先保留已有 partyATitle */
    private void normalizePartyATitle(Map<String, Object> data, SalesOrder existingOrder) {
        if (data == null) return;
        String partyA = data.get("partyATitle") != null ? String.valueOf(data.get("partyATitle")).trim() : "";
        String platform = data.get("platformName") != null ? String.valueOf(data.get("platformName")).trim() : "";
        String operationEntity = data.get("operationEntityTitle") != null ? String.valueOf(data.get("operationEntityTitle")).trim() : "";
        if (!partyA.isBlank()) {
            data.put("partyATitle", partyA);
            return;
        }
        if (!platform.isBlank()) {
            data.put("partyATitle", platform);
            return;
        }
        // 更新时：优先保留已有的 partyATitle，避免 operationEntityTitle（经营实体）覆盖真实甲方
        if (existingOrder != null && existingOrder.getPartyATitle() != null && !existingOrder.getPartyATitle().isBlank()) {
            data.put("partyATitle", existingOrder.getPartyATitle().trim());
            return;
        }
        if (!operationEntity.isBlank()) {
            data.put("partyATitle", operationEntity);
        }
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> save(@RequestBody Map<String, Object> orderData) {
        System.out.println("POST /api/sales-orders called with orderData: " + orderData);
        try {
            Map<String, Object> processedData = new HashMap<>(orderData);
            normalizePartyATitle(processedData, null);
            processDeductionRate(processedData);
            validateOrderLineAmounts(processedData);

            // 兼容旧前端：若一次提交了多商品，后端拆分为多条销售单保存，避免只生成一条
            List<Map<String, Object>> products = asMapList(orderData.get("products"));
            if (products.size() > 1) {
                List<Map<String, Object>> logistics = asMapList(orderData.get("logistics"));
                List<Map<String, Object>> invoices = asMapList(orderData.get("invoices"));
                List<Map<String, Object>> reconciliations = asMapList(orderData.get("reconciliations"));
                List<Map<String, Object>> createdList = new ArrayList<>();

                for (int i = 0; i < products.size(); i++) {
                    Map<String, Object> singleData = new HashMap<>(processedData);
                    Map<String, Object> p = products.get(i);

                    // 商品主字段回填到顶层，保证每条销售单都有自己的商品信息
                    copyIfPresent(p, singleData, "omsOrderNo");
                    copyIfPresent(p, singleData, "platformSku");
                    copyIfPresent(p, singleData, "model");
                    copyIfPresent(p, singleData, "materialNo");
                    copyIfPresent(p, singleData, "productConfig");
                    copyIfPresent(p, singleData, "warrantyPeriod");
                    copyIfPresent(p, singleData, "quantity");
                    copyIfPresent(p, singleData, "taxIncludedPrice");
                    copyIfPresent(p, singleData, "taxIncludedTotal");
                    copyIfPresent(p, singleData, "orderType");

                    List<Map<String, Object>> productLogistics = filterByProductIndex(logistics, i);
                    List<Map<String, Object>> productInvoices = filterByProductIndex(invoices, i);
                    List<Map<String, Object>> productReconciliations = filterByProductIndex(reconciliations, i);

                    if (!productLogistics.isEmpty()) {
                        Map<String, Object> l0 = productLogistics.get(0);
                        copyIfPresent(l0, singleData, "receiverName");
                        copyIfPresent(l0, singleData, "receiverPhone");
                        copyIfPresent(l0, singleData, "receiverAddress");
                        copyIfPresent(l0, singleData, "deliveryParty");
                        copyIfPresent(l0, singleData, "shippingParty");
                        copyIfPresent(l0, singleData, "deliveryDate");
                    }
                    if (!productInvoices.isEmpty()) {
                        Map<String, Object> inv0 = productInvoices.get(0);
                        copyIfPresent(inv0, singleData, "invoiceTitle");
                        copyIfPresent(inv0, singleData, "finalCustomerTitle");
                        copyIfPresent(inv0, singleData, "operationEntityTitle");
                        copyIfPresent(inv0, singleData, "paymentMethod");
                        copyIfPresent(inv0, singleData, "invoiceNumber");
                    }
                    if (!productReconciliations.isEmpty()) {
                        Map<String, Object> rec0 = productReconciliations.get(0);
                        copyIfPresent(rec0, singleData, "deliveryPartyPurchasePrice");
                        copyIfPresent(rec0, singleData, "deductionRate");
                        copyIfPresent(rec0, singleData, "settlementNo");
                        copyIfPresent(rec0, singleData, "platformReconciliationNo");
                        copyIfPresent(rec0, singleData, "offlineSales");
                        copyIfPresent(rec0, singleData, "offlineContractNo");
                        copyIfPresent(rec0, singleData, "offlineShippingPrice");
                    }

                    // 多商品拆分：每条子单共用顶层已归一的甲方抬头/平台名，避免发票子行 operationEntityTitle 覆盖导致各行甲方不一致
                    if (processedData.get("partyATitle") != null)
                        singleData.put("partyATitle", processedData.get("partyATitle"));
                    if (processedData.get("platformName") != null)
                        singleData.put("platformName", processedData.get("platformName"));
                    if (processedData.get("topLevelCustomerName") != null)
                        singleData.put("topLevelCustomerName", processedData.get("topLevelCustomerName"));

                    singleData.put("products", List.of(p));
                    singleData.put("logistics", productLogistics);
                    singleData.put("invoices", productInvoices);
                    singleData.put("reconciliations", productReconciliations);

                    SalesOrder singleOrder = objectMapper.convertValue(singleData, SalesOrder.class);
                    SalesOrder saved = salesOrderService.saveOrder(singleOrder);

                    Map<String, Object> details = new HashMap<>();
                    details.put("products", List.of(p));
                    details.put("logistics", productLogistics);
                    details.put("invoices", productInvoices);
                    details.put("reconciliations", productReconciliations);
                    saved.setOrderDetails(objectMapper.writeValueAsString(details));
                    saved = salesOrderService.saveOrder(saved);

                    Map<String, Object> out = objectMapper.convertValue(saved, Map.class);
                    out.put("products", List.of(p));
                    out.put("logistics", productLogistics);
                    out.put("invoices", productInvoices);
                    out.put("reconciliations", productReconciliations);
                    createdList.add(out);
                }

                String operatorName = getCurrentOperatorName();
                operationLogService.log(operatorName, "新建销售订单", "SALES_ORDER", "BATCH",
                        "批量新建 " + createdList.size() + " 条（同一请求多商品拆分保存）");

                List<SalesOrder> createdOrdersForNotify = new ArrayList<>();
                for (Map<String, Object> item : createdList) {
                    try {
                        createdOrdersForNotify.add(objectMapper.convertValue(item, SalesOrder.class));
                    } catch (Exception ignored) {}
                }
                notifyOrderCreatedBatch(createdOrdersForNotify, operatorName);

                Map<String, Object> first = createdList.get(0);
                first.put("createdCount", createdList.size());
                first.put("createdOrderIds", createdList.stream().map(m -> m.get("id")).collect(Collectors.toList()));
                return first;
            }

            syncOrderFromFirstProduct(processedData);
            syncOrderFromFirstLogistics(processedData);
            syncOrderFromFirstReconciliation(processedData);
            processDeductionRate(processedData);
            SalesOrder order = objectMapper.convertValue(processedData, SalesOrder.class);
            
            SalesOrder savedOrder = salesOrderService.saveOrder(order);
            
            Map<String, Object> details = new HashMap<>();
            details.put("products", orderData.get("products"));
            details.put("logistics", orderData.get("logistics"));
            details.put("invoices", orderData.get("invoices"));
            details.put("reconciliations", orderData.get("reconciliations"));
            
            savedOrder.setOrderDetails(objectMapper.writeValueAsString(details));
            savedOrder = salesOrderService.saveOrder(savedOrder);
            
            String operatorName = getCurrentOperatorName();
            String detailStr = "新建，订单号 " + (savedOrder.getOmsOrderNo() != null ? savedOrder.getOmsOrderNo() : savedOrder.getPlatformOrderNo())
                + "，金额 " + (savedOrder.getTaxIncludedTotal() != null ? savedOrder.getTaxIncludedTotal().toString() : "-");
            operationLogService.log(operatorName, "新建销售订单", "SALES_ORDER", String.valueOf(savedOrder.getId()), detailStr);
            
            notifyOrderCreated(savedOrder, operatorName);
            
            Map<String, Object> result = objectMapper.convertValue(savedOrder, Map.class);
            result.put("products", orderData.get("products"));
            result.put("logistics", orderData.get("logistics"));
            result.put("invoices", orderData.get("invoices"));
            result.put("reconciliations", orderData.get("reconciliations"));
            
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error saving order: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("保存订单失败", e);
        }
    }

    private void notifyOrderCreated(SalesOrder order, String operatorName) {
        try {
            String orderNo = order.getOmsOrderNo() != null ? order.getOmsOrderNo() : order.getPlatformOrderNo();
            String partyA = order.getPartyATitle() != null ? order.getPartyATitle() : order.getPlatformName();
            String model = order.getModel() != null ? order.getModel() : "-";
            String amount = order.getTaxIncludedTotal() != null ? "¥" + order.getTaxIncludedTotal().toPlainString() : "-";
            String qty = order.getQuantity() != null ? String.valueOf(order.getQuantity()) : "-";
            String offlineSales = order.getOfflineSales() != null && !order.getOfflineSales().isBlank()
                    ? order.getOfflineSales().trim() : "-";
            String offlineContractNo = order.getOfflineContractNo() != null && !order.getOfflineContractNo().isBlank()
                    ? order.getOfflineContractNo().trim() : "-";
            String offlineShip = order.getOfflineShippingPrice() != null
                    ? "¥" + order.getOfflineShippingPrice().toPlainString() : "-";
            String title = "📦 新订单创建通知";
            String text = "## 📦 新订单创建通知\n\n"
                    + "**OMS订单号：** " + (orderNo != null ? orderNo : "-") + "\n\n"
                    + "**甲方抬头：** " + (partyA != null ? partyA : "-") + "\n\n"
                    + "**甲方订单号：** " + (order.getPlatformOrderNo() != null ? order.getPlatformOrderNo() : "-") + "\n\n"
                    + "**型号：** " + model + "\n\n"
                    + "**数量：** " + qty + "\n\n"
                    + "**金额：** " + amount + "\n\n"
                    + "**线下销售：** " + offlineSales + "\n\n"
                    + "**线下销售合同号：** " + offlineContractNo + "\n\n"
                    + "**线下销售出货价：** " + offlineShip + "\n\n"
                    + "**创建人：** " + (operatorName != null ? operatorName : "-") + "\n\n"
                    + "---\n"
                    + "*来自 OMS 订单系统*";
            dingTalkService.sendMarkdownMessage(title, text);
        } catch (Exception ex) {
            System.err.println("发送新订单创建钉钉通知失败: " + ex.getMessage());
        }
    }

    private void notifyOrderCreatedBatch(List<SalesOrder> orders, String operatorName) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Map<String, List<SalesOrder>> grouped = new LinkedHashMap<>();
        for (SalesOrder order : orders) {
            if (order == null) continue;
            String key = safe(order.getOmsOrderNo()) + "|" + safe(order.getPlatformOrderNo()) + "|" + safe(resolvePartyA(order));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(order);
        }
        for (List<SalesOrder> group : grouped.values()) {
            if (group == null || group.isEmpty()) continue;
            if (group.size() == 1) {
                notifyOrderCreated(group.get(0), operatorName);
                continue;
            }
            notifyOrderCreatedMerged(group, operatorName);
        }
    }

    private void notifyOrderCreatedMerged(List<SalesOrder> orders, String operatorName) {
        try {
            SalesOrder first = orders.get(0);
            String orderNo = first.getOmsOrderNo() != null ? first.getOmsOrderNo() : first.getPlatformOrderNo();
            String partyA = resolvePartyA(first);
            int totalQty = orders.stream()
                    .map(SalesOrder::getQuantity)
                    .filter(q -> q != null && q > 0)
                    .reduce(0, Integer::sum);
            BigDecimal totalAmount = orders.stream()
                    .map(this::resolveNotifyOrderAmount)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String detailLines = orders.stream()
                    .map(order -> "- " + safe(order.getModel())
                            + " × " + (order.getQuantity() != null ? order.getQuantity() : 1)
                            + "，" + moneyOrDash(resolveNotifyOrderAmount(order)))
                    .collect(Collectors.joining("\n"));
            LinkedHashSet<String> offlineSalesSet = orders.stream()
                    .map(SalesOrder::getOfflineSales)
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String title = "📦 新订单创建通知";
            String text = "## 📦 新订单创建通知\n\n"
                    + "**OMS订单号：** " + safe(orderNo) + "\n\n"
                    + "**甲方抬头：** " + safe(partyA) + "\n\n"
                    + "**甲方订单号：** " + safe(first.getPlatformOrderNo()) + "\n\n"
                    + "**商品行数：** " + orders.size() + "\n\n"
                    + "**合计数量：** " + (totalQty > 0 ? totalQty : orders.size()) + "\n\n"
                    + "**合计金额：** " + moneyOrDash(totalAmount) + "\n\n"
                    + "**商品型号：**\n" + detailLines + "\n\n"
                    + "**线下销售：** " + (offlineSalesSet.isEmpty() ? "-" : String.join(" / ", offlineSalesSet)) + "\n\n"
                    + "**创建人：** " + safe(operatorName) + "\n\n"
                    + "---\n"
                    + "*来自 OMS 订单系统*";
            dingTalkService.sendMarkdownMessage(title, text);
        } catch (Exception ex) {
            System.err.println("发送聚合新订单创建钉钉通知失败: " + ex.getMessage());
        }
    }

    private String resolvePartyA(SalesOrder order) {
        if (order == null) return "-";
        if (order.getPartyATitle() != null && !order.getPartyATitle().isBlank()) return order.getPartyATitle().trim();
        if (order.getPlatformName() != null && !order.getPlatformName().isBlank()) return order.getPlatformName().trim();
        return "-";
    }

    private BigDecimal resolveNotifyOrderAmount(SalesOrder order) {
        if (order == null) return null;
        if (order.getTaxIncludedPrice() != null && order.getQuantity() != null && order.getQuantity() > 0) {
            try {
                return order.getTaxIncludedPrice().multiply(new BigDecimal(order.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignore) {}
        }
        if (order.getTaxIncludedTotal() == null) return null;
        return order.getTaxIncludedTotal().setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyOrDash(BigDecimal amount) {
        return amount == null ? "-" : "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private List<Map<String, Object>> filterByProductIndex(List<Map<String, Object>> list, int productIndex) {
        if (list == null || list.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> item : list) {
            Integer idx = toInt(item.get("productIndex"));
            if (idx == null) {
                // 兼容没有 productIndex 的旧数据，默认归到第一条商品
                if (productIndex == 0) matched.add(item);
            } else if (idx == productIndex) {
                matched.add(item);
            }
        }
        return matched;
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source == null || target == null || key == null) return;
        if (!source.containsKey(key)) return;
        Object v = source.get(key);
        if (v != null) {
            target.put(key, v);
        }
    }

    /**
     * 校验每行商品：含税总价须与「数量×含税单价」一致（±0.02 元），防止 OCR/PDF 导入金额识别错误直接落库。
     */
    private void validateOrderLineAmounts(Map<String, Object> data) {
        if (data == null) return;
        List<Map<String, Object>> products = asMapList(data.get("products"));
        if (!products.isEmpty()) {
            for (int i = 0; i < products.size(); i++) {
                assertLineQtyPriceTotal(products.get(i), "第" + (i + 1) + "个商品");
            }
        } else {
            assertLineQtyPriceTotal(data, "订单");
        }
    }

    private void assertLineQtyPriceTotal(Map<String, Object> line, String label) {
        if (line == null) return;
        Integer qty = parsePositiveQuantity(line.get("quantity"));
        BigDecimal unitPrice = toBigDecimalMoney(line.get("taxIncludedPrice"));
        BigDecimal lineTotal = toBigDecimalMoney(line.get("taxIncludedTotal"));
        if (qty == null || qty <= 0) {
            return;
        }
        if (unitPrice == null || lineTotal == null) {
            throw new IllegalArgumentException(label + "：请填写含税单价与含税总价，且总价须等于数量×单价（请核对 OCR 识别结果）。");
        }
        BigDecimal expected = unitPrice.multiply(new BigDecimal(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedTotal = lineTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = expected.subtract(normalizedTotal).abs();
        if (diff.compareTo(LINE_AMOUNT_TOLERANCE) > 0) {
            throw new IllegalArgumentException(String.format(
                    "%s：含税总价与「数量×含税单价」不一致（请核对 OCR）。数量=%d，含税单价=%s，按此计算总价应为 %s，当前填写含税总价=%s。",
                    label, qty, unitPrice.toPlainString(), expected.toPlainString(), normalizedTotal.toPlainString()));
        }
    }

    private Integer parsePositiveQuantity(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof Number n) {
                int q = n.intValue();
                return q > 0 ? q : null;
            }
            String s = String.valueOf(value).trim();
            if (s.isEmpty()) return null;
            int q = s.contains(".") ? (int) Double.parseDouble(s) : Integer.parseInt(s);
            return q > 0 ? q : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimalMoney(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            try {
                return new BigDecimal(n.toString());
            } catch (Exception e) {
                return null;
            }
        }
        String s = String.valueOf(value).trim().replace(",", "").replace("，", "").replace("¥", "").replace("￥", "");
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private User getCurrentUserOrNull() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null || name.isBlank()) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    private boolean hasPermission(User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) return false;
        String p = user.getPermissions();
        if (p == null || p.isBlank()) return false;
        return java.util.Arrays.stream(p.split(","))
                .map(String::trim)
                .anyMatch(permission::equalsIgnoreCase);
    }

    /** 销售结算权限：可维护平台对账单号/结算单号 */
    private boolean canEditSettlementBySales() {
        User user = getCurrentUserOrNull();
        if (user == null) return false;
        if ("ROLE_ADMIN".equals(user.getRole())) return true;
        return hasPermission(user, "settlement_sales");
    }

    /** 财务结算权限：可维护发票号/发票附件/平台回款状态（兼容 platform_refund） */
    private boolean canEditSettlementByFinance() {
        User user = getCurrentUserOrNull();
        if (user == null) return false;
        if ("ROLE_ADMIN".equals(user.getRole())) return true;
        return hasPermission(user, "settlement_finance") || hasPermission(user, "platform_refund");
    }

    private void sanitizeSettlementFields(Map<String, Object> data, SalesOrder existingOrder, boolean canSales, boolean canFinance) {
        if (data == null) return;
        if (!canSales) {
            data.put("platformReconciliationNo", existingOrder != null ? existingOrder.getPlatformReconciliationNo() : null);
            data.put("settlementNo", existingOrder != null ? existingOrder.getSettlementNo() : null);
            data.put("platformReconciliationUrl", existingOrder != null ? existingOrder.getPlatformReconciliationUrl() : null);
            data.put("settlementUrl", existingOrder != null ? existingOrder.getSettlementUrl() : null);
        }
        if (!canFinance) {
            data.put("invoiceNumber", existingOrder != null ? existingOrder.getInvoiceNumber() : null);
            data.put("invoiceUrl", existingOrder != null ? existingOrder.getInvoiceUrl() : null);
            data.put("platformRefundStatus", existingOrder != null ? existingOrder.getPlatformRefundStatus() : null);
            data.put("platformRefundUrl", existingOrder != null ? existingOrder.getPlatformRefundUrl() : null);
        }
    }

    @PutMapping("/{id}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> orderData) {
        System.out.println("PUT /api/sales-orders/" + id + " called with orderData: " + orderData);
        System.out.println("deliveryPartyPurchasePrice in request: " + orderData.get("deliveryPartyPurchasePrice"));
        try {
            SalesOrder existingOrder = salesOrderService.getAllOrders().stream()
                    .filter(o -> o.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("订单不存在"));
            
            Map<String, Object> processedData = new HashMap<>(orderData);
            normalizePartyATitle(processedData, existingOrder);
            boolean canSalesSettlement = canEditSettlementBySales();
            boolean canFinanceSettlement = canEditSettlementByFinance();
            sanitizeSettlementFields(processedData, existingOrder, canSalesSettlement, canFinanceSettlement);
            processDeductionRate(processedData);
            // 指派/转派时前端显式传的交付方采购价（如 3550）必须保留，避免被后续回写或 saveOrder 内扣点算法覆盖
            Object explicitPurchasePrice = orderData.get("deliveryPartyPurchasePrice");
            // 编辑时商品/物流在子表里，列表展示的是订单主表字段，需用第一个商品和第一条物流回填主表，列表才能显示最新
            syncOrderFromFirstProduct(processedData);
            syncOrderFromFirstLogistics(processedData);
            syncOrderFromFirstReconciliation(processedData);
            processDeductionRate(processedData);
            validateOrderLineAmounts(processedData);
            if (explicitPurchasePrice != null && !"".equals(explicitPurchasePrice.toString().trim())) {
                processedData.put("deliveryPartyPurchasePrice", explicitPurchasePrice);
            }
            // 指派时选择的支付方式（如背靠背）必须回写，避免被覆盖导致被指派方看到仍是账期
            Object requestPaymentMethod = orderData.get("paymentMethod");
            if (requestPaymentMethod != null && !"".equals(requestPaymentMethod.toString().trim())) {
                processedData.put("paymentMethod", requestPaymentMethod.toString().trim());
            }
            SalesOrder order = objectMapper.convertValue(processedData, SalesOrder.class);
            order.setId(id);
            order.setCreateTime(existingOrder.getCreateTime());
            order.setCreatedBy(existingOrder.getCreatedBy());
            
            System.out.println("Converted order deliveryPartyPurchasePrice: " + order.getDeliveryPartyPurchasePrice());
            SalesOrder savedOrder = salesOrderService.saveOrder(order);
            
            Map<String, Object> details = new HashMap<>();
            details.put("products", processedData.get("products"));
            details.put("logistics", processedData.get("logistics"));
            details.put("invoices", processedData.get("invoices"));
            details.put("reconciliations", processedData.get("reconciliations"));
            
            savedOrder.setOrderDetails(objectMapper.writeValueAsString(details));
            savedOrder = salesOrderService.saveOrder(savedOrder);
            
            String operatorName = getCurrentOperatorName();
            operationLogService.log(operatorName, "编辑销售订单", "SALES_ORDER", String.valueOf(id), "编辑订单");
            
            Map<String, Object> result = objectMapper.convertValue(savedOrder, Map.class);
            result.put("products", processedData.get("products"));
            result.put("logistics", processedData.get("logistics"));
            result.put("invoices", processedData.get("invoices"));
            result.put("reconciliations", processedData.get("reconciliations"));

            salesOrderService.handleSettlementLifecycleAfterPersist(
                    id,
                    existingOrder.getPlatformReconciliationNo(),
                    existingOrder.getInvoiceNumber(),
                    existingOrder.getPlatformRefundStatus());

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error updating order: " + e.getMessage());
            e.printStackTrace();
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("Communications") || msg.contains("Connection") || msg.contains("SQL")) {
                msg = "数据库连接或执行异常，请检查 MySQL 是否启动及配置是否正确: " + msg;
            }
            throw new RuntimeException(msg, e);
        }
    }

    /** 用请求体中 reconciliations[0] 回填主表结算/对账字段，保证列表「对账单号」等与对账明细一致 */
    @SuppressWarnings("unchecked")
    private void syncOrderFromFirstReconciliation(Map<String, Object> data) {
        Object recObj = data.get("reconciliations");
        if (!(recObj instanceof List) || ((List<?>) recObj).isEmpty()) {
            return;
        }
        Object first = ((List<?>) recObj).get(0);
        if (!(first instanceof Map)) {
            return;
        }
        Map<String, Object> firstRec = (Map<String, Object>) first;
        String[] keys = {"settlementNo", "platformReconciliationNo", "offlineSales", "offlineContractNo",
                "offlineShippingPrice", "deductionRate", "deliveryPartyPurchasePrice"};
        for (String key : keys) {
            if (!firstRec.containsKey(key)) {
                continue;
            }
            Object v = firstRec.get(key);
            if (v != null) {
                data.put(key, v);
            }
        }
    }
    
    /** 用请求体中的 products[0] 回填订单主表字段（platformSku 等），保证列表展示与编辑一致 */
    @SuppressWarnings("unchecked")
    private void syncOrderFromFirstProduct(Map<String, Object> data) {
        Object productsObj = data.get("products");
        if (!(productsObj instanceof List) || ((List<?>) productsObj).isEmpty()) return;
        Object first = ((List<?>) productsObj).get(0);
        if (!(first instanceof Map)) return;
        Map<String, Object> firstProduct = (Map<String, Object>) first;
        // 含 omsOrderNo：商品明细中修改的工业电商销售订单号回写主表，列表与编辑框一致
        String[] keys = {"platformSku", "model", "productName", "materialNo", "productConfig", "warrantyPeriod", "quantity", "taxIncludedPrice", "taxIncludedTotal", "orderType", "omsOrderNo"};
        for (String key : keys) {
            if (!firstProduct.containsKey(key)) continue;
            Object v = firstProduct.get(key);
            if (v != null) data.put(key, v);
            else if ("omsOrderNo".equals(key)) data.put(key, "");
        }
    }

    /** 用请求体中的 logistics[0] 回填订单主表物流字段（收货人、地址等），保证列表展示与编辑一致。
     * 若请求体顶层已显式传入 deliveryParty/shippingParty/deliveryDate（如指派弹窗生成合同时），则不使用 logistics[0] 覆盖，避免合同乙方等被旧物流数据覆盖。 */
    @SuppressWarnings("unchecked")
    private void syncOrderFromFirstLogistics(Map<String, Object> data) {
        Object logisticsObj = data.get("logistics");
        if (!(logisticsObj instanceof List) || ((List<?>) logisticsObj).isEmpty()) return;
        Object first = ((List<?>) logisticsObj).get(0);
        if (!(first instanceof Map)) return;
        Map<String, Object> firstLogistics = (Map<String, Object>) first;
        String[] keys = {"receiverName", "receiverPhone", "receiverAddress", "deliveryParty", "shippingParty", "deliveryDate"};
        for (String key : keys) {
            if (!firstLogistics.containsKey(key) || firstLogistics.get(key) == null) continue;
            Object existing = data.get(key);
            if (existing != null && (key.equals("deliveryParty") || key.equals("shippingParty") || key.equals("deliveryDate"))) {
                String existingStr = existing.toString().trim();
                if (!existingStr.isEmpty()) continue;
            }
            data.put(key, firstLogistics.get(key));
        }
    }

    private void processDeductionRate(Map<String, Object> data) {
        if (data.containsKey("deductionRate")) {
            Object value = data.get("deductionRate");
            if (value instanceof String) {
                String strValue = (String) value;
                strValue = strValue.replace("%", "").trim();
                if (!strValue.isEmpty()) {
                    data.put("deductionRate", strValue);
                } else {
                    data.remove("deductionRate");
                }
            }
        }
    }
    
    @GetMapping("/{id}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrder(@PathVariable Long id) {
        System.out.println("GET /api/sales-orders/" + id + " called");
        try {
            SalesOrder order = salesOrderService.getAllOrders().stream()
                    .filter(o -> o.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("订单不存在"));
            
            // 获取当前登录用户
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            System.out.println("Current user: " + currentUsername);
            System.out.println("Order assignedUsername: " + order.getAssignedUsername());

            // 特殊限制：热像科技-财务、热像科技-仓库仅可查看甲方抬头为飞础科的订单
            if (!salesOrderService.canCurrentUserViewOrder(order)) {
                throw new RuntimeException("无权访问该订单");
            }

            mergeLogisticsFromShipmentIfMissing(order);
            salesOrderService.sanitizeErpEntryVisibility(order);

            // 检查是否是被指派用户
            boolean isAssignedUser = order.getAssignedUsername() != null && 
                                    order.getAssignedUsername().equals(currentUsername);
            System.out.println("Is assigned user: " + isAssignedUser);
            
            Map<String, Object> result = objectMapper.convertValue(order, Map.class);
            
            result.put("products", new ArrayList<>());
            result.put("logistics", new ArrayList<>());
            result.put("invoices", new ArrayList<>());
            result.put("reconciliations", new ArrayList<>());
            
            if (order.getOrderDetails() != null && !order.getOrderDetails().isEmpty()) {
                Map<String, Object> details = objectMapper.readValue(order.getOrderDetails(), Map.class);
                if (details.get("products") != null) {
                    List<Map<String, Object>> products = (List<Map<String, Object>>) details.get("products");
                    // 对于被指派用户，修改产品数据中的价格字段
                    if (isAssignedUser) {
                        System.out.println("Processing products for assigned user...");
                        for (Map<String, Object> product : products) {
                            // 使用 deliveryPartyPurchasePrice 作为 taxIncludedPrice
                            if (order.getDeliveryPartyPurchasePrice() != null) {
                                System.out.println("Setting product taxIncludedPrice to deliveryPartyPurchasePrice: " + order.getDeliveryPartyPurchasePrice());
                                product.put("taxIncludedPrice", order.getDeliveryPartyPurchasePrice().toString());
                                // 重新计算含税总价
                                Object quantityObj = product.get("quantity");
                                if (quantityObj != null) {
                                    try {
                                        int quantity = Integer.parseInt(quantityObj.toString());
                                        java.math.BigDecimal price = order.getDeliveryPartyPurchasePrice();
                                        java.math.BigDecimal total = price.multiply(new java.math.BigDecimal(quantity));
                                        product.put("taxIncludedTotal", total.toString());
                                        System.out.println("Calculated taxIncludedTotal: " + total);
                                    } catch (Exception e) {
                                        System.err.println("Error calculating total: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    result.put("products", products);
                }
                if (details.get("logistics") != null) {
                    result.put("logistics", details.get("logistics"));
                }
                if (details.get("invoices") != null) {
                    result.put("invoices", details.get("invoices"));
                }
                if (details.get("reconciliations") != null) {
                    result.put("reconciliations", details.get("reconciliations"));
                }
            }
            
            // 对于被指派用户，同时修改主订单数据中的价格字段
            if (isAssignedUser && order.getDeliveryPartyPurchasePrice() != null) {
                System.out.println("Setting main order taxIncludedPrice to deliveryPartyPurchasePrice: " + order.getDeliveryPartyPurchasePrice());
                result.put("taxIncludedPrice", order.getDeliveryPartyPurchasePrice().toString());
                // 重新计算含税总价
                if (order.getQuantity() != null) {
                    java.math.BigDecimal total = order.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(order.getQuantity()));
                    result.put("taxIncludedTotal", total.toString());
                    System.out.println("Calculated main order taxIncludedTotal: " + total);
                }
            }
            // 编辑时返回原始 platformName/operationEntityTitle，避免覆盖后回写导致甲方抬头被污染
            // 另外提供 displayPartyATitle 供前端仅展示用
            String displayPartyA = getDisplayPartyATitleForList(order, currentUsername);
            if (displayPartyA != null) {
                result.put("displayPartyATitle", displayPartyA);
            }
            return result;
        } catch (Exception e) {
            System.err.println("Error getting order: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("获取订单失败", e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        salesOrderService.deleteOrder(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/generate-no")
    public String generateNo(@RequestParam Long userId,
                             @RequestParam String orderType,
                             @RequestParam(required = false) String orderDate) {
        LocalDate effectiveOrderDate = null;
        if (orderDate != null && !orderDate.isBlank()) {
            effectiveOrderDate = LocalDate.parse(orderDate.trim());
        }
        return salesOrderService.generateOmsOrderNo(userId, orderType, effectiveOrderDate);
    }

    @PatchMapping("/{id}/erp-entry")
    public SalesOrder updateErpEntry(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String screenshotUrl = body != null ? String.valueOf(body.getOrDefault("erpEntryScreenshotUrl", "")) : "";
        String operator = body != null ? String.valueOf(body.getOrDefault("erpEntryOperator", "")) : "";
        LocalDateTime entryTime = null;
        if (body != null && body.get("erpEntryTime") != null) {
            entryTime = parseFlexibleDateTime(String.valueOf(body.get("erpEntryTime")));
        }
        SalesOrder updated = salesOrderService.updateErpEntry(id, screenshotUrl, operator, entryTime);
        operationLogService.log(getCurrentOperatorName(), "维护商务ERP录单", TARGET_TYPE_SALES_ORDER, String.valueOf(id),
                "状态=" + (updated.getErpEntryStatus() != null ? updated.getErpEntryStatus() : "")
                        + "，录单人=" + (updated.getErpEntryOperator() != null ? updated.getErpEntryOperator() : ""));
        return updated;
    }

    private LocalDateTime parseFlexibleDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    @PatchMapping("/{id}/audit")
    public SalesOrder audit(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, Object> params) {
        LocalDate deliveryDate = null;
        if (params != null && params.containsKey("deliveryDate")) {
            deliveryDate = java.time.LocalDate.parse(params.get("deliveryDate").toString());
        }
        return salesOrderService.auditOrder(id, deliveryDate);
    }

    @PatchMapping("/{id}/status")
    public SalesOrder updateStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String newStatus = request.get("status");
        SalesOrder order = salesOrderService.getAllOrders().stream().filter(o -> o.getId().equals(id)).findFirst().orElse(null);
        String oldStatus = order != null ? order.getStatus() : null;
        SalesOrder updated = salesOrderService.updateStatus(id, newStatus);
        operationLogService.log(getCurrentOperatorName(), "状态变更", TARGET_TYPE_SALES_ORDER, String.valueOf(id),
                "状态由 " + (oldStatus != null ? oldStatus : "") + " 改为 " + (newStatus != null ? newStatus : ""));
        return updated;
    }

    @PatchMapping("/{id}/contract")
    public SalesOrder updateContractUrl(@PathVariable Long id, @RequestBody java.util.Map<String, Object> request) {
        System.out.println("updateContractUrl called with id: " + id);
        System.out.println("request: " + request);
        String contractUrl = (String) request.get("contractUrl");
        System.out.println("contractUrl: " + contractUrl);
        return salesOrderService.updateContractUrl(id, contractUrl);
    }

    @PatchMapping("/{id}/return")
    public SalesOrder returnOrder(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String returnReason = request.get("returnReason");
        return salesOrderService.returnOrder(id, returnReason);
    }

    @PatchMapping("/batch-return")
    public java.util.List<SalesOrder> batchReturnOrders(@RequestBody java.util.Map<String, Object> request) {
        String returnReason = request != null && request.get("returnReason") != null
                ? request.get("returnReason").toString() : null;
        java.util.List<Long> ids = new java.util.ArrayList<>();
        Object rawIds = request != null ? request.get("ids") : null;
        if (rawIds instanceof java.util.List<?> rawList) {
            for (Object item : rawList) {
                if (item == null) {
                    continue;
                }
                if (item instanceof Number number) {
                    ids.add(number.longValue());
                    continue;
                }
                try {
                    ids.add(Long.parseLong(item.toString().trim()));
                } catch (Exception ignore) {
                }
            }
        }
        return salesOrderService.returnOrders(ids, returnReason);
    }

    /** 更新签收单：签收单URL、签收时间、签收状态（列表内上传签收单/预览/填签收时间后保存） */
    @PatchMapping("/{id}/receipt")
    public SalesOrder updateReceipt(@PathVariable Long id, @RequestBody java.util.Map<String, Object> request) {
        String receiptUrl = request != null && request.containsKey("receiptUrl") ? (String) request.get("receiptUrl") : null;
        String receiptStatus = request != null && request.containsKey("receiptStatus") ? (String) request.get("receiptStatus") : null;
        java.time.LocalDateTime receiptTime = null;
        if (request != null && request.get("receiptTime") != null && !request.get("receiptTime").toString().isBlank()) {
            String rtStr = request.get("receiptTime").toString().trim();
            try {
                receiptTime = rtStr.contains(" ") 
                    ? java.time.LocalDateTime.parse(rtStr.replace(" ", "T")) 
                    : java.time.LocalDateTime.parse(rtStr);
            } catch (Exception e) {
                try {
                    receiptTime = java.time.LocalDateTime.parse(rtStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception e2) {
                    try {
                        receiptTime = java.time.LocalDate.parse(rtStr).atStartOfDay();
                    } catch (Exception e3) {
                        // ignore
                    }
                }
            }
        }
        SalesOrder updated = salesOrderService.updateReceipt(id, receiptUrl, receiptTime, receiptStatus);
        operationLogService.log(getCurrentOperatorName(), "上传/更新签收单", TARGET_TYPE_SALES_ORDER, String.valueOf(id),
                (receiptUrl != null ? "签收单已上传" : "") + (receiptTime != null ? " 签收时间:" + receiptTime : ""));
        return updated;
    }

    /** 上传发票附件后 OCR 解析：回填发票号码/开票日期建议值，并提示购买方/销售方是否与订单一致 */
    @PostMapping("/{id}/invoice-ocr-validate")
    public com.oms.dto.InvoiceOcrValidationResult validateInvoiceOcr(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String invoiceUrl = body != null && body.get("invoiceUrl") != null ? String.valueOf(body.get("invoiceUrl")) : null;
        String originalFilename = body != null && body.get("originalFilename") != null
                ? String.valueOf(body.get("originalFilename")) : null;
        return invoiceOcrService.analyzeSalesInvoice(id, invoiceUrl, originalFilename);
    }

    /** 仅更新结算字段（避免结算弹窗走全量 PUT 覆盖甲方抬头等字段） */
    @PatchMapping("/{id}/settlement")
    public SalesOrder updateSettlement(@PathVariable Long id, @RequestBody java.util.Map<String, Object> request) {
        SalesOrder order = salesOrderService.getAllOrders().stream()
                .filter(o -> o.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        String prevPrn = order.getPlatformReconciliationNo();
        String prevInv = order.getInvoiceNumber();
        String prevRefund = order.getPlatformRefundStatus();
        if (request.containsKey("settlementNo"))
            order.setSettlementNo(request.get("settlementNo") != null ? request.get("settlementNo").toString().trim() : null);
        if (request.containsKey("settlementUrl"))
            order.setSettlementUrl(request.get("settlementUrl") != null ? request.get("settlementUrl").toString().trim() : null);
        if (request.containsKey("platformReconciliationNo"))
            order.setPlatformReconciliationNo(request.get("platformReconciliationNo") != null ? request.get("platformReconciliationNo").toString().trim() : null);
        if (request.containsKey("platformReconciliationUrl"))
            order.setPlatformReconciliationUrl(request.get("platformReconciliationUrl") != null ? request.get("platformReconciliationUrl").toString().trim() : null);
        if (request.containsKey("invoiceNumber"))
            order.setInvoiceNumber(request.get("invoiceNumber") != null ? request.get("invoiceNumber").toString().trim() : null);
        if (request.containsKey("invoiceIssuedDate")) {
            Object value = request.get("invoiceIssuedDate");
            String text = value != null ? value.toString().trim() : "";
            order.setInvoiceIssuedDate(text.isEmpty() ? null : LocalDate.parse(text));
        }
        if (request.containsKey("invoiceUrl"))
            order.setInvoiceUrl(request.get("invoiceUrl") != null ? request.get("invoiceUrl").toString().trim() : null);
        if (request.containsKey("platformRefundStatus"))
            order.setPlatformRefundStatus(request.get("platformRefundStatus") != null ? request.get("platformRefundStatus").toString().trim() : null);
        if (request.containsKey("platformRefundUrl"))
            order.setPlatformRefundUrl(request.get("platformRefundUrl") != null ? request.get("platformRefundUrl").toString().trim() : null);
        invoiceOcrService.assertInvoiceNumberUnique(id, order.getInvoiceNumber());
        salesOrderService.ensureSettlementDocumentNumbers(order);
        salesOrderService.applySettlementAnchorDates(order, prevPrn, prevInv);
        SalesOrder saved = salesOrderService.saveOrder(order);
        salesOrderService.handleSettlementLifecycleAfterPersist(id, prevPrn, prevInv, prevRefund);
        operationLogService.log(getCurrentOperatorName(), "更新结算信息", TARGET_TYPE_SALES_ORDER, String.valueOf(id), "结算信息已更新");
        return saved;
    }

    /** 仅更新收货人/电话/地址（发货弹窗同步前回写主单用） */
    @PatchMapping("/{id}/receiver")
    public SalesOrder updateReceiver(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String receiverName = request != null ? (String) request.get("receiverName") : null;
        String receiverPhone = request != null ? (String) request.get("receiverPhone") : null;
        String receiverAddress = request != null ? (String) request.get("receiverAddress") : null;
        return salesOrderService.updateReceiver(id, receiverName, receiverPhone, receiverAddress);
    }

    /** 将主单的发货要求与物流信息同步到链式单（飞础科/上海热像科技可调用，交付方/出货方不显示同步按钮） */
    @PostMapping("/{id}/sync-shipment-to-chain")
    public ResponseEntity<Void> syncShipmentToChain(@PathVariable Long id) {
        orderShipmentService.syncShipmentToChainOrdersByOrderId(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/operation-logs")
    public List<Map<String, Object>> getOperationLogs(@PathVariable Long id) {
        String operatorName = getCurrentOperatorName();
        List<OperationLog> logs = operationLogService.getLogsForOperator("SALES_ORDER", String.valueOf(id), operatorName);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return logs.stream().map(log -> {
            Map<String, Object> m = new HashMap<>();
            m.put("operationType", log.getAction());
            m.put("description", log.getDetails() != null ? log.getDetails() : "");
            m.put("operator", log.getOperatorName() != null ? log.getOperatorName() : "");
            m.put("operationTime", log.getCreateTime() != null ? log.getCreateTime().format(fmt) : "");
            return m;
        }).collect(Collectors.toList());
    }

}
