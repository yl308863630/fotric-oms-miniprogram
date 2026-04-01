package com.oms.controller;

import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.SubjectAccountGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subject-account-groups")
public class SubjectAccountGroupController {
    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null || name.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(name.trim()).orElse(null);
    }

    @GetMapping("/current")
    public ResponseEntity<?> current() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        return ResponseEntity.ok(subjectAccountGroupService.resolveContext(currentUser.getUsername()));
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String username) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        if (!subjectAccountGroupService.canManageGroups(currentUser)) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员或飞础科账号可执行归组"));
        }
        return ResponseEntity.ok(subjectAccountGroupService.getManualGroupDetail(username));
    }

    @GetMapping("/manual-groups")
    public ResponseEntity<?> manualGroups(@RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        if (!subjectAccountGroupService.canManageGroups(currentUser)) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员或飞础科账号可执行归组"));
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        Page<Map<String, Object>> result = subjectAccountGroupService.listManualGroups(keyword, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/lookup-batch")
    public ResponseEntity<?> lookupBatch(@RequestBody Map<String, Object> request) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        if (!subjectAccountGroupService.canManageGroups(currentUser)) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员或飞础科账号可执行归组"));
        }
        Object usernamesObj = request.get("usernames");
        java.util.List<String> usernames = usernamesObj instanceof java.util.Collection<?> collection
                ? collection.stream().map(item -> item == null ? null : String.valueOf(item)).toList()
                : java.util.List.of();
        return ResponseEntity.ok(subjectAccountGroupService.getManualGroupMap(usernames));
    }

    @PostMapping("/bind")
    public ResponseEntity<?> bind(@RequestBody Map<String, String> request) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        if (!subjectAccountGroupService.canManageGroups(currentUser)) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员或飞础科账号可执行归组"));
        }
        String leftUsername = request.get("leftUsername");
        String rightUsername = request.get("rightUsername");
        String primaryUsername = request.get("primaryUsername");
        return ResponseEntity.ok(subjectAccountGroupService.bindUsernames(
                leftUsername,
                rightUsername,
                primaryUsername,
                currentUser.getId()
        ));
    }

    @DeleteMapping("/member")
    public ResponseEntity<?> removeMember(@RequestParam String username) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        }
        if (!subjectAccountGroupService.canManageGroups(currentUser)) {
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员或飞础科账号可执行归组"));
        }
        try {
            return ResponseEntity.ok(subjectAccountGroupService.removeFromManualGroup(username, currentUser.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
