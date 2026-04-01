package com.oms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.PartnerInfo;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.PartnerInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/partner-info")
public class PartnerInfoController {
    @Autowired
    private PartnerInfoService partnerInfoService;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    @GetMapping
    public Page<PartnerInfo> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String identity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return partnerInfoService.getPartnerInfoWithPagination(name, identity, PageRequest.of(page, size));
    }

    @GetMapping("/by-title")
    public ResponseEntity<PartnerInfo> getByTitle(@RequestParam String title) {
        PartnerInfo entity = partnerInfoService.getByTitleForCurrentUser(title);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(entity);
    }

    /** 按抬头查该抬头下全部 PartnerInfo（同一公司多用户时用于乙方业务员下拉选唯一用户名）。返回简化结构避免 LocalDateTime 等序列化导致 500，用户名作为唯一标识。 */
    @GetMapping("/by-title/list")
    public ResponseEntity<?> getListByTitle(@RequestParam(required = false) String title) {
        try {
            if (title == null || title.trim().isEmpty()) return ResponseEntity.ok(List.of());
            List<PartnerInfo> list = partnerInfoService.getListByTitleForCurrentUser(title.trim());
            if (list == null || list.isEmpty()) return ResponseEntity.ok(List.of());
            List<Map<String, Object>> out = new ArrayList<>();
            for (PartnerInfo p : list) {
                try {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId() != null ? p.getId() : 0L);
                    m.put("title", p.getTitle() != null ? p.getTitle() : "");
                    m.put("name", p.getName() != null ? p.getName() : "");
                    m.put("username", p.getUsername() != null ? p.getUsername() : "");
                    m.put("contactPerson", p.getContactPerson() != null ? p.getContactPerson() : "");
                    m.put("contactPhone", p.getContactPhone() != null ? p.getContactPhone() : "");
                    out.add(m);
                } catch (Throwable ex) {
                    org.slf4j.LoggerFactory.getLogger(PartnerInfoController.class).warn("by-title/list skip one row: {}", ex.getMessage());
                }
            }
            return ResponseEntity.ok(out);
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(PartnerInfoController.class).warn("by-title/list failed, returning empty list: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /** 按用户名查该用户对应的 PartnerInfo 列表（用于新建销售订单展示业务员对应的联系人），仅返回 id/username/contactPerson。若按用户名无结果则尝试按抬头/名称查（避免前端误传公司名导致 500 且能带出该公司下用户）。失败一律返回 200+空列表。 */
    @GetMapping("/by-username")
    public ResponseEntity<?> getListByUsername(@RequestParam(required = false) String username) {
        try {
            String param = (username != null) ? username.trim() : "";
            if (param.isEmpty()) return ResponseEntity.ok(List.of());
            List<PartnerInfo> list = partnerInfoService.getListByUsernameForCurrentUser(param);
            if (list == null || list.isEmpty()) {
                list = partnerInfoService.getListByTitleForCurrentUser(param);
            }
            if (list == null || list.isEmpty()) return ResponseEntity.ok(List.of());
            List<Map<String, Object>> out = new ArrayList<>();
            for (PartnerInfo p : list) {
                out.add(Map.of(
                    "id", p.getId() != null ? p.getId() : 0L,
                    "username", p.getUsername() != null ? p.getUsername() : "",
                    "contactPerson", p.getContactPerson() != null ? p.getContactPerson() : "",
                    "contactPhone", p.getContactPhone() != null ? p.getContactPhone() : "",
                    "title", p.getTitle() != null ? p.getTitle() : "",
                    "name", p.getName() != null ? p.getName() : "",
                    "email", p.getEmail() != null ? p.getEmail() : ""
                ));
            }
            return ResponseEntity.ok(out);
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(PartnerInfoController.class).warn("by-username failed, returning empty list: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/reference-users")
    public ResponseEntity<?> getReferenceUsers() {
        return ResponseEntity.ok(partnerInfoService.getReferenceUsers());
    }

    @PostMapping("/duplicate-check")
    public ResponseEntity<?> duplicateCheck(@RequestBody PartnerInfo partnerInfo) {
        try {
            Long excludeId = partnerInfo != null ? partnerInfo.getId() : null;
            return ResponseEntity.ok(partnerInfoService.checkDuplicateRisk(partnerInfo, excludeId));
        } catch (Throwable e) {
            return ResponseEntity.ok(Map.of(
                    "matched", false,
                    "block", false,
                    "message", "",
                    "suggestedPrimaryUsername", "",
                    "candidates", List.of()
            ));
        }
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<PartnerInfo> get(@PathVariable Long id) {
        PartnerInfo entity = partnerInfoService.getPartnerInfoById(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        User currentUser = getCurrentUser();
        if (!partnerInfoService.canView(currentUser, entity)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(entity);
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String password = root.has("password") && !root.get("password").isNull() ? root.get("password").asText("") : null;
            if (password != null) password = password.trim();
            PartnerInfo partnerInfo = objectMapper.readValue(rawBody, PartnerInfo.class);
            if (password != null && !password.isEmpty()) {
                PartnerInfo saved = partnerInfoService.savePartnerInfoWithAccount(partnerInfo, password);
                return ResponseEntity.ok(saved);
            }
            return ResponseEntity.ok(partnerInfoService.savePartnerInfo(partnerInfo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "保存失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String newPassword = root.has("newPassword") && !root.get("newPassword").isNull() ? root.get("newPassword").asText("") : null;
            if (newPassword != null) newPassword = newPassword.trim();
            PartnerInfo partnerInfo = objectMapper.readValue(rawBody, PartnerInfo.class);
            PartnerInfo updated = partnerInfoService.updatePartnerInfo(id, partnerInfo, newPassword);
            if (updated == null) {
                PartnerInfo existing = partnerInfoService.getPartnerInfoById(id);
                if (existing == null) {
                    return ResponseEntity.notFound().build();
                }
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "更新失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = partnerInfoService.deletePartnerInfo(id);
        if (!deleted) {
            PartnerInfo existing = partnerInfoService.getPartnerInfoById(id);
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().build();
    }
}