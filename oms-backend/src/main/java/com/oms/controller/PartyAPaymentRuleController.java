package com.oms.controller;

import com.oms.entity.PartyAPaymentRule;
import com.oms.service.PartyAPaymentRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/party-a-payment-rules")
public class PartyAPaymentRuleController {
    @Autowired
    private PartyAPaymentRuleService partyAPaymentRuleService;

    @GetMapping
    public Page<PartyAPaymentRule> list(
            @RequestParam(required = false) String partyATitle,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return partyAPaymentRuleService.getRules(partyATitle, enabled, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartyAPaymentRule> get(@PathVariable Long id) {
        PartyAPaymentRule rule = partyAPaymentRuleService.getRuleById(id);
        return rule == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(rule);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PartyAPaymentRule rule) {
        try {
            return ResponseEntity.ok(partyAPaymentRuleService.createRule(rule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "保存规则失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PartyAPaymentRule rule) {
        try {
            return ResponseEntity.ok(partyAPaymentRuleService.updateRule(id, rule));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "更新规则失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            partyAPaymentRuleService.deleteRule(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(@RequestBody(required = false) Map<String, Object> request) {
        String partyATitle = request != null && request.get("partyATitle") != null
                ? String.valueOf(request.get("partyATitle")).trim()
                : "";
        boolean all = request != null && Boolean.parseBoolean(String.valueOf(request.getOrDefault("allOpenOrders", "false")));
        int updated = all || partyATitle.isEmpty()
                ? partyAPaymentRuleService.recalculateAllOpenOrders()
                : partyAPaymentRuleService.recalculateByTitle(partyATitle);
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }
}
