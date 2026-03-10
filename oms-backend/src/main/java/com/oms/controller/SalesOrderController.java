package com.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.OperationLog;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.OperationLogService;
import com.oms.service.SalesOrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String TARGET_TYPE_SALES_ORDER = "SALES_ORDER";

    private String getCurrentOperatorName() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "SYSTEM";
        return userRepository.findByUsername(username).map(u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : username).orElse(username);
    }

    @GetMapping
    public Page<SalesOrder> list(
            @RequestParam(required = false) String omsOrderNo,
            @RequestParam(required = false) String platformOrderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platformRefundStatus,
            @RequestParam(required = false) String offlineSales,
            @RequestParam(required = false) String needReceiptSlip,
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
        Page<SalesOrder> pageResult = salesOrderService.searchOrders(omsOrderNo, platformOrderNo, status, platformRefundStatus, offlineSales, needReceiptSlip, PageRequest.of(page, size, pageSort));
        
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean salesOnly = "sales".equalsIgnoreCase(view);
        
        // 对被指派用户看到的订单进行数据转换（仅当非 view=sales 时做指派方视角转换）
        return pageResult.map(ord -> {
            if (salesOnly) {
                SalesOrder copy = new SalesOrder();
                BeanUtils.copyProperties(ord, copy);
                copy.setDeductionRate(null);
                // 仅当当前用户是该订单的「被指派方」时，才用交付方采购价覆盖展示含税单价/总价、用甲方抬头覆盖发票信息；创建人(如 sonmin) 看自己的震坤行等订单必须看到原始金额，不得被联动改掉
                boolean isAssigneeOfThisOrder = ord.getAssignedUsername() != null && ord.getAssignedUsername().equals(currentUsername);
                if (isAssigneeOfThisOrder) {
                    if (ord.getDeliveryPartyPurchasePrice() != null) {
                        copy.setTaxIncludedPrice(ord.getDeliveryPartyPurchasePrice());
                        if (ord.getQuantity() != null) {
                            copy.setTaxIncludedTotal(
                                ord.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(ord.getQuantity()))
                            );
                        }
                    }
                    String partyATitle = (ord.getPlatformName() != null && !ord.getPlatformName().isBlank())
                            ? ord.getPlatformName().trim()
                            : (ord.getOperationEntityTitle() != null && !ord.getOperationEntityTitle().isBlank()
                                    ? ord.getOperationEntityTitle().trim() : null);
                    if (partyATitle != null) {
                        copy.setInvoiceTitle(partyATitle);
                    }
                }
                return copy;
            }
            // 被指派方（非创建人）看列表时：价格用合同价，甲方/发票展示订单上的甲方（不硬编码）
            if (ord.getAssignedUsername() != null && 
                ord.getAssignedUsername().equals(currentUsername) &&
                (ord.getCreatedBy() == null || !isUserCreatedOrder(ord.getCreatedBy(), currentUsername))) {
                SalesOrder transformedOrder = new SalesOrder();
                BeanUtils.copyProperties(ord, transformedOrder);
                if (ord.getDeliveryPartyPurchasePrice() != null) {
                    transformedOrder.setTaxIncludedPrice(ord.getDeliveryPartyPurchasePrice());
                    if (ord.getQuantity() != null) {
                        transformedOrder.setTaxIncludedTotal(
                            ord.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(ord.getQuantity()))
                        );
                    }
                }
                String partyATitle = (ord.getPlatformName() != null && !ord.getPlatformName().isBlank())
                        ? ord.getPlatformName().trim()
                        : (ord.getOperationEntityTitle() != null && !ord.getOperationEntityTitle().isBlank()
                                ? ord.getOperationEntityTitle().trim() : null);
                if (partyATitle != null) {
                    transformedOrder.setInvoiceTitle(partyATitle);
                }
                return transformedOrder;
            }
            return ord;
        });
    }
    
    // 辅助方法：检查用户是否是订单创建者
    private boolean isUserCreatedOrder(Long createdById, String username) {
        if (createdById == null) return false;
        User user = userRepository.findById(createdById).orElse(null);
        return user != null && user.getUsername().equals(username);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> orderData) {
        System.out.println("POST /api/sales-orders called with orderData: " + orderData);
        try {
            Map<String, Object> processedData = new HashMap<>(orderData);
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
            
            Map<String, Object> result = objectMapper.convertValue(savedOrder, Map.class);
            result.put("products", orderData.get("products"));
            result.put("logistics", orderData.get("logistics"));
            result.put("invoices", orderData.get("invoices"));
            result.put("reconciliations", orderData.get("reconciliations"));
            
            return result;
        } catch (Exception e) {
            System.err.println("Error saving order: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("保存订单失败", e);
        }
    }

    /** 仅热像科技-财务或工业电商业务员（permissions 含 platform_refund）或管理员可更新平台回款状态 */
    private boolean canUpdatePlatformRefund() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return false;
        return userRepository.findByUsername(name).map(u -> {
            if ("ROLE_ADMIN".equals(u.getRole())) return true;
            String p = u.getPermissions();
            return p != null && java.util.Arrays.stream(p.split(",")).map(String::trim).anyMatch("platform_refund"::equalsIgnoreCase);
        }).orElse(false);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> orderData) {
        System.out.println("PUT /api/sales-orders/" + id + " called with orderData: " + orderData);
        System.out.println("deliveryPartyPurchasePrice in request: " + orderData.get("deliveryPartyPurchasePrice"));
        try {
            SalesOrder existingOrder = salesOrderService.getAllOrders().stream()
                    .filter(o -> o.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("订单不存在"));
            
            Map<String, Object> processedData = new HashMap<>(orderData);
            if (processedData.containsKey("platformRefundStatus") && !canUpdatePlatformRefund()) {
                processedData.remove("platformRefundStatus");
            }
            processDeductionRate(processedData);
            // 指派/转派时前端显式传的交付方采购价（如 3550）必须保留，避免被后续回写或 saveOrder 内扣点算法覆盖
            Object explicitPurchasePrice = orderData.get("deliveryPartyPurchasePrice");
            // 编辑时商品/物流在子表里，列表展示的是订单主表字段，需用第一个商品和第一条物流回填主表，列表才能显示最新
            syncOrderFromFirstProduct(processedData);
            syncOrderFromFirstLogistics(processedData);
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
            details.put("products", orderData.get("products"));
            details.put("logistics", orderData.get("logistics"));
            details.put("invoices", orderData.get("invoices"));
            details.put("reconciliations", orderData.get("reconciliations"));
            
            savedOrder.setOrderDetails(objectMapper.writeValueAsString(details));
            savedOrder = salesOrderService.saveOrder(savedOrder);
            
            String operatorName = getCurrentOperatorName();
            operationLogService.log(operatorName, "编辑销售订单", "SALES_ORDER", String.valueOf(id), "编辑订单");
            
            Map<String, Object> result = objectMapper.convertValue(savedOrder, Map.class);
            result.put("products", orderData.get("products"));
            result.put("logistics", orderData.get("logistics"));
            result.put("invoices", orderData.get("invoices"));
            result.put("reconciliations", orderData.get("reconciliations"));
            
            return result;
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
    
    /** 用请求体中的 products[0] 回填订单主表字段（platformSku 等），保证列表展示与编辑一致 */
    @SuppressWarnings("unchecked")
    private void syncOrderFromFirstProduct(Map<String, Object> data) {
        Object productsObj = data.get("products");
        if (!(productsObj instanceof List) || ((List<?>) productsObj).isEmpty()) return;
        Object first = ((List<?>) productsObj).get(0);
        if (!(first instanceof Map)) return;
        Map<String, Object> firstProduct = (Map<String, Object>) first;
        String[] keys = {"platformSku", "model", "materialNo", "productConfig", "warrantyPeriod", "quantity", "taxIncludedPrice", "taxIncludedTotal", "orderType"};
        for (String key : keys) {
            if (firstProduct.containsKey(key) && firstProduct.get(key) != null) {
                data.put(key, firstProduct.get(key));
            }
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
    public String generateNo(@RequestParam Long userId, @RequestParam String orderType) {
        return salesOrderService.generateOmsOrderNo(userId, orderType);
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
        SalesOrder updated = salesOrderService.returnOrder(id, returnReason);
        operationLogService.log(getCurrentOperatorName(), "订单退回", TARGET_TYPE_SALES_ORDER, String.valueOf(id),
                "退回，原因：" + (returnReason != null ? returnReason : ""));
        return updated;
    }

    @GetMapping("/{id}/operation-logs")
    public List<Map<String, Object>> getOperationLogs(@PathVariable Long id) {
        List<OperationLog> logs = operationLogService.getLogs("SALES_ORDER", String.valueOf(id));
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
