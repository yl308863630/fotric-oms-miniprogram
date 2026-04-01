package com.oms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.AuthorizationImportMapping;
import com.oms.entity.User;
import com.oms.repository.AuthorizationImportMappingRepository;
import com.oms.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthorizationImportMappingService {
    private final AuthorizationImportMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuthorizationImportMappingService(AuthorizationImportMappingRepository mappingRepository,
                                             UserRepository userRepository,
                                             ObjectMapper objectMapper) {
        this.mappingRepository = mappingRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listCurrentUserMappings() {
        User user = currentUserOrThrow();
        List<AuthorizationImportMapping> list = mappingRepository.findByCreatedByOrderByUpdateTimeDesc(user.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (AuthorizationImportMapping m : list) {
            out.add(toView(m));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> saveForCurrentUser(Long id, String mappingName, Map<String, String> mapping, Boolean setDefault) {
        User user = currentUserOrThrow();
        if (mappingName == null || mappingName.isBlank()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        try {
            AuthorizationImportMapping target;
            if (id == null) {
                target = new AuthorizationImportMapping();
                target.setCreatedBy(user.getId());
            } else {
                target = mappingRepository.findByIdAndCreatedBy(id, user.getId())
                        .orElseThrow(() -> new RuntimeException("映射模板不存在或无权限"));
            }
            target.setMappingName(mappingName.trim());
            target.setMappingJson(objectMapper.writeValueAsString(mapping == null ? Map.of() : mapping));
            if (Boolean.TRUE.equals(setDefault)) {
                clearDefault(user.getId());
                target.setIsDefault(true);
            } else if (target.getIsDefault() == null) {
                target.setIsDefault(false);
            }
            target = mappingRepository.save(target);
            return toView(target);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("保存映射模板失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteForCurrentUser(Long id) {
        User user = currentUserOrThrow();
        AuthorizationImportMapping mapping = mappingRepository.findByIdAndCreatedBy(id, user.getId())
                .orElseThrow(() -> new RuntimeException("映射模板不存在或无权限"));
        mappingRepository.delete(mapping);
    }

    @Transactional
    public Map<String, Object> setDefaultForCurrentUser(Long id) {
        User user = currentUserOrThrow();
        AuthorizationImportMapping mapping = mappingRepository.findByIdAndCreatedBy(id, user.getId())
                .orElseThrow(() -> new RuntimeException("映射模板不存在或无权限"));
        clearDefault(user.getId());
        mapping.setIsDefault(true);
        return toView(mappingRepository.save(mapping));
    }

    private void clearDefault(Long userId) {
        List<AuthorizationImportMapping> list = mappingRepository.findByCreatedByOrderByUpdateTimeDesc(userId);
        for (AuthorizationImportMapping m : list) {
            if (Boolean.TRUE.equals(m.getIsDefault())) {
                m.setIsDefault(false);
                mappingRepository.save(m);
            }
        }
    }

    private Map<String, Object> toView(AuthorizationImportMapping m) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", m.getId());
        out.put("mappingName", m.getMappingName());
        out.put("isDefault", Boolean.TRUE.equals(m.getIsDefault()));
        out.put("createTime", m.getCreateTime());
        out.put("updateTime", m.getUpdateTime());
        try {
            Map<String, String> map = objectMapper.readValue(
                    m.getMappingJson() == null ? "{}" : m.getMappingJson(),
                    new TypeReference<>() {}
            );
            out.put("mapping", map);
        } catch (Exception e) {
            out.put("mapping", Map.of());
        }
        return out;
    }

    private User currentUserOrThrow() {
        String username = SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            throw new RuntimeException("请先登录");
        }
        return userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new RuntimeException("当前用户不存在"));
    }
}
