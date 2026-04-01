package com.oms.service;

import com.oms.entity.OrderShipment;
import com.oms.entity.SalesOrder;
import com.oms.entity.SalesSerialItem;
import com.oms.entity.User;
import com.oms.repository.OrderShipmentRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.SalesSerialItemRepository;
import com.oms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderShipmentService {
    private static final String REXIANG_TITLE = "上海热像科技股份有限公司";

    @Autowired
    private OrderShipmentRepository orderShipmentRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private SalesSerialItemRepository salesSerialItemRepository;

    public Page<OrderShipment> getAllShipments(Pageable pageable) {
        return orderShipmentRepository.findAll(pageable);
    }

    public Optional<OrderShipment> getShipmentById(Long id) {
        return orderShipmentRepository.findById(id);
    }

    public Optional<OrderShipment> getShipmentBySalesOrderId(Long salesOrderId) {
        return orderShipmentRepository.findBySalesOrderId(salesOrderId);
    }

    @Transactional
    public OrderShipment saveShipment(OrderShipment shipment) {
        return saveShipmentInternal(shipment, true);
    }

    @Transactional
    public List<OrderShipment> saveBatchShipments(OrderShipment sharedShipment,
                                                  Map<Long, List<String>> serialCodesByOrderId,
                                                  List<Long> salesOrderIds) {
        if (sharedShipment == null || salesOrderIds == null || salesOrderIds.isEmpty()) {
            throw new IllegalArgumentException("批量发货信息无效：缺少销售订单");
        }
        List<SalesOrder> orders = salesOrderRepository.findAllById(salesOrderIds);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("未找到需要批量发货的销售订单");
        }
        List<OrderShipment> savedShipments = new ArrayList<>();
        List<DingTalkService.LogisticsLineSummary> lineSummaries = new ArrayList<>();
        LinkedHashSet<String> snSummary = new LinkedHashSet<>();
        LinkedHashSet<String> businessOwners = new LinkedHashSet<>();
        LinkedHashSet<String> deliveryParties = new LinkedHashSet<>();
        LinkedHashSet<String> shippingParties = new LinkedHashSet<>();

        for (SalesOrder order : orders) {
            OrderShipment shipment = buildBatchShipmentForOrder(sharedShipment, order, serialCodesByOrderId);
            OrderShipment savedShipment = saveShipmentInternal(shipment, false);
            savedShipments.add(savedShipment);

            List<String> snCodes = parseSnCodes(savedShipment.getSnCode());
            syncSerialItems(order, snCodes);
            snSummary.addAll(snCodes);
            lineSummaries.add(new DingTalkService.LogisticsLineSummary(order.getModel(), order.getQuantity()));
            if (!isBlank(order.getEcommerceSalesName())) {
                businessOwners.add(order.getEcommerceSalesName().trim());
            }
            if (!isBlank(order.getDeliveryParty())) {
                deliveryParties.add(order.getDeliveryParty().trim());
            }
            if (!isBlank(order.getShippingParty())) {
                shippingParties.add(order.getShippingParty().trim());
            }
            if (canSetOrderStatusToShipped(sharedShipment)) {
                order.setStatus("已发货");
                salesOrderRepository.save(order);
            }
        }

        SalesOrder firstOrder = orders.get(0);
        dingTalkService.sendLogisticsNotificationDetailed(
                firstOrder.getOmsOrderNo(),
                sharedShipment.getTrackingNumber(),
                "已发货",
                sharedShipment.getReturnReceiptTrackingNumber(),
                joinDistinct(new ArrayList<>(businessOwners)),
                buildDeliverySummary(new ArrayList<>(deliveryParties), new ArrayList<>(shippingParties)),
                buildRequirementSummary(sharedShipment),
                lineSummaries,
                new ArrayList<>(snSummary),
                null
        );
        return savedShipments;
    }

    private OrderShipment saveShipmentInternal(OrderShipment shipment, boolean allowNotification) {
        if (shipment == null || shipment.getSalesOrderId() == null) {
            throw new IllegalArgumentException("发货信息无效：缺少销售订单ID(salesOrderId)");
        }
        System.out.println("========== 开始保存发货信息 ==========");
        System.out.println("salesOrderId: " + shipment.getSalesOrderId());
        System.out.println("needReceiptReturn: " + shipment.getNeedReceiptReturn());
        
        // 检查是否已存在该订单的发货信息
        Optional<OrderShipment> existingShipment = orderShipmentRepository.findBySalesOrderId(shipment.getSalesOrderId());
        boolean shouldSendNotification = false;

        // 前端/JSON 常省略未改字段 → 反序列化为 null，禁止用 null 覆盖已有物流单号、需签收单等（否则列表丢失、钉钉有而列表无）
        if (existingShipment.isPresent()) {
            mergeIncomingShipmentPreserveExisting(shipment, existingShipment.get());
        }
        
        if (existingShipment.isPresent()) {
            // 如果存在，更新现有记录
            OrderShipment existing = existingShipment.get();
            shipment.setId(existing.getId());
            System.out.println("Updating existing shipment with ID: " + existing.getId());
            
            // 检查是否新增了物流单号或车牌号
            boolean hadTrackingNumber = existing.getTrackingNumber() != null && !existing.getTrackingNumber().isEmpty();
            boolean hasTrackingNumber = shipment.getTrackingNumber() != null && !shipment.getTrackingNumber().isEmpty();
            boolean hadVehiclePlate = existing.getVehiclePlate() != null && !existing.getVehiclePlate().isEmpty();
            boolean hasVehiclePlate = shipment.getVehiclePlate() != null && !shipment.getVehiclePlate().isEmpty();
            
            shouldSendNotification = (!hadTrackingNumber && hasTrackingNumber) || (!hadVehiclePlate && hasVehiclePlate);
        } else {
            // 新发货记录，检查是否有物流单号或车牌号
            boolean hasTrackingNumber = shipment.getTrackingNumber() != null && !shipment.getTrackingNumber().isEmpty();
            boolean hasVehiclePlate = shipment.getVehiclePlate() != null && !shipment.getVehiclePlate().isEmpty();
            
            shouldSendNotification = hasTrackingNumber || hasVehiclePlate;
        }
        
        OrderShipment savedShipment = orderShipmentRepository.save(shipment);
        
        Optional<SalesOrder> salesOrderOpt = salesOrderRepository.findById(shipment.getSalesOrderId());
        System.out.println("找到salesOrder: " + salesOrderOpt.isPresent());
        if (salesOrderOpt.isPresent()) {
            SalesOrder salesOrder = salesOrderOpt.get();
            salesOrder.setDeliveryNoteUrl(shipment.getDeliveryNoteUrl());
            salesOrder.setReceiptUrl(shipment.getReceiptUrl());
            salesOrder.setReceiptTime(shipment.getReceiptTime());
            salesOrder.setBoxLabelUrls(shipment.getBoxLabelUrls());
            salesOrder.setDeliveryNotePrintQuantity(shipment.getPrintQuantity());
            salesOrder.setForbiddenCouriers(shipment.getForbiddenCouriers());
            salesOrder.setPrintBoxLabel(shipment.getPrintBoxLabel());
            salesOrder.setPrintBarcode128(shipment.getPrintBarcode128());
            salesOrder.setLogisticsCompany(shipment.getLogisticsCompany());
            salesOrder.setTrackingNumber(shipment.getTrackingNumber());
            salesOrder.setReturnReceiptTrackingNumber(shipment.getReturnReceiptTrackingNumber());
            salesOrder.setReturnReceiptReceiverPhone(shipment.getReturnReceiptReceiverPhone());
            salesOrder.setVehiclePlate(shipment.getVehiclePlate());
            salesOrder.setDeliveryMethod(shipment.getDeliveryMethod());
            salesOrder.setSnCode(shipment.getSnCode());
            // 回写需签收单：仅当请求明确带了开关时才覆盖，避免 null 误清为 false
            if (shipment.getNeedReceiptReturn() != null) {
                salesOrder.setNeedReceiptSlip(Boolean.TRUE.equals(shipment.getNeedReceiptReturn()));
            }
            String targetStage = SalesOrderReceiptFlowHelper.determineReceiptFlowStage(salesOrder);
            if (targetStage != null) {
                salesOrder.setReceiptFlowStage(SalesOrderReceiptFlowHelper.advanceStage(
                        salesOrder.getReceiptFlowStage(), targetStage));
            }
            // 不自动设置状态为已发货，让前端决定
            salesOrderRepository.save(salesOrder);
            System.out.println("SalesOrder保存成功");

            String poNo = salesOrder.getPurchaseOrderNo();
            if (poNo == null || !poNo.startsWith("CHAIN_FROM:")) {
                // 主单发货后默认仅透出给上海热像科技链式单；其他交付方需手动点击“同步”后可见
                syncShipmentToRexiangChainsOnly(salesOrder.getId());
            } else {
                // 交付方/出货方在链式单上填写快递单号、车牌号等，回写到主单，飞础科/上海热像科技可见
                syncChainShipmentToMainOrder(salesOrder.getId(), salesOrder);
            }

            // 如果需要发送钉钉通知
            if (allowNotification && shouldSendNotification) {
                try {
                    dingTalkService.sendLogisticsNotificationDetailed(
                            salesOrder.getOmsOrderNo(),
                            shipment.getTrackingNumber(),
                            "已发货",
                            shipment.getReturnReceiptTrackingNumber(),
                            salesOrder.getEcommerceSalesName(),
                            buildDeliverySummary(List.of(salesOrder.getDeliveryParty()), List.of(salesOrder.getShippingParty())),
                            buildRequirementSummary(shipment),
                            List.of(new DingTalkService.LogisticsLineSummary(salesOrder.getModel(), salesOrder.getQuantity())),
                            parseSnCodes(shipment.getSnCode()),
                            null
                    );
                } catch (Exception e) {
                    System.err.println("发送钉钉物流更新通知失败: " + e.getMessage());
                }
            }
        }

        String operatorName = getCurrentOperatorName();
        String details = buildShipmentDetails(savedShipment);
        operationLogService.log(operatorName, "发货操作", "SALES_ORDER", String.valueOf(shipment.getSalesOrderId()), details);
        
        return savedShipment;
    }

    private OrderShipment buildBatchShipmentForOrder(OrderShipment sharedShipment,
                                                     SalesOrder order,
                                                     Map<Long, List<String>> serialCodesByOrderId) {
        OrderShipment existingShipment = orderShipmentRepository.findBySalesOrderId(order.getId()).orElse(null);
        OrderShipment shipment = new OrderShipment();
        if (existingShipment != null) {
            shipment.setId(existingShipment.getId());
        }
        shipment.setSalesOrderId(order.getId());
        shipment.setDeliveryNoteUrl(sharedShipment.getDeliveryNoteUrl());
        shipment.setNeedReceiptReturn(sharedShipment.getNeedReceiptReturn());
        shipment.setPrintQuantity(sharedShipment.getPrintQuantity());
        shipment.setForbiddenCouriers(sharedShipment.getForbiddenCouriers());
        shipment.setPrintBoxLabel(sharedShipment.getPrintBoxLabel());
        shipment.setPrintBarcode128(sharedShipment.getPrintBarcode128());
        shipment.setShippingAddress(sharedShipment.getShippingAddress());
        shipment.setShippingContact(sharedShipment.getShippingContact());
        shipment.setShippingPhone(sharedShipment.getShippingPhone());
        shipment.setDeliveryMethod(sharedShipment.getDeliveryMethod());
        shipment.setLogisticsCompany(sharedShipment.getLogisticsCompany());
        shipment.setTrackingNumber(sharedShipment.getTrackingNumber());
        shipment.setReturnReceiptTrackingNumber(sharedShipment.getReturnReceiptTrackingNumber());
        shipment.setReturnReceiptReceiverPhone(sharedShipment.getReturnReceiptReceiverPhone());
        shipment.setVehiclePlate(sharedShipment.getVehiclePlate());
        shipment.setLogisticsContact(sharedShipment.getLogisticsContact());
        shipment.setLogisticsPhone(sharedShipment.getLogisticsPhone());
        shipment.setReceiptUrl(existingShipment != null ? existingShipment.getReceiptUrl() : null);
        shipment.setReceiptTime(existingShipment != null ? existingShipment.getReceiptTime() : null);
        shipment.setBoxLabelUrls(sharedShipment.getBoxLabelUrls());
        shipment.setSnCode(String.join(",", serialCodesByOrderId.getOrDefault(order.getId(), List.of())));
        return shipment;
    }

    private void syncSerialItems(SalesOrder order, List<String> desiredCodes) {
        if (order == null || order.getId() == null) {
            return;
        }
        List<SalesSerialItem> existingItems = salesSerialItemRepository.findBySalesOrderIdOrderByIdAsc(order.getId());
        Map<String, SalesSerialItem> existingByCode = new LinkedHashMap<>();
        for (SalesSerialItem item : existingItems) {
            if (!isBlank(item.getSnCode())) {
                existingByCode.put(item.getSnCode().trim(), item);
            }
        }
        LinkedHashSet<String> normalizedDesired = new LinkedHashSet<>();
        for (String code : desiredCodes) {
            if (!isBlank(code)) {
                normalizedDesired.add(code.trim());
            }
        }
        for (SalesSerialItem item : existingItems) {
            String code = item.getSnCode() == null ? "" : item.getSnCode().trim();
            if (!code.isEmpty() && !normalizedDesired.contains(code)) {
                salesSerialItemRepository.delete(item);
            }
        }
        int targetQty = order.getQuantity() == null ? 0 : order.getQuantity();
        if (targetQty > 0 && normalizedDesired.size() > targetQty) {
            throw new IllegalArgumentException("SN 数量超出订单数量：" + order.getModel());
        }
        for (String code : normalizedDesired) {
            if (existingByCode.containsKey(code)) {
                continue;
            }
            Optional<SalesSerialItem> duplicate = salesSerialItemRepository.findBySnCode(code);
            if (duplicate.isPresent()) {
                throw new IllegalArgumentException("SN 已存在: " + code);
            }
            SalesSerialItem item = new SalesSerialItem();
            item.setMasterId(order.getMasterId());
            item.setSalesOrderId(order.getId());
            item.setAllocationId(order.getAllocationId());
            item.setProductModel(order.getModel());
            item.setSnCode(code);
            item.setSerialStatus(order.getAllocationId() != null ? "ASSIGNED" : "CREATED");
            if (order.getAllocationId() != null) {
                item.setBindTime(java.time.LocalDateTime.now());
            }
            salesSerialItemRepository.save(item);
        }
    }

    private List<String> parseSnCodes(String raw) {
        if (isBlank(raw)) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : raw.split("[,，;；\\n\\r]+")) {
            if (!isBlank(item)) {
                result.add(item.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private boolean canSetOrderStatusToShipped(OrderShipment shipment) {
        if (shipment == null) {
            return false;
        }
        String method = shipment.getDeliveryMethod() == null ? "" : shipment.getDeliveryMethod().trim();
        if ("商家联系物流".equals(method)) {
            return !isBlank(shipment.getTrackingNumber());
        }
        if ("自主车辆配送".equals(method)) {
            return !isBlank(shipment.getVehiclePlate());
        }
        return false;
    }

    private String buildRequirementSummary(OrderShipment shipment) {
        if (shipment == null) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(shipment.getNeedReceiptReturn())) {
            parts.add("需回签单");
        }
        if (shipment.getPrintQuantity() != null) {
            parts.add("送货单打印数量=" + shipment.getPrintQuantity());
        }
        if (!isBlank(shipment.getForbiddenCouriers())) {
            parts.add("禁用快递=" + shipment.getForbiddenCouriers().trim());
        }
        if (Boolean.TRUE.equals(shipment.getPrintBoxLabel())) {
            parts.add("打印箱唛");
        }
        if (Boolean.TRUE.equals(shipment.getPrintBarcode128())) {
            parts.add("打印128条码");
        }
        if (!isBlank(shipment.getDeliveryNoteUrl())) {
            parts.add("已上传送货单模板");
        }
        if (!isBlank(shipment.getBoxLabelUrls())) {
            parts.add("已上传箱唛附件");
        }
        return parts.isEmpty() ? "-" : String.join("；", parts);
    }

    private String buildDeliverySummary(List<String> deliveryParties, List<String> shippingParties) {
        String delivery = joinDistinct(deliveryParties);
        String shipping = joinDistinct(shippingParties);
        if ("-".equals(delivery) && "-".equals(shipping)) {
            return "-";
        }
        return "交付方=" + delivery + "；出货方=" + shipping;
    }

    private String joinDistinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String value : values) {
            if (!isBlank(value)) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? "-" : String.join(" / ", parts);
    }

    /** 更新发货单时：请求体缺省字段用库中旧值填充，避免清空物流单号、签收需求等 */
    private void mergeIncomingShipmentPreserveExisting(OrderShipment incoming, OrderShipment existing) {
        if (incoming == null || existing == null) return;
        if (isBlank(incoming.getTrackingNumber()) && !isBlank(existing.getTrackingNumber())) {
            incoming.setTrackingNumber(existing.getTrackingNumber());
        }
        if (isBlank(incoming.getReturnReceiptTrackingNumber()) && !isBlank(existing.getReturnReceiptTrackingNumber())) {
            incoming.setReturnReceiptTrackingNumber(existing.getReturnReceiptTrackingNumber());
        }
        if (isBlank(incoming.getReturnReceiptReceiverPhone()) && !isBlank(existing.getReturnReceiptReceiverPhone())) {
            incoming.setReturnReceiptReceiverPhone(existing.getReturnReceiptReceiverPhone());
        }
        if (isBlank(incoming.getLogisticsCompany()) && !isBlank(existing.getLogisticsCompany())) {
            incoming.setLogisticsCompany(existing.getLogisticsCompany());
        }
        if (isBlank(incoming.getVehiclePlate()) && !isBlank(existing.getVehiclePlate())) {
            incoming.setVehiclePlate(existing.getVehiclePlate());
        }
        if (incoming.getNeedReceiptReturn() == null && existing.getNeedReceiptReturn() != null) {
            incoming.setNeedReceiptReturn(existing.getNeedReceiptReturn());
        }
        if (isBlank(incoming.getSnCode()) && !isBlank(existing.getSnCode())) {
            incoming.setSnCode(existing.getSnCode());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional
    public void deleteShipment(Long id) {
        if (id != null) {
            orderShipmentRepository.deleteById(id);
        }
    }

    private String getCurrentOperatorName() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "system";
        return userRepository.findByUsername(username)
                .map(u -> (u.getRealName() != null && !u.getRealName().isBlank()) ? u.getRealName() : u.getUsername())
                .orElse(username);
    }

    /**
     * 将主单的发货要求与物流信息同步到其下所有链式单（交付方/出货方可见）
     * @param mainOrderId 主单 ID
     */
    @Transactional
    public void syncShipmentToChainOrders(Long mainOrderId) {
        if (mainOrderId == null) return;
        SalesOrder main = salesOrderRepository.findById(mainOrderId).orElse(null);
        if (main == null) return;
        boolean warehouseLimitedSync = isCurrentUserWarehouseScope();
        String chainMarker = "CHAIN_FROM:" + mainOrderId;
        List<SalesOrder> chains = salesOrderRepository.findByPurchaseOrderNo(chainMarker);
        if (chains == null || chains.isEmpty()) return;
        for (SalesOrder chain : chains) {
            applyMainShipmentToChain(main, chain, warehouseLimitedSync);
            salesOrderRepository.save(chain);
            upsertChainShipmentFromMain(main, chain, warehouseLimitedSync);
        }
    }

    /**
     * 自动透出仅给上海热像科技链式单；用于“未点击同步”场景。
     */
    private void syncShipmentToRexiangChainsOnly(Long mainOrderId) {
        if (mainOrderId == null) return;
        SalesOrder main = salesOrderRepository.findById(mainOrderId).orElse(null);
        if (main == null) return;
        boolean warehouseLimitedSync = isCurrentUserWarehouseScope();
        String chainMarker = "CHAIN_FROM:" + mainOrderId;
        List<SalesOrder> chains = salesOrderRepository.findByPurchaseOrderNo(chainMarker);
        if (chains == null || chains.isEmpty()) return;
        for (SalesOrder chain : chains) {
            if (!isRexiangChain(chain)) continue;
            applyMainShipmentToChain(main, chain, warehouseLimitedSync);
            salesOrderRepository.save(chain);
            upsertChainShipmentFromMain(main, chain, warehouseLimitedSync);
        }
    }

    private boolean isRexiangChain(SalesOrder chain) {
        if (chain == null || chain.getCreatedBy() == null) return false;
        User chainOwner = userRepository.findById(chain.getCreatedBy()).orElse(null);
        if (chainOwner == null || chainOwner.getCompanyTitle() == null) return false;
        return REXIANG_TITLE.equals(chainOwner.getCompanyTitle().trim());
    }

    private void applyMainShipmentToChain(SalesOrder main, SalesOrder chain, boolean warehouseLimitedSync) {
        if (warehouseLimitedSync) {
            chain.setTrackingNumber(main.getTrackingNumber());
            chain.setSnCode(main.getSnCode());
            chain.setLogisticsCompany(main.getLogisticsCompany());
            return;
        }
        chain.setDeliveryNoteUrl(main.getDeliveryNoteUrl());
        chain.setBoxLabelUrls(main.getBoxLabelUrls());
        chain.setDeliveryNotePrintQuantity(main.getDeliveryNotePrintQuantity());
        chain.setForbiddenCouriers(main.getForbiddenCouriers());
        chain.setPrintBoxLabel(main.getPrintBoxLabel());
        chain.setPrintBarcode128(main.getPrintBarcode128());
        chain.setNeedReceiptSlip(main.getNeedReceiptSlip());
        chain.setReceiverName(main.getReceiverName());
        chain.setReceiverPhone(main.getReceiverPhone());
        chain.setReceiverAddress(main.getReceiverAddress());
        chain.setTrackingNumber(main.getTrackingNumber());
        chain.setReturnReceiptTrackingNumber(main.getReturnReceiptTrackingNumber());
        chain.setReturnReceiptReceiverPhone(main.getReturnReceiptReceiverPhone());
        chain.setVehiclePlate(main.getVehiclePlate());
        chain.setDeliveryMethod(main.getDeliveryMethod());
        chain.setSnCode(main.getSnCode());
        chain.setLogisticsCompany(main.getLogisticsCompany());
        chain.setReceiptFlowStage(main.getReceiptFlowStage());
        chain.setMotherDeliveredAt(main.getMotherDeliveredAt());
        chain.setReturnDeliveredAt(main.getReturnDeliveredAt());
    }

    /**
     * 点击「同步」后，除了链式单 sales_orders 主表外，还补齐链式单自己的 order_shipments 记录。
     * 否则被指派方点「操作-发货」时拿不到已同步的签收要求、打印数量、条码/箱唛等发货要求。
     */
    private void upsertChainShipmentFromMain(SalesOrder main, SalesOrder chain, boolean warehouseLimitedSync) {
        if (main == null || chain == null || main.getId() == null || chain.getId() == null) {
            return;
        }
        OrderShipment source = orderShipmentRepository.findBySalesOrderId(main.getId()).orElse(null);
        if (source == null) {
            return;
        }
        OrderShipment target = orderShipmentRepository.findBySalesOrderId(chain.getId()).orElseGet(OrderShipment::new);
        target.setSalesOrderId(chain.getId());
        if (warehouseLimitedSync) {
            target.setTrackingNumber(source.getTrackingNumber());
            target.setSnCode(source.getSnCode());
            target.setLogisticsCompany(source.getLogisticsCompany());
            orderShipmentRepository.save(target);
            return;
        }
        target.setDeliveryNoteUrl(source.getDeliveryNoteUrl());
        target.setNeedReceiptReturn(source.getNeedReceiptReturn());
        target.setPrintQuantity(source.getPrintQuantity());
        target.setForbiddenCouriers(source.getForbiddenCouriers());
        target.setPrintBoxLabel(source.getPrintBoxLabel());
        target.setPrintBarcode128(source.getPrintBarcode128());
        target.setShippingAddress(source.getShippingAddress());
        target.setShippingContact(source.getShippingContact());
        target.setShippingPhone(source.getShippingPhone());
        target.setDeliveryMethod(source.getDeliveryMethod());
        target.setLogisticsCompany(source.getLogisticsCompany());
        target.setTrackingNumber(source.getTrackingNumber());
        target.setReturnReceiptTrackingNumber(source.getReturnReceiptTrackingNumber());
        target.setReturnReceiptReceiverPhone(source.getReturnReceiptReceiverPhone());
        target.setVehiclePlate(source.getVehiclePlate());
        target.setLogisticsContact(source.getLogisticsContact());
        target.setLogisticsPhone(source.getLogisticsPhone());
        target.setReceiptUrl(source.getReceiptUrl());
        target.setReceiptTime(source.getReceiptTime());
        target.setBoxLabelUrls(source.getBoxLabelUrls());
        target.setSnCode(source.getSnCode());
        orderShipmentRepository.save(target);
    }

    private boolean isCurrentUserWarehouseScope() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            return false;
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }
        String role = user.getRole() != null ? user.getRole().trim() : "";
        if ("ROLE_WAREHOUSE".equalsIgnoreCase(role) || "仓库".equals(role)) {
            return true;
        }
        String permissions = user.getPermissions() != null ? user.getPermissions() : "";
        for (String item : permissions.split(",")) {
            if ("warehouse".equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 交付方/出货方在链式单上保存发货信息后，将快递单号、车牌号等回写主单（飞础科/上海热像科技可见）
     */
    private void syncChainShipmentToMainOrder(Long chainOrderId, SalesOrder chainOrder) {
        String poNo = chainOrder.getPurchaseOrderNo();
        if (poNo == null || !poNo.startsWith("CHAIN_FROM:")) return;
        Long mainId;
        try {
            mainId = Long.parseLong(poNo.substring("CHAIN_FROM:".length()).trim());
        } catch (NumberFormatException e) {
            return;
        }
        SalesOrder main = salesOrderRepository.findById(mainId).orElse(null);
        if (main == null) return;
        main.setTrackingNumber(chainOrder.getTrackingNumber());
        main.setReturnReceiptTrackingNumber(chainOrder.getReturnReceiptTrackingNumber());
        main.setReturnReceiptReceiverPhone(chainOrder.getReturnReceiptReceiverPhone());
        main.setVehiclePlate(chainOrder.getVehiclePlate());
        main.setDeliveryMethod(chainOrder.getDeliveryMethod());
        main.setLogisticsCompany(chainOrder.getLogisticsCompany());
        main.setSnCode(chainOrder.getSnCode());
        if (chainOrder.getNeedReceiptSlip() != null) {
            main.setNeedReceiptSlip(chainOrder.getNeedReceiptSlip());
        }
        main.setReceiptUrl(chainOrder.getReceiptUrl());
        main.setReceiptTime(chainOrder.getReceiptTime());
        if (chainOrder.getReceiptStatus() != null && !chainOrder.getReceiptStatus().isBlank()) {
            main.setReceiptStatus(chainOrder.getReceiptStatus());
        }
        String targetStage = SalesOrderReceiptFlowHelper.determineReceiptFlowStage(main);
        if (targetStage != null) {
            main.setReceiptFlowStage(SalesOrderReceiptFlowHelper.advanceStage(main.getReceiptFlowStage(), targetStage));
        }
        salesOrderRepository.save(main);
    }

    /** 按当前订单 ID 将其物流信息同步到直接下游链式单；主单传主单 ID，链式单传链式单 ID */
    @Transactional
    public void syncShipmentToChainOrdersByOrderId(Long orderId) {
        if (orderId == null) return;
        SalesOrder order = salesOrderRepository.findById(orderId).orElse(null);
        if (order == null) return;
        syncShipmentToChainOrders(order.getId());
    }

    private String buildShipmentDetails(OrderShipment s) {
        StringBuilder sb = new StringBuilder("发货");
        if (s.getLogisticsCompany() != null && !s.getLogisticsCompany().isBlank()) {
            sb.append("，物流：").append(s.getLogisticsCompany());
        }
        if (s.getTrackingNumber() != null && !s.getTrackingNumber().isBlank()) {
            sb.append("，单号：").append(s.getTrackingNumber());
        }
        if (s.getSnCode() != null && !s.getSnCode().isBlank()) {
            sb.append("，SN：").append(s.getSnCode());
        }
        return sb.toString();
    }
}
