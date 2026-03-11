package com.oms.service;

import com.oms.entity.Contract;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.ContractRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import com.oms.service.OperationLogService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SalesOrderService {
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private DingTalkService dingTalkService;

    public Page<SalesOrder> searchOrders(String omsOrderNo, String platformOrderNo, String status, String platformRefundStatus, String offlineSales, String needReceiptSlip, Pageable pageable) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("Current username from SecurityContext: " + currentUsername);
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        System.out.println("Current user: " + (currentUser != null ? currentUser.getRealName() + ", role: " + currentUser.getRole() : "null"));
        
        return salesOrderRepository.findAll((Specification<SalesOrder>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (omsOrderNo != null && !omsOrderNo.isEmpty()) {
                predicates.add(cb.like(root.get("omsOrderNo"), "%" + omsOrderNo + "%"));
            }
            if (platformOrderNo != null && !platformOrderNo.isEmpty()) {
                predicates.add(cb.like(root.get("platformOrderNo"), "%" + platformOrderNo + "%"));
            }
            if (status != null && !status.isEmpty()) {
                if (status.contains(",")) {
                    List<String> statusList = java.util.Arrays.asList(status.split(",\\s*"));
                    predicates.add(root.get("status").in(statusList));
                } else {
                    predicates.add(cb.equal(root.get("status"), status));
                }
            }
            if (platformRefundStatus != null && !platformRefundStatus.isEmpty()) {
                if (platformRefundStatus.contains(",")) {
                    List<String> list = java.util.Arrays.asList(platformRefundStatus.split(",\\s*"));
                    predicates.add(root.get("platformRefundStatus").in(list));
                } else {
                    predicates.add(cb.equal(root.get("platformRefundStatus"), platformRefundStatus));
                }
            }
            if (offlineSales != null && !offlineSales.isEmpty()) {
                if ("1".equals(offlineSales) || "true".equalsIgnoreCase(offlineSales) || "是".equals(offlineSales)) {
                    predicates.add(cb.and(cb.isNotNull(root.get("offlineSales")), cb.notEqual(root.get("offlineSales"), "")));
                } else {
                    predicates.add(cb.or(cb.isNull(root.get("offlineSales")), cb.equal(root.get("offlineSales"), "")));
                }
            }
            // 需签收单（待妥投）：status=已发货 且 未上传签收单（receiptUrl 为空），与 Dashboard 待妥投统计一致
            if ("1".equals(needReceiptSlip) || "true".equalsIgnoreCase(needReceiptSlip) || "receipt".equalsIgnoreCase(needReceiptSlip)) {
                predicates.add(cb.equal(root.get("status"), "已发货"));
                predicates.add(cb.or(cb.isNull(root.get("receiptUrl")), cb.equal(root.get("receiptUrl"), "")));
            }

            // 销售订单列表可见范围规划：
            // - 管理员(ROLE_ADMIN)：全量订单
            // - 仓库角色(ROLE_WAREHOUSE)或权限(warehouse)：全量订单，便于发货/出库
            // - 其他用户：仅自己创建(createdBy)或被指派(assignedUsername)的订单
            boolean canSeeAllOrders = currentUser != null
                    && ("ROLE_ADMIN".equals(currentUser.getRole())
                            || "ROLE_WAREHOUSE".equals(currentUser.getRole())
                            || hasPermission(currentUser, "warehouse"));
            if (currentUser != null && !canSeeAllOrders) {
                System.out.println("Adding permission filter: createdBy = " + currentUser.getId() + " OR assignedUsername = " + currentUsername);
                Predicate createdByPredicate = cb.equal(root.get("createdBy"), currentUser.getId());
                Predicate assignedUsernamePredicate = cb.equal(root.get("assignedUsername"), currentUsername);
                predicates.add(cb.or(createdByPredicate, assignedUsernamePredicate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /** 判断用户是否拥有某权限（permissions 逗号分隔，包含即视为拥有） */
    private boolean hasPermission(com.oms.entity.User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) return false;
        String perms = user.getPermissions();
        if (perms == null || perms.isBlank()) return false;
        for (String p : perms.split(",")) {
            if (permission.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    public List<SalesOrder> getAllOrders() {
        return salesOrderRepository.findAll();
    }

    /**
     * 生成工业电商销售订单号
     * 逻辑：(D?) + YYMMDD + 真实姓名首字母 + 01 (两位流水)
     */
    public String generateOmsOrderNo(Long userId, String orderType) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        String realName = user.getRealName();
        
        // 1. 获取日期 YYYYMMDD
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 2. 获取姓名首字母 (简单处理：取每个字符的拼音首字母，这里简化为取每个字符的前缀)
        // 实际项目中建议使用 Pinyin4j 等库，这里暂用简单逻辑
        String nameInitial = getInitials(realName);
        
        // 3. 构造前缀
        String prefix = (orderType != null && orderType.equals("第三方订单") ? "D" : "") + datePart + nameInitial;
        
        // 4. 查询当日该前缀的最大流水号
        return salesOrderRepository.findFirstByOmsOrderNoStartingWithOrderByOmsOrderNoDesc(prefix)
                .map(order -> {
                    String lastNo = order.getOmsOrderNo();
                    String seqStr = lastNo.substring(lastNo.length() - 2);
                    int seq = Integer.parseInt(seqStr) + 1;
                    return prefix + String.format("%02d", seq);
                })
                .orElse(prefix + "01");
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "XX";
        
        // 简单处理：对于中文名，返回每个字的首字母
        // 这里使用简化逻辑，对于常见中文名进行处理
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                // 对于中文字符，使用简化的首字母映射
                // 实际项目中建议使用 Pinyin4j 等库
                sb.append(getChineseInitial(c));
            } else if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.length() > 0 ? sb.toString() : "JS";
    }
    
    private char getChineseInitial(char c) {
        // 简化的中文字符首字母映射（坚领、热像科技等常用，避免订单号变成 XXX）
        // 实际项目中建议使用 Pinyin4j 等库
        String chinese = String.valueOf(c);
        if (chinese.equals("宋")) return 'S';
        if (chinese.equals("敏")) return 'M';
        if (chinese.equals("张")) return 'Z';
        if (chinese.equals("王")) return 'W';
        if (chinese.equals("李")) return 'L';
        if (chinese.equals("赵")) return 'Z';
        if (chinese.equals("钱")) return 'Q';
        if (chinese.equals("孙")) return 'S';
        if (chinese.equals("周")) return 'Z';
        if (chinese.equals("吴")) return 'W';
        if (chinese.equals("坚")) return 'J';
        if (chinese.equals("领")) return 'L';
        if (chinese.equals("热")) return 'R';
        if (chinese.equals("像")) return 'X';
        if (chinese.equals("科")) return 'K';
        if (chinese.equals("技")) return 'J';
        if (chinese.equals("上")) return 'S';
        if (chinese.equals("海")) return 'H';
        if (chinese.equals("飞")) return 'F';
        if (chinese.equals("础")) return 'C';
        return 'X';
    }

    @Transactional
    public SalesOrder saveOrder(SalesOrder order) {
        System.out.println("saveOrder called with order: " + order);
        System.out.println("Order ID: " + order.getId());
        System.out.println("Order OMS No: " + order.getOmsOrderNo());
        
        if (order == null) return null;
        // 工业电商新建订单：顶层客户名=平台名（仅本组织合同用于结算方式及期限；链式订单不继承）
        if (order.getId() == null && (order.getTopLevelCustomerName() == null || order.getTopLevelCustomerName().isBlank())
                && order.getPlatformName() != null && !order.getPlatformName().isBlank()) {
            order.setTopLevelCustomerName(order.getPlatformName());
        }
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        // 如果是更新订单，检查权限并处理字段更新
        if (order.getId() != null) {
            SalesOrder existingOrder = salesOrderRepository.findById(order.getId()).orElse(null);
            if (existingOrder != null) {
                // 检查权限：管理员、创建者或被指派用户可以编辑
                if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                    boolean isCreator = existingOrder.getCreatedBy().equals(currentUser.getId());
                    boolean isAssigned = existingOrder.getAssignedUsername() != null && 
                                        existingOrder.getAssignedUsername().equals(currentUsername);
                    
                    if (!isCreator && !isAssigned) {
                        throw new RuntimeException("无权修改该订单");
                    }
                }
                
                // 保留核心字段不被覆盖
                order.setCreatedBy(existingOrder.getCreatedBy());
                order.setOmsOrderNo(existingOrder.getOmsOrderNo());
                order.setCreateTime(existingOrder.getCreateTime());
                
                // 保留指派信息（除非显式更新）
                if (order.getAssignedUsername() == null && existingOrder.getAssignedUsername() != null) {
                    order.setAssignedUsername(existingOrder.getAssignedUsername());
                    order.setAssignTime(existingOrder.getAssignTime());
                }
                
                // 保留业务员信息（除非显式更新）
                if (order.getEcommerceSalesId() == null && existingOrder.getEcommerceSalesId() != null) {
                    order.setEcommerceSalesId(existingOrder.getEcommerceSalesId());
                    order.setEcommerceSalesName(existingOrder.getEcommerceSalesName());
                }
                
                // 保留订单详情（除非显式更新）
                if (order.getOrderDetails() == null && existingOrder.getOrderDetails() != null) {
                    order.setOrderDetails(existingOrder.getOrderDetails());
                }
                
                // 添加日志记录更新
                System.out.println("Updating order, deliveryPartyPurchasePrice: " + order.getDeliveryPartyPurchasePrice());
                System.out.println("Updating order, deductionRate: " + order.getDeductionRate());
            }
        }
        
        // 如果是新订单，设置创建用户ID
        if (order.getId() == null && currentUser != null) {
            order.setCreatedBy(currentUser.getId());
        }
        
        // 如果是新订单且没有编号，则生成编号
        if (order.getId() == null) {
            if (order.getEcommerceSalesId() != null) {
                order.setOmsOrderNo(generateOmsOrderNo(order.getEcommerceSalesId(), order.getOrderType()));
            } else {
                // 确保omsOrderNo不为空
                order.setOmsOrderNo("TEMP_" + System.currentTimeMillis());
            }
        }
        
        // 自动设置业务员姓名（仅在新建时或ecommerceSalesId改变时）
        if (order.getEcommerceSalesId() != null) {
            User user = userRepository.findById(order.getEcommerceSalesId()).orElse(null);
            if (user != null) {
                order.setEcommerceSalesName(user.getRealName());
            }
        }
        
        // 自动计算含税总价（仅在新建时或价格/数量改变时）
        if (order.getTaxIncludedPrice() != null && order.getQuantity() != null) {
            order.setTaxIncludedTotal(order.getTaxIncludedPrice().multiply(new java.math.BigDecimal(order.getQuantity())));
            // 设置amount字段，保持兼容
            order.setAmount(order.getTaxIncludedTotal());
            
            // 仅当请求未显式传交付方采购价时，才按扣点自动计算（指派/转派时前端会传 3550 等，不得覆盖）
            if (order.getDeliveryPartyPurchasePrice() == null && order.getDeductionRate() != null) {
                try {
                    java.math.BigDecimal rate = order.getDeductionRate().divide(new java.math.BigDecimal("100"));
                    java.math.BigDecimal purchasePrice = order.getTaxIncludedTotal().multiply(java.math.BigDecimal.ONE.subtract(rate));
                    order.setDeliveryPartyPurchasePrice(purchasePrice);
                } catch (Exception e) {
                    System.err.println("Error calculating delivery party purchase price: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else if (order.getId() == null) {
            // 如果是新订单且没有价格和数量，设置默认值
            order.setAmount(java.math.BigDecimal.ZERO);
            order.setTaxIncludedTotal(java.math.BigDecimal.ZERO);
        }
        
        System.out.println("Saving order to database...");
        SalesOrder savedOrder = salesOrderRepository.save(order);
        System.out.println("Order saved successfully, ID: " + savedOrder.getId());
        
        // 如果是新订单，发送钉钉通知
        if (order.getId() == null) {
            try {
                dingTalkService.sendOrderNotification(
                    savedOrder.getOmsOrderNo(),
                    savedOrder.getFinalCustomerTitle() != null ? savedOrder.getFinalCustomerTitle() : "未填写客户",
                    savedOrder.getProductName() != null ? savedOrder.getProductName() : "未填写商品",
                    savedOrder.getTaxIncludedTotal() != null ? savedOrder.getTaxIncludedTotal() : java.math.BigDecimal.ZERO
                );
            } catch (Exception e) {
                System.err.println("发送钉钉新订单通知失败: " + e.getMessage());
            }
        }
        
        return savedOrder;
    }

    @Transactional
    public void deleteOrder(Long id) {
        if (id != null) {
            // 获取当前登录用户
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            
            SalesOrder order = salesOrderRepository.findById(id).orElse(null);
            
            // 权限检查：非管理员只能删除自己创建的订单
            if (order != null && currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                if (!order.getCreatedBy().equals(currentUser.getId())) {
                    throw new RuntimeException("无权删除该订单");
                }
            }
            
            salesOrderRepository.deleteById(id);
        }
    }

    @Transactional
    public SalesOrder auditOrder(Long id, LocalDate deliveryDate) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        // 权限：管理员；或创建人（待指派时确认）；或被指派方（待确认订单时确认，如坚领确认指派过来的订单）
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId());
            boolean isAssignee = currentUsername != null && currentUsername.equals(order.getAssignedUsername());
            boolean canConfirm = isCreator || ("待确认订单".equals(order.getStatus()) && isAssignee);
            if (!canConfirm) {
                throw new RuntimeException("无权操作该订单");
            }
        }
        
        order.setStatus("待合同盖章");
        if (deliveryDate != null) {
            order.setDeliveryDate(deliveryDate);
        }
        return salesOrderRepository.save(order);
    }

    @Transactional
    public SalesOrder updateStatus(Long id, String status) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        // 权限检查：管理员、仓库(全量可见)可改状态；其他用户仅能操作自己创建或被指派的订单
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            boolean isWarehouse = "ROLE_WAREHOUSE".equals(currentUser.getRole()) || hasPermission(currentUser, "warehouse");
            if (!isWarehouse) {
                boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId());
                boolean isAssigned = order.getAssignedUsername() != null && order.getAssignedUsername().equals(currentUsername);
                if (!isCreator && !isAssigned) {
                    throw new RuntimeException("无权操作该订单");
                }
            }
        }
        
        String oldStatus = order.getStatus();
        order.setStatus(status);
        SalesOrder saved = salesOrderRepository.save(order);
        String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                ? currentUser.getRealName() : currentUsername;
        operationLogService.log(operatorName, "状态变更", "SALES_ORDER", String.valueOf(id),
                "状态由 " + (oldStatus != null ? oldStatus : "") + " 改为 " + status);
        
        // 如果状态变为"已发货"，发送物流通知
        if ("已发货".equals(status) && order.getTrackingNumber() != null && !order.getTrackingNumber().isEmpty()) {
            try {
                dingTalkService.sendLogisticsNotification(
                    saved.getOmsOrderNo(),
                    saved.getTrackingNumber(),
                    "已发货"
                );
            } catch (Exception e) {
                System.err.println("发送钉钉物流更新通知失败: " + e.getMessage());
            }
        }
        
        return saved;
    }

    @Transactional
    public SalesOrder updateContractUrl(Long id, String contractUrl) {
        System.out.println("updateContractUrl called with id: " + id);
        System.out.println("contractUrl: " + contractUrl);
        
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        // 权限检查：非管理员只能操作自己创建的订单
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            if (!order.getCreatedBy().equals(currentUser.getId())) {
                throw new RuntimeException("无权操作该订单");
            }
        }
        
        order.setContractUrl(contractUrl);
        return salesOrderRepository.save(order);
    }

    @Transactional
    public SalesOrder returnOrder(Long id, String returnReason) {
        System.out.println("returnOrder called with id: " + id);
        System.out.println("returnReason: " + returnReason);
        
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("Current username trying to return: " + currentUsername);
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        System.out.println("Order assignedUsername: " + order.getAssignedUsername());
        System.out.println("Order status: " + order.getStatus());
        
        // 权限检查：管理员、或被指派用户、或订单交付方公司下的任意用户（如热像科技-商务）可退回
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            boolean isAssignedUser = order.getAssignedUsername() != null && order.getAssignedUsername().equals(currentUsername);
            boolean isDeliveryPartyCompany = false;
            if (!isAssignedUser && currentUser.getCompanyTitle() != null && !currentUser.getCompanyTitle().isBlank()
                    && order.getDeliveryParty() != null && !order.getDeliveryParty().isBlank()) {
                String ct = currentUser.getCompanyTitle().trim();
                String dp = order.getDeliveryParty().trim();
                isDeliveryPartyCompany = ct.equals(dp) || ct.contains(dp) || dp.contains(ct);
            }
            if (!isAssignedUser && !isDeliveryPartyCompany) {
                System.out.println("Permission check failed!");
                throw new RuntimeException("只有被指派的用户或交付方公司用户才能退回该订单");
            }
            System.out.println("Permission check passed!");
        }
        
        // 设置退回信息
        order.setReturnReason(returnReason);
        order.setReturnTime(LocalDateTime.now());
        order.setReturnedBy(currentUsername);
        
        // 清空指派信息
        order.setAssignedUsername(null);
        order.setAssignTime(null);
        
        // 修改订单状态为"已退回"
        order.setStatus("已退回");
        
        // 删除所有关联的合同
        List<Contract> existingContracts = contractRepository.findAllBySalesOrderId(id);
        if (existingContracts != null && !existingContracts.isEmpty()) {
            for (Contract contract : existingContracts) {
                System.out.println("删除关联的合同，合同ID: " + contract.getId() + ", 合同编号: " + contract.getContractNo());
                contractRepository.delete(contract);
            }
        }
        
        SalesOrder saved = salesOrderRepository.save(order);
        String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                ? currentUser.getRealName() : currentUsername;
        operationLogService.log(operatorName, "订单退回", "SALES_ORDER", String.valueOf(id),
                "退回，原因：" + (returnReason != null ? returnReason : ""));
        return saved;
    }
}
