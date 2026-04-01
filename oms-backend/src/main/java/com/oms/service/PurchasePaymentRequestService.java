package com.oms.service;

import com.oms.entity.PurchaseInboundInvoice;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.PurchasePaymentRequest;
import com.oms.entity.User;
import com.oms.repository.PurchaseInboundInvoiceRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.PurchasePaymentRequestRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PurchasePaymentRequestService {
    @Autowired
    private PurchasePaymentRequestRepository paymentRequestRepository;
    @Autowired
    private PurchaseInboundInvoiceRepository inboundInvoiceRepository;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OperationLogService operationLogService;

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) return null;
        return userRepository.findByUsername(username).orElse(null);
    }

    private String currentOperatorName() {
        User u = currentUser();
        if (u == null) return "system";
        if (u.getRealName() != null && !u.getRealName().isBlank()) return u.getRealName();
        return u.getUsername();
    }

    private boolean hasPerm(User user, String perm) {
        if (user == null || perm == null || perm.isBlank()) return false;
        if ("ROLE_ADMIN".equals(user.getRole())) return true;
        String p = user.getPermissions();
        if (p == null || p.isBlank()) return false;
        return java.util.Arrays.stream(p.split(",")).map(String::trim).anyMatch(perm::equalsIgnoreCase);
    }

    private void ensureViewPermission(PurchasePaymentRequest request) {
        User user = currentUser();
        if (user == null) throw new RuntimeException("请先登录");
        if ("ROLE_ADMIN".equals(user.getRole())) return;
        if (hasPerm(user, "purchase_payment_view_all")) return;
        if (request.getCreatedBy() == null || !request.getCreatedBy().equals(user.getId())) {
            throw new RuntimeException("无权查看该付款申请");
        }
    }

    private boolean canTransition(String from, String to) {
        String curr = from == null ? "" : from.trim();
        String next = to == null ? "" : to.trim();
        if (next.isEmpty()) return false;
        if (curr.isEmpty()) return true;
        if (curr.equals(next)) return true;
        return switch (curr) {
            case "PENDING_REVIEW" -> java.util.Set.of("INVOICE_TRACKING", "READY_TO_PAY", "REJECTED", "CLOSED").contains(next);
            case "INVOICE_TRACKING" -> java.util.Set.of("READY_TO_PAY", "REJECTED", "CLOSED").contains(next);
            case "READY_TO_PAY" -> java.util.Set.of("PAID", "REJECTED", "CLOSED").contains(next);
            case "REJECTED" -> java.util.Set.of("PENDING_REVIEW", "CLOSED").contains(next);
            case "PAID" -> java.util.Set.of("CLOSED").contains(next);
            case "CLOSED" -> false;
            default -> false;
        };
    }

    public Page<PurchasePaymentRequest> search(String keyword, String status, Pageable pageable) {
        User user = currentUser();
        return paymentRequestRepository.findAll((Specification<PurchasePaymentRequest>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("requestNo"), like),
                        cb.like(root.get("purchaseOrderNo"), like),
                        cb.like(root.get("omsOrderNo"), like),
                        cb.like(root.get("payeeCompany"), like)
                ));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (user != null && !"ROLE_ADMIN".equals(user.getRole()) && !hasPerm(user, "purchase_payment_view_all")) {
                predicates.add(cb.equal(root.get("createdBy"), user.getId()));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public List<PurchasePaymentRequest> listByPurchaseOrderId(Long purchaseOrderId) {
        return paymentRequestRepository.findByPurchaseOrderIdOrderByIdDesc(purchaseOrderId);
    }

    public PurchasePaymentRequest getById(Long id) {
        PurchasePaymentRequest req = paymentRequestRepository.findById(id).orElse(null);
        if (req == null) return null;
        ensureViewPermission(req);
        return req;
    }

    @Transactional
    public PurchasePaymentRequest create(Map<String, Object> body) {
        User user = currentUser();
        if (user == null) throw new RuntimeException("请先登录");
        if (!hasPerm(user, "purchase_payment_apply")) {
            throw new RuntimeException("无权限发起付款申请");
        }
        Long purchaseOrderId = body != null && body.get("purchaseOrderId") != null
                ? Long.valueOf(String.valueOf(body.get("purchaseOrderId")))
                : null;
        if (purchaseOrderId == null) throw new RuntimeException("purchaseOrderId 不能为空");
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new RuntimeException("采购订单不存在"));
        // 按业务口径：被指派方/转派方采购单付款不受平台回款限制，采购侧可独立发起付款申请。

        PurchasePaymentRequest req = new PurchasePaymentRequest();
        req.setRequestNo("PR" + System.currentTimeMillis());
        req.setPurchaseOrderId(po.getId());
        req.setPurchaseOrderNo(po.getPurchaseOrderNo());
        req.setOmsOrderNo(po.getOmsOrderNo());
        req.setPayeeCompany(po.getSupplier());
        req.setRequestedAmount(po.getTaxIncludedPurchaseTotal() != null ? po.getTaxIncludedPurchaseTotal() : BigDecimal.ZERO);
        req.setPaidAmount(BigDecimal.ZERO);
        req.setStatus("PENDING_REVIEW");
        req.setApplyRemark(body != null ? String.valueOf(body.getOrDefault("applyRemark", "")) : "");
        req.setCreatedBy(user.getId());
        req.setCreatedByName(currentOperatorName());
        PurchasePaymentRequest saved = paymentRequestRepository.save(req);
        operationLogService.log(currentOperatorName(), "发起付款申请", "PURCHASE_PAYMENT_REQUEST",
                String.valueOf(saved.getId()), "采购单:" + (saved.getPurchaseOrderNo() != null ? saved.getPurchaseOrderNo() : "-"));
        return saved;
    }

    @Transactional
    public PurchasePaymentRequest updateStatus(Long id, String status, String remark) {
        PurchasePaymentRequest req = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("付款申请不存在"));
        ensureViewPermission(req);
        User user = currentUser();
        if (user == null) throw new RuntimeException("请先登录");
        if (status == null || status.isBlank()) throw new RuntimeException("状态不能为空");
        if ("PAID".equals(status)) {
            if (!hasPerm(user, "purchase_payment_finance")) {
                throw new RuntimeException("无权限将状态更新为已打款");
            }
        } else if (!hasPerm(user, "purchase_payment_finance") && !hasPerm(user, "purchase_invoice_track")) {
            throw new RuntimeException("无权限更新付款申请状态");
        }
        if (!canTransition(req.getStatus(), status)) {
            throw new RuntimeException("非法状态流转：" + req.getStatus() + " -> " + status);
        }
        req.setStatus(status);
        if (remark != null) req.setFinanceRemark(remark);
        if ("PAID".equals(status)) {
            req.setPaidTime(LocalDateTime.now());
            if (req.getPaidAmount() == null || req.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
                req.setPaidAmount(req.getRequestedAmount());
            }
            PurchaseOrder po = purchaseOrderRepository.findById(req.getPurchaseOrderId()).orElse(null);
            if (po != null) {
                po.setPaymentStatus("已付款");
                purchaseOrderRepository.save(po);
            }
        }
        PurchasePaymentRequest saved = paymentRequestRepository.save(req);
        operationLogService.log(currentOperatorName(), "更新付款申请状态", "PURCHASE_PAYMENT_REQUEST",
                String.valueOf(saved.getId()), "状态更新为:" + status);
        return saved;
    }

    public List<PurchaseInboundInvoice> listInboundInvoices(Long paymentRequestId) {
        PurchasePaymentRequest req = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new RuntimeException("付款申请不存在"));
        ensureViewPermission(req);
        return inboundInvoiceRepository.findByPaymentRequestIdOrderByIdDesc(paymentRequestId);
    }

    @Transactional
    public PurchaseInboundInvoice addInboundInvoice(Long paymentRequestId, PurchaseInboundInvoice invoice) {
        PurchasePaymentRequest req = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new RuntimeException("付款申请不存在"));
        ensureViewPermission(req);
        if ("PAID".equals(req.getStatus()) || "CLOSED".equals(req.getStatus())) {
            throw new RuntimeException("当前状态不允许维护进项发票");
        }
        User user = currentUser();
        if (user == null) throw new RuntimeException("请先登录");
        if (!hasPerm(user, "purchase_invoice_track") && !hasPerm(user, "purchase_payment_finance")) {
            throw new RuntimeException("无权限维护进项发票");
        }
        invoice.setId(null);
        invoice.setPaymentRequestId(paymentRequestId);
        PurchaseInboundInvoice saved = inboundInvoiceRepository.save(invoice);
        if ("已收票".equals(saved.getStatus())) {
            req.setStatus("READY_TO_PAY");
            paymentRequestRepository.save(req);
        } else {
            req.setStatus("INVOICE_TRACKING");
            paymentRequestRepository.save(req);
        }
        operationLogService.log(currentOperatorName(), "新增进项发票跟踪", "PURCHASE_PAYMENT_REQUEST",
                String.valueOf(paymentRequestId), "进项发票状态:" + (saved.getStatus() != null ? saved.getStatus() : "待开票"));
        return saved;
    }

    @Transactional
    public PurchasePaymentRequest uploadVoucher(Long id, String voucherUrl, String bankFlowNo, BigDecimal paidAmount) {
        PurchasePaymentRequest req = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("付款申请不存在"));
        ensureViewPermission(req);
        if (!"READY_TO_PAY".equals(req.getStatus()) && !"INVOICE_TRACKING".equals(req.getStatus())) {
            throw new RuntimeException("当前状态不允许打款，请先推进到 READY_TO_PAY");
        }
        User user = currentUser();
        if (user == null) throw new RuntimeException("请先登录");
        if (!hasPerm(user, "purchase_payment_finance")) {
            throw new RuntimeException("无权限上传打款凭证");
        }
        req.setVoucherUrl(voucherUrl);
        req.setBankFlowNo(bankFlowNo);
        req.setPaidAmount(paidAmount != null ? paidAmount : req.getRequestedAmount());
        req.setStatus("PAID");
        req.setPaidTime(LocalDateTime.now());
        PurchasePaymentRequest saved = paymentRequestRepository.save(req);

        PurchaseOrder po = purchaseOrderRepository.findById(req.getPurchaseOrderId()).orElse(null);
        if (po != null) {
            po.setPaymentStatus(saved.getPaidAmount() != null && saved.getRequestedAmount() != null
                    && saved.getPaidAmount().compareTo(saved.getRequestedAmount()) < 0 ? "部分付款" : "已付款");
            purchaseOrderRepository.save(po);
        }
        operationLogService.log(currentOperatorName(), "上传打款凭证", "PURCHASE_PAYMENT_REQUEST",
                String.valueOf(saved.getId()), "已上传凭证并回写付款状态");
        return saved;
    }
}

