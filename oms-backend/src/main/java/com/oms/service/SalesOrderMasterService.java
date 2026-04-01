package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.entity.SalesOrderAllocation;
import com.oms.entity.SalesOrderMaster;
import com.oms.entity.SalesSerialItem;
import com.oms.entity.User;
import com.oms.repository.SalesOrderAllocationRepository;
import com.oms.repository.SalesOrderMasterRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.SalesSerialItemRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOrderMasterService {
    @Autowired
    private SalesOrderMasterRepository salesOrderMasterRepository;

    @Autowired
    private SalesOrderAllocationRepository salesOrderAllocationRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesSerialItemRepository salesSerialItemRepository;

    @Autowired
    private UserRepository userRepository;

    public Page<SalesOrderMaster> searchMasters(String masterNo, String rootOmsOrderNo, String platformOrderNo, String partyATitle,
                                                String masterStatus, Pageable pageable) {
        return salesOrderMasterRepository.findAll((Specification<SalesOrderMaster>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(masterNo)) {
                predicates.add(cb.like(root.get("masterNo"), "%" + masterNo.trim() + "%"));
            }
            if (hasText(rootOmsOrderNo)) {
                predicates.add(cb.like(root.get("rootOmsOrderNo"), "%" + rootOmsOrderNo.trim() + "%"));
            }
            if (hasText(platformOrderNo)) {
                predicates.add(cb.like(root.get("platformOrderNo"), "%" + platformOrderNo.trim() + "%"));
            }
            if (hasText(partyATitle)) {
                predicates.add(cb.like(root.get("partyATitle"), "%" + partyATitle.trim() + "%"));
            }
            if (hasText(masterStatus)) {
                predicates.add(cb.equal(root.get("masterStatus"), masterStatus.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public Map<String, Object> getMasterDetail(Long id) {
        SalesOrderMaster master = salesOrderMasterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("主单不存在"));
        List<SalesOrder> orders = salesOrderRepository.findByMasterIdOrderByIdAsc(id);
        List<SalesOrderAllocation> allocations = salesOrderAllocationRepository.findByMasterIdOrderByIdAsc(id);
        List<SalesSerialItem> serialItems = salesSerialItemRepository.findByMasterIdOrderByIdAsc(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("master", master);
        result.put("orders", orders);
        result.put("allocations", allocations);
        result.put("serialItems", serialItems);
        result.put("summary", buildMasterSummary(master, orders, allocations, serialItems));
        return result;
    }

    @Transactional
    public Map<String, Object> backfillAnchors(List<Long> orderIds, Integer limit, boolean onlyMissing) {
        List<SalesOrder> candidates;
        if (orderIds != null && !orderIds.isEmpty()) {
            candidates = salesOrderRepository.findAllById(orderIds);
        } else {
            candidates = salesOrderRepository.findAll();
        }

        int max = limit == null || limit <= 0 ? candidates.size() : limit;
        int scanned = 0;
        int updated = 0;
        List<Long> touchedOrderIds = new ArrayList<>();

        for (SalesOrder order : candidates) {
            if (order == null || order.getId() == null) {
                continue;
            }
            if (scanned >= max) {
                break;
            }
            scanned++;

            boolean missingAnchors = order.getMasterId() == null || order.getAllocationId() == null;
            if (onlyMissing && !missingAnchors) {
                continue;
            }
            User creator = order.getCreatedBy() == null ? null : userRepository.findById(order.getCreatedBy()).orElse(null);
            SalesOrder after = ensureAnchorsForOrder(order, creator);
            if (after.getMasterId() != null && after.getAllocationId() != null) {
                updated++;
                touchedOrderIds.add(after.getId());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scannedCount", scanned);
        result.put("updatedCount", updated);
        result.put("touchedOrderIds", touchedOrderIds);
        result.put("onlyMissing", onlyMissing);
        return result;
    }

    @Transactional
    public SalesOrder ensureAnchorsForOrder(SalesOrder order, User currentUser) {
        if (order == null || order.getId() == null) {
            return order;
        }

        boolean changed = false;

        SalesOrderMaster master = null;
        if (order.getMasterId() == null) {
            master = findOrCreateMasterForOrder(order, currentUser);
            order.setMasterId(master.getId());
            changed = true;
        } else {
            master = salesOrderMasterRepository.findById(order.getMasterId()).orElse(null);
        }

        if (order.getLineNo() == null) {
            order.setLineNo(resolveLineNo(order));
            changed = true;
        }
        if (order.getLineStatus() == null || order.getLineStatus().isBlank()) {
            order.setLineStatus(order.getStatus());
            changed = true;
        }
        if (order.getIsMasterPrimaryLine() == null) {
            order.setIsMasterPrimaryLine(order.getLineNo() != null && order.getLineNo() == 1);
            changed = true;
        }

        if (order.getAllocationId() == null) {
            SalesOrderAllocation allocation = createDefaultAllocation(order);
            order.setAllocationId(allocation.getId());
            changed = true;
        }

        if (changed) {
            order = salesOrderRepository.save(order);
        }

        if (master != null) {
            refreshMasterSummary(master.getId());
        }
        return order;
    }

    @Transactional
    public void refreshMasterSummary(Long masterId) {
        if (masterId == null) {
            return;
        }
        SalesOrderMaster master = salesOrderMasterRepository.findById(masterId).orElse(null);
        if (master == null) {
            return;
        }
        List<SalesOrder> orders = salesOrderRepository.findByMasterIdOrderByIdAsc(masterId);
        master.setTotalLineCount(orders.size());
        master.setTotalQuantity(orders.stream().map(SalesOrder::getQuantity).filter(Objects::nonNull).reduce(0, Integer::sum));
        if (hasText(orders.isEmpty() ? null : orders.get(0).getStatus())) {
            master.setMasterStatus(resolveMasterStatus(orders));
        }
        salesOrderMasterRepository.save(master);
    }

    private SalesOrderMaster findOrCreateMasterForOrder(SalesOrder order, User currentUser) {
        String platformOrderNo = trim(order.getPlatformOrderNo());
        String partyATitle = trim(order.getPartyATitle());
        String platformName = trim(order.getPlatformName());
        Long createdBy = order.getCreatedBy();

        SalesOrderMaster existing = null;
        if (hasText(platformOrderNo) && createdBy != null && hasText(partyATitle)) {
            existing = salesOrderMasterRepository
                    .findFirstByPlatformOrderNoAndPartyATitleAndCreatedByOrderByIdDesc(platformOrderNo, partyATitle, createdBy)
                    .orElse(null);
        }
        if (existing == null && hasText(platformOrderNo) && createdBy != null && hasText(platformName)) {
            existing = salesOrderMasterRepository
                    .findFirstByPlatformOrderNoAndPlatformNameAndCreatedByOrderByIdDesc(platformOrderNo, platformName, createdBy)
                    .orElse(null);
        }
        if (existing != null) {
            return existing;
        }

        SalesOrderMaster master = new SalesOrderMaster();
        master.setMasterNo(generateMasterNo(order.getId()));
        master.setRootOmsOrderNo(trim(order.getOmsOrderNo()));
        master.setPlatformOrderNo(platformOrderNo);
        master.setPartyATitle(hasText(partyATitle) ? partyATitle : platformName);
        master.setPlatformName(hasText(platformName) ? platformName : partyATitle);
        master.setMasterStatus(resolveInitialMasterStatus(order));
        master.setAssignStatus(hasText(order.getAssignedUsername()) ? "已指派" : "未指派");
        master.setFinanceStatus(resolveInitialFinanceStatus(order));
        master.setCreatedBy(createdBy);
        master.setCreator(resolveCreatorName(currentUser));
        return salesOrderMasterRepository.save(master);
    }

    private SalesOrderAllocation createDefaultAllocation(SalesOrder order) {
        SalesOrderAllocation allocation = new SalesOrderAllocation();
        allocation.setMasterId(order.getMasterId());
        allocation.setSalesOrderId(order.getId());
        allocation.setAllocationNo(generateAllocationNo(order.getId()));
        allocation.setAssignedUsername(trim(order.getAssignedUsername()));
        allocation.setAssignedUserId(resolveAssignedUserId(order.getAssignedUsername()));
        allocation.setAssignedCompanyTitle(resolveAssignedCompanyTitle(order));
        allocation.setAllocatedQty(order.getQuantity());
        allocation.setPlannedDeliveryDate(order.getDeliveryDate());
        allocation.setAllocationStatus(hasText(order.getAssignedUsername()) || hasText(order.getDeliveryParty()) ? "已指派" : "待指派");
        allocation.setSourceType("SALES_ORDER");
        allocation = salesOrderAllocationRepository.save(allocation);
        allocation.setRootAllocationId(allocation.getId());
        return salesOrderAllocationRepository.save(allocation);
    }

    private Integer resolveLineNo(SalesOrder order) {
        if (order.getMasterId() == null) {
            return 1;
        }
        List<SalesOrder> siblings = salesOrderRepository.findByMasterIdOrderByIdAsc(order.getMasterId());
        int max = siblings.stream()
                .filter(item -> item.getId() != null && !item.getId().equals(order.getId()))
                .map(SalesOrder::getLineNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        return max + 1;
    }

    private String resolveMasterStatus(List<SalesOrder> orders) {
        if (orders.stream().anyMatch(order -> "部分发货".equals(trim(order.getLineStatus())) || "部分发货".equals(trim(order.getStatus())))) {
            return "部分发货";
        }
        if (orders.stream().allMatch(order -> "已结算".equals(trim(order.getStatus())))) {
            return "已完成";
        }
        if (orders.stream().allMatch(order -> "已发货".equals(trim(order.getStatus())) || "已到货".equals(trim(order.getStatus()))
                || "已签收".equals(trim(order.getStatus())) || "已结算".equals(trim(order.getStatus())))) {
            return "全部发货";
        }
        return "待执行";
    }

    private String resolveInitialMasterStatus(SalesOrder order) {
        if ("已发货".equals(trim(order.getStatus())) || "已到货".equals(trim(order.getStatus())) || "已签收".equals(trim(order.getStatus()))) {
            return "全部发货";
        }
        return "待执行";
    }

    private String resolveInitialFinanceStatus(SalesOrder order) {
        if (hasText(order.getSettlementNo())) return "已结算";
        if (hasText(order.getInvoiceNumber())) return "已开票";
        if (hasText(order.getPlatformReconciliationNo())) return "已对账";
        return "未开始";
    }

    private Long resolveAssignedUserId(String assignedUsername) {
        if (!hasText(assignedUsername)) {
            return null;
        }
        return userRepository.findByUsername(assignedUsername.trim()).map(User::getId).orElse(null);
    }

    private String resolveAssignedCompanyTitle(SalesOrder order) {
        if (hasText(order.getDeliveryParty())) {
            return order.getDeliveryParty().trim();
        }
        if (hasText(order.getAssignedUsername())) {
            return userRepository.findByUsername(order.getAssignedUsername().trim())
                    .map(User::getCompanyTitle)
                    .filter(this::hasText)
                    .map(String::trim)
                    .orElse(null);
        }
        return null;
    }

    private String resolveCreatorName(User currentUser) {
        if (currentUser == null) {
            return null;
        }
        if (hasText(currentUser.getRealName())) {
            return currentUser.getRealName().trim();
        }
        return trim(currentUser.getUsername());
    }

    private String generateMasterNo(Long orderId) {
        return "SM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + (orderId == null ? "" : orderId);
    }

    private String generateAllocationNo(Long orderId) {
        return "SA" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + (orderId == null ? "" : orderId);
    }

    private Map<String, Object> buildMasterSummary(SalesOrderMaster master, List<SalesOrder> orders,
                                                   List<SalesOrderAllocation> allocations, List<SalesSerialItem> serialItems) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("masterId", master.getId());
        summary.put("masterNo", master.getMasterNo());
        summary.put("orderCount", orders == null ? 0 : orders.size());
        summary.put("allocationCount", allocations == null ? 0 : allocations.size());
        summary.put("serialCount", serialItems == null ? 0 : serialItems.size());
        summary.put("totalQuantity", orders == null ? 0 : orders.stream()
                .map(SalesOrder::getQuantity)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum));
        summary.put("assignedAllocationCount", allocations == null ? 0 : allocations.stream()
                .filter(allocation -> hasText(allocation.getAssignedUsername()) || allocation.getAssignedUserId() != null || hasText(allocation.getAssignedCompanyTitle()))
                .count());
        return summary;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
