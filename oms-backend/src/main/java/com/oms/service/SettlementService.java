package com.oms.service;

import com.oms.entity.Settlement;
import com.oms.repository.SettlementRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SettlementService {
    @Autowired
    private SettlementRepository settlementRepository;

    public Page<Settlement> searchSettlements(String billNo, String status, String projectName, @org.springframework.lang.NonNull Pageable pageable) {
        return settlementRepository.findAll((Specification<Settlement>) (root, query, cb) -> {
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

    @Autowired
    private com.oms.repository.InvoiceRepository invoiceRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Transactional
    public Settlement createSettlementFromInvoice(String invoiceBillNo) {
        com.oms.entity.Invoice invoice = invoiceRepository.findByBillNo(invoiceBillNo).orElse(null);
        if (invoice == null) {
            throw new RuntimeException("未找到对账单: " + invoiceBillNo);
        }

        if (!"INVOICED".equals(invoice.getStatus())) {
            throw new RuntimeException("对账单状态不是'已开票'，无法生成结算单");
        }

        Settlement settlement = new Settlement();
        settlement.setBillNo("SET" + System.currentTimeMillis());
        settlement.setAmount(invoice.getAmount());
        settlement.setProjectName(invoice.getProjectName());
        settlement.setStatus("DRAFT");
        
        Settlement savedSettlement = settlementRepository.save(settlement);

        // 更新对账单状态
        invoice.setStatus("SETTLED");
        invoice.setReceiptTime(LocalDateTime.now());
        invoiceRepository.save(invoice);

        // 记录日志
        operationLogService.log("SYSTEM", "从对账单生成结算单", "SETTLEMENT", savedSettlement.getBillNo(), 
            "关联对账单: " + invoiceBillNo);
        operationLogService.log("SYSTEM", "生成结算单并完结", "INVOICE", invoiceBillNo, 
            "生成结算单: " + savedSettlement.getBillNo());

        return savedSettlement;
    }

    public List<Settlement> getAllSettlements() {
        return settlementRepository.findAll();
    }

    public Settlement getSettlementByBillNo(String billNo) {
        return settlementRepository.findByBillNo(billNo).orElse(null);
    }

    @Transactional
    public Settlement createSettlement(Settlement settlement) {
        if (settlement == null) return null;
        return settlementRepository.save(settlement);
    }

    @Transactional
    public Settlement apply(String billNo) {
        return settlementRepository.findByBillNo(billNo).map(settlement -> {
            if ("DRAFT".equals(settlement.getStatus())) {
                settlement.setStatus("PENDING");
                settlement.setApplyTime(LocalDateTime.now());
                return settlementRepository.save(settlement);
            }
            return settlement;
        }).orElse(null);
    }

    @Transactional
    public Settlement settle(String billNo) {
        return settlementRepository.findByBillNo(billNo).map(settlement -> {
            if ("PENDING".equals(settlement.getStatus())) {
                settlement.setStatus("SETTLED");
                settlement.setSettleTime(LocalDateTime.now());
                return settlementRepository.save(settlement);
            }
            return settlement;
        }).orElse(null);
    }
}
