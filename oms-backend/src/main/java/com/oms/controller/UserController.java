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
import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private boolean isAdmin() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return false;
        return userRepository.findByUsername(name)
                .map(u -> "ROLE_ADMIN".equals(u.getRole()))
                .orElse(false);
    }

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return null;
        return userRepository.findByUsername(name).orElse(null);
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
    public List<User> getUsersByCompanyTitle(@RequestParam String companyTitle) {
        return userService.getUsersByCompanyTitle(companyTitle);
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
