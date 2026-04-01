package com.oms.service;

import com.oms.entity.Invoice;
import com.oms.repository.InvoiceRepository;
import com.oms.repository.SalesOrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvoiceService {
    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private OperationLogService operationLogService;

    public Page<Invoice> searchInvoices(String billNo, String status, String projectName, @org.springframework.lang.NonNull Pageable pageable) {
        return invoiceRepository.findAll((Specification<Invoice>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (billNo != null && !billNo.isEmpty()) {
                predicates.add(cb.like(root.get("billNo"), "%" + billNo + "%"));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (projectName != null && !projectName.isEmpty()) {
                predicates.add(cb.like(root.get("projectName"), "%" + projectName + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    @Transactional
    public Invoice createInvoiceFromOrders(List<String> orderNos, String projectName) {
        if (orderNos == null || orderNos.isEmpty()) {
            throw new RuntimeException("订单编号列表不能为空");
        }

        List<Long> ids = orderNos.stream()
                .map(no -> salesOrderRepository.findByOmsOrderNo(no))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(com.oms.entity.SalesOrder::getId)
                .toList();

        if (ids.isEmpty()) {
            throw new RuntimeException("未找到选中的订单");
        }

        List<com.oms.entity.SalesOrder> orders = salesOrderRepository.findAllById(ids);
        com.oms.entity.SalesOrder firstOrder = orders.get(0);

        Invoice invoice = new Invoice();
        invoice.setBillNo("INV" + System.currentTimeMillis());
        invoice.setProjectName(projectName != null ? projectName : "");
        invoice.setStatus("DRAFT");

        BigDecimal totalAmount = orders.stream()
            .map(com.oms.entity.SalesOrder::getTaxIncludedTotal)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setAmount(totalAmount);
        invoice.setPreTaxAmount(totalAmount);
        invoice.setTaxAmount(BigDecimal.ZERO);

        String buyer = firstOrder.getTopLevelCustomerName() != null && !firstOrder.getTopLevelCustomerName().isBlank()
            ? firstOrder.getTopLevelCustomerName()
            : (firstOrder.getPlatformName() != null && !firstOrder.getPlatformName().isBlank() ? firstOrder.getPlatformName() : "购方待填");
        String seller = firstOrder.getOperationEntityTitle() != null && !firstOrder.getOperationEntityTitle().isBlank()
            ? firstOrder.getOperationEntityTitle()
            : (firstOrder.getPlatformName() != null && !firstOrder.getPlatformName().isBlank() ? firstOrder.getPlatformName() : "销方待填");
        invoice.setBuyerName(buyer);
        invoice.setSellerName(seller);

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 更新订单状态为“已对账”（或类似状态，防止重复生成）
        orders.forEach(order -> {
            order.setStatus("已对账");
            salesOrderRepository.save(order);
        });

        // 记录日志
        operationLogService.log("SYSTEM", "从订单生成对账单", "INVOICE", savedInvoice.getBillNo(), 
            "关联订单: " + String.join(", ", orderNos));

        return savedInvoice;
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Invoice getInvoiceByBillNo(String billNo) {
        if (billNo == null) return null;
        return invoiceRepository.findByBillNo(billNo).orElse(null);
    }

    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        if (invoice == null) return null;
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice updateStatus(String billNo, String status) {
        if (billNo == null) return null;
        return invoiceRepository.findByBillNo(billNo).map(invoice -> {
            invoice.setStatus(status);
            return invoiceRepository.save(invoice);
        }).orElse(null);
    }

    @Transactional
    public Invoice saveReceipt(String billNo, String fileUrl) {
        if (billNo == null) return null;
        return invoiceRepository.findByBillNo(billNo).map(invoice -> {
            invoice.setReceiptFileUrl(fileUrl);
            invoice.setStatus("INVOICED");
            return invoiceRepository.save(invoice);
        }).orElse(null);
    }
}
