package com.oms.service;

import com.oms.entity.PurchaseOrder;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.UserRepository;
import com.oms.service.OperationLogService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PurchaseOrderService {
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OperationLogService operationLogService;

    public Page<PurchaseOrder> searchOrders(String keyword, String status, Pageable pageable) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        return purchaseOrderRepository.findAll((Specification<PurchaseOrder>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                    cb.like(root.get("purchaseOrderNo"), pattern),
                    cb.like(root.get("omsOrderNo"), pattern),
                    cb.like(root.get("supplier"), pattern)
                ));
            }
            
            // 状态过滤
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            // 权限过滤
            if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                // 非管理员只能看到自己创建的采购订单
                predicates.add(cb.equal(root.get("createdBy"), currentUser.getId()));
            }
            
            // 下游退回的订单：关联的销售订单状态为「已退回」时，不在采购列表中显示，仅在「销售订单指派」中可见，指派完成后再生成/显示采购数据
            Subquery<String> returnedOmsOrderNos = query.subquery(String.class);
            var soRoot = returnedOmsOrderNos.from(SalesOrder.class);
            returnedOmsOrderNos.select(soRoot.get("omsOrderNo")).where(cb.equal(soRoot.get("status"), "已退回"));
            predicates.add(cb.or(
                root.get("omsOrderNo").isNull(),
                cb.not(root.get("omsOrderNo").in(returnedOmsOrderNos))
            ));
            
            // 列表按创建时间倒序（最新的在最前）
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public PurchaseOrder getOrderById(Long id) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        PurchaseOrder order = purchaseOrderRepository.findById(id).orElse(null);
        
        // 权限检查：非管理员只能查看自己创建的订单
        if (order != null && currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            if (!order.getCreatedBy().equals(currentUser.getId())) {
                throw new RuntimeException("无权访问该采购订单");
            }
        }
        
        return order;
    }

    @Transactional
    public PurchaseOrder saveOrder(PurchaseOrder order) {
        if (order == null) return null;
        
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
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
        
        return purchaseOrderRepository.save(order);
    }

    @Transactional
    public void deleteOrder(Long id) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
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
}
