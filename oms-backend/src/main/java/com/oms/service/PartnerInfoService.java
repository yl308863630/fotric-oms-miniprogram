package com.oms.service;

import com.oms.entity.PartnerInfo;
import com.oms.entity.User;
import com.oms.repository.PartnerInfoRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PartnerInfoService {
    private static final String FEICHUKE_TITLE = "飞础科智慧科技（上海）有限公司";
    /** 视为飞础科（代运营）可查看全部用户信息的用户名，用于 companyTitle 未维护时的兜底 */
    private static final Set<String> FEICHUKE_USERNAMES = Set.of("sonmin");

    @Autowired
    private PartnerInfoRepository partnerInfoRepository;

    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    /** 管理员或飞础科（代运营）可见全部、可改删任意记录。飞础科判定：所在公司抬头包含飞础科公司名，或用户名在兜底列表中（便于 companyTitle 未维护时 sonmin 等仍可见全部） */
    private boolean isAdminOrFeichuke(User user) {
        if (user == null) return false;
        if ("ROLE_ADMIN".equals(user.getRole())) return true;
        String ct = user.getCompanyTitle();
        if (ct != null && !ct.trim().isEmpty() && ct.trim().contains(FEICHUKE_TITLE)) return true;
        return user.getUsername() != null && FEICHUKE_USERNAMES.contains(user.getUsername().trim());
    }

    /** 当前用户是否有权查看/编辑该条记录（created_by 为 null 的仅 admin/飞础科可见） */
    public boolean canAccess(User user, PartnerInfo info) {
        if (user == null || info == null) return false;
        if (isAdminOrFeichuke(user)) return true;
        return info.getCreatedBy() != null && info.getCreatedBy().equals(user.getId());
    }

    public List<PartnerInfo> getAllPartnerInfo() {
        return partnerInfoRepository.findAll();
    }

    /**
     * 分页查询用户信息（合作管理-用户信息维护、指派时交付方/出货方下拉等）。
     * 列表对所有人可见全量：飞础科便于派单选交付方/出货方，交付方等也能看到已有抬头避免重复新建；编辑/删除仍仅创建人或管理员/飞础科可操作。
     */
    public Page<PartnerInfo> getPartnerInfoWithPagination(String name, String identity, Pageable pageable) {
        Specification<PartnerInfo> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
            }
            if (identity != null && !identity.trim().isEmpty()) {
                predicates.add(cb.like(root.get("identities"), "%" + identity.trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return partnerInfoRepository.findAll(spec, pageable);
    }

    public PartnerInfo getPartnerInfoById(Long id) {
        return partnerInfoRepository.findById(id).orElse(null);
    }

    public PartnerInfo savePartnerInfo(PartnerInfo partnerInfo) {
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) return partnerInfoRepository.save(partnerInfo);
        if (partnerInfo.getId() == null) {
            partnerInfo.setCreatedBy(currentUser.getId());
        }
        return partnerInfoRepository.save(partnerInfo);
    }

    public PartnerInfo updatePartnerInfo(Long id, PartnerInfo partnerInfo) {
        User currentUser = getCurrentUser();
        PartnerInfo existing = partnerInfoRepository.findById(id).orElse(null);
        if (existing == null) return null;
        if (!canAccess(currentUser, existing)) {
            return null;
        }
        partnerInfo.setId(id);
        partnerInfo.setCreatedBy(existing.getCreatedBy());
        partnerInfo.setCreateTime(existing.getCreateTime());
        return partnerInfoRepository.save(partnerInfo);
    }

    public boolean deletePartnerInfo(Long id) {
        User currentUser = getCurrentUser();
        PartnerInfo existing = partnerInfoRepository.findById(id).orElse(null);
        if (existing == null) return false;
        if (!canAccess(currentUser, existing)) {
            return false;
        }
        partnerInfoRepository.deleteById(id);
        return true;
    }
}
