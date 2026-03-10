package com.oms.service;

import com.oms.entity.User;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchUsers(String username, String realName, String role) {
        return userRepository.findAll((Specification<User>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (realName != null && !realName.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("realName")), "%" + realName.toLowerCase() + "%"));
            }
            if (role != null && !role.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public Page<User> searchUsersWithPagination(String username, String realName, String role, Pageable pageable) {
        return searchUsersWithPagination(username, realName, role, null, pageable);
    }

    /**
     * 分页查询用户。当 filterByCompanyTitle 非空时，仅返回该公司下的用户（用于交付方只看本公司）。
     */
    public Page<User> searchUsersWithPagination(String username, String realName, String role,
                                                  String filterByCompanyTitle, Pageable pageable) {
        return userRepository.findAll((Specification<User>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (realName != null && !realName.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("realName")), "%" + realName.toLowerCase() + "%"));
            }
            if (role != null && !role.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (filterByCompanyTitle != null && !filterByCompanyTitle.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("companyTitle"), filterByCompanyTitle.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    public User saveUser(User user) {
        if (user.getId() == null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            Long userId = user.getId();
            User existing = userRepository.findById(userId).orElseThrow();
            if (user.getPassword() != null && !user.getPassword().isEmpty() && !user.getPassword().equals(existing.getPassword())) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            } else {
                user.setPassword(existing.getPassword());
            }
            // 保持创建时间
            user.setCreateTime(existing.getCreateTime());
            // 保持最后登录时间
            user.setLastLoginTime(existing.getLastLoginTime());
        }
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        if (id != null) {
            userRepository.deleteById(id);
        }
    }

    public Optional<User> findById(Long id) {
        if (id == null) return Optional.empty();
        return userRepository.findById(id);
    }

    public List<User> getUsersByCompanyTitle(String companyTitle) {
        if (companyTitle == null || companyTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return userRepository.findByCompanyTitle(companyTitle);
    }
}
