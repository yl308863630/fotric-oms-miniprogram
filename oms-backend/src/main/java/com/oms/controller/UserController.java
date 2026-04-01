package com.oms.controller;

import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    /** 与合同预览、仓库可见范围等处的公司名保持一致 */
    private static final String COMPANY_REXIANG = "上海热像科技股份有限公司";
    private static final String COMPANY_FEICHUKE = "飞础科智慧科技（上海）有限公司";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    /** 抬头模糊匹配（去空格后双向 contains），与系统内其它公司判断一致 */
    private static boolean titleMatchesCanonical(String storedTitle, String canonical) {
        if (storedTitle == null || canonical == null) {
            return false;
        }
        String s = storedTitle.trim();
        String c = canonical.trim();
        if (s.isEmpty() || c.isEmpty()) {
            return false;
        }
        return s.contains(c) || c.contains(s);
    }

    /** 当前用户是否视为「上海热像科技」主体（权限高：可查热像 + 飞础科用户列表） */
    private static boolean isRexiangCompanyUser(User user) {
        if (user == null) {
            return false;
        }
        return titleMatchesCanonical(user.getCompanyTitle(), COMPANY_REXIANG);
    }

    /**
     * 飞础科主体且非热像（热像优先）。飞础科账号只能按公司拉取飞础科本公司用户，不可拉取热像用户列表。
     */
    private static boolean isFeichukeOnlyCompanyUser(User user) {
        if (user == null) {
            return false;
        }
        String ct = user.getCompanyTitle();
        return titleMatchesCanonical(ct, COMPANY_FEICHUKE) && !titleMatchesCanonical(ct, COMPANY_REXIANG);
    }

    /** 请求的 companyTitle 参数是否允许被当前用户查询 */
    private static boolean currentUserMayQueryUsersByCompanyTitle(User current, String requestedCompanyTitle) {
        if (requestedCompanyTitle == null || requestedCompanyTitle.trim().isEmpty()) {
            return false;
        }
        String req = requestedCompanyTitle.trim();

        if (isRexiangCompanyUser(current)) {
            return titleMatchesCanonical(req, COMPANY_REXIANG) || titleMatchesCanonical(req, COMPANY_FEICHUKE);
        }
        if (isFeichukeOnlyCompanyUser(current)) {
            return titleMatchesCanonical(req, COMPANY_FEICHUKE);
        }
        // 其它公司：仅可查询与本人所在抬头一致（允许与库中略有出入时的双向包含）
        String my = current.getCompanyTitle() == null ? "" : current.getCompanyTitle().trim();
        if (my.isEmpty()) {
            return false;
        }
        return my.equals(req) || my.contains(req) || req.contains(my);
    }

    private boolean isAdmin() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null)
            return false;
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        if (name == null || name.isBlank()) return false;
        return userRepository.findByUsername(name)
                .map(u -> "ROLE_ADMIN".equals(u.getRole()))
                .orElse(false);
    }

    private User getCurrentUser() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null)
            return null;
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        if (name == null || name.isBlank()) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", user.getId());
        body.put("username", user.getUsername() != null ? user.getUsername() : "");
        if (user.getRealName() != null) body.put("realName", user.getRealName());
        if (user.getCompanyTitle() != null && !user.getCompanyTitle().isBlank()) {
            body.put("companyTitle", user.getCompanyTitle().trim());
        }
        if (user.getPhone() != null) body.put("phone", user.getPhone());
        if (user.getEmail() != null) body.put("email", user.getEmail());
        if (user.getDepartment() != null) body.put("department", user.getDepartment());
        if (user.getRole() != null) body.put("role", user.getRole());
        if (user.getPermissions() != null) body.put("permissions", user.getPermissions());
        return ResponseEntity.ok(body);
    }

    /**
     * 当前用户自助更新个人资料（不允许修改角色/权限/公司抬头）
     */
    @PatchMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> body) {
        User current = getCurrentUser();
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String realName = body != null ? body.get("realName") : null;
        String phone = body != null ? body.get("phone") : null;
        String email = body != null ? body.get("email") : null;
        String department = body != null ? body.get("department") : null;

        if (realName != null) current.setRealName(realName.trim());
        if (phone != null) current.setPhone(phone.trim());
        if (department != null) current.setDepartment(department.trim());

        if (email != null) {
            String normalized = email.trim();
            if (normalized.isEmpty()) {
                current.setEmail(null);
            } else {
                User conflict = userRepository.findByEmail(normalized).orElse(null);
                if (conflict != null && (current.getId() == null || !current.getId().equals(conflict.getId()))) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱已被占用"));
                }
                current.setEmail(normalized);
            }
        }
        userRepository.save(current);
        return me();
    }

    @GetMapping
    public ResponseEntity<Page<User>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String filterCompanyTitle = null;
        if (!isAdmin()) {
            User current = getCurrentUser();
            if (current != null && current.getCompanyTitle() != null && !current.getCompanyTitle().trim().isEmpty()) {
                filterCompanyTitle = current.getCompanyTitle().trim();
            }
        }
        Page<User> result = userService.searchUsersWithPagination(username, realName, role, filterCompanyTitle, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-company-title")
    public ResponseEntity<?> getUsersByCompanyTitle(@RequestParam String companyTitle) {
        if (isAdmin()) {
            return ResponseEntity.ok(userService.getUsersByCompanyTitle(companyTitle.trim()));
        }
        User current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!currentUserMayQueryUsersByCompanyTitle(current, companyTitle)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无权按该公司抬头查询用户列表"));
        }
        return ResponseEntity.ok(userService.getUsersByCompanyTitle(companyTitle.trim()));
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {
        if (!isAdmin()) {
            User current = getCurrentUser();
            if (current == null || current.getCompanyTitle() == null || current.getCompanyTitle().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (user.getCompanyTitle() == null || !user.getCompanyTitle().trim().equals(current.getCompanyTitle().trim())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return ResponseEntity.ok(userService.saveUser(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
        if (!isAdmin()) {
            User current = getCurrentUser();
            if (current == null || current.getCompanyTitle() == null || current.getCompanyTitle().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (user.getCompanyTitle() == null || !user.getCompanyTitle().trim().equals(current.getCompanyTitle().trim())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        user.setId(id);
        return ResponseEntity.ok(userService.saveUser(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}
