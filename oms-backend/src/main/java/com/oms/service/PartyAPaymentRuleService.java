package com.oms.service;

import com.oms.entity.PartyAPaymentRule;
import com.oms.entity.User;
import com.oms.repository.PartyAPaymentRuleRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
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
public class PartyAPaymentRuleService {
    @Autowired
    private PartyAPaymentRuleRepository partyAPaymentRuleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpectedRefundDateService expectedRefundDateService;

    public Page<PartyAPaymentRule> getRules(String partyATitle, Boolean enabled, Pageable pageable) {
        Specification<PartyAPaymentRule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (partyATitle != null && !partyATitle.trim().isEmpty()) {
                predicates.add(cb.like(root.get("partyATitle"), "%" + partyATitle.trim() + "%"));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return partyAPaymentRuleRepository.findAll(spec, pageable);
    }

    public PartyAPaymentRule getRuleById(Long id) {
        return partyAPaymentRuleRepository.findById(id).orElse(null);
    }

    @Transactional
    public PartyAPaymentRule createRule(PartyAPaymentRule rule) {
        normalizeAndValidate(rule, null);
        User currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getId() != null) {
            rule.setCreatedBy(currentUser.getId());
        }
        PartyAPaymentRule saved = partyAPaymentRuleRepository.save(rule);
        if (Boolean.TRUE.equals(saved.getEnabled())) {
            expectedRefundDateService.recalculateOrdersForTitle(saved.getPartyATitle());
        }
        return saved;
    }

    @Transactional
    public PartyAPaymentRule updateRule(Long id, PartyAPaymentRule rule) {
        PartyAPaymentRule existing = partyAPaymentRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("甲方回款规则不存在"));
        normalizeAndValidate(rule, id);
        existing.setPartyATitle(rule.getPartyATitle().trim());
        existing.setEnabled(rule.getEnabled() == null ? Boolean.TRUE : rule.getEnabled());
        existing.setBaseEventType(rule.getBaseEventType());
        existing.setBaseDayOfMonth(rule.getBaseDayOfMonth());
        existing.setCycleCutoffDay(rule.getCycleCutoffDay());
        existing.setCarryOverToNextCycle(rule.getCarryOverToNextCycle() == null ? Boolean.TRUE : rule.getCarryOverToNextCycle());
        existing.setOffsetDays(rule.getOffsetDays() == null ? 0 : rule.getOffsetDays());
        existing.setPaymentAnchorType(rule.getPaymentAnchorType());
        existing.setAnchorDay1(rule.getAnchorDay1());
        existing.setAnchorDay2(rule.getAnchorDay2());
        existing.setAnchorDay3(rule.getAnchorDay3());
        existing.setDescription(rule.getDescription());
        existing.setExampleRuleText(rule.getExampleRuleText());
        PartyAPaymentRule saved = partyAPaymentRuleRepository.save(existing);
        expectedRefundDateService.recalculateOrdersForTitle(saved.getPartyATitle());
        return saved;
    }

    @Transactional
    public void deleteRule(Long id) {
        PartyAPaymentRule existing = partyAPaymentRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("甲方回款规则不存在"));
        String title = existing.getPartyATitle();
        partyAPaymentRuleRepository.delete(existing);
        if (title != null && !title.trim().isEmpty()) {
            expectedRefundDateService.recalculateOrdersForTitle(title.trim());
        }
    }

    @Transactional
    public int recalculateByTitle(String partyATitle) {
        return expectedRefundDateService.recalculateOrdersForTitle(partyATitle);
    }

    @Transactional
    public int recalculateAllOpenOrders() {
        return expectedRefundDateService.recalculateAllOpenOrders();
    }

    private void normalizeAndValidate(PartyAPaymentRule rule, Long excludeId) {
        if (rule == null) {
            throw new IllegalArgumentException("规则不能为空");
        }
        if (rule.getPartyATitle() == null || rule.getPartyATitle().trim().isEmpty()) {
            throw new IllegalArgumentException("甲方抬头不能为空");
        }
        if (rule.getBaseEventType() == null || rule.getBaseEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("基准事件不能为空");
        }
        if (rule.getPaymentAnchorType() == null || rule.getPaymentAnchorType().trim().isEmpty()) {
            throw new IllegalArgumentException("付款节点类型不能为空");
        }
        rule.setPartyATitle(rule.getPartyATitle().trim());
        rule.setBaseEventType(rule.getBaseEventType().trim());
        rule.setPaymentAnchorType(rule.getPaymentAnchorType().trim());
        if (rule.getEnabled() == null) {
            rule.setEnabled(Boolean.TRUE);
        }
        if (rule.getCarryOverToNextCycle() == null) {
            rule.setCarryOverToNextCycle(Boolean.TRUE);
        }
        if (rule.getOffsetDays() == null) {
            rule.setOffsetDays(0);
        }

        List<PartyAPaymentRule> existing = partyAPaymentRuleRepository.findAllByPartyATitleTrimmed(rule.getPartyATitle());
        for (PartyAPaymentRule item : existing) {
            if (item.getId() != null && !item.getId().equals(excludeId)) {
                throw new IllegalArgumentException("该甲方抬头已存在回款规则，请直接编辑原规则");
            }
        }
    }

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) {
            return null;
        }
        return userRepository.findByUsername(name).orElse(null);
    }
}
