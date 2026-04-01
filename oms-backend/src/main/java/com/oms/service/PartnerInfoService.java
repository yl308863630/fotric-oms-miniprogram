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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PartnerInfoService {
    private static final String FEICHUKE_TITLE = "飞础科智慧科技（上海）有限公司";
    /** 视为飞础科（代运营）可查看全部用户信息的用户名，用于 companyTitle 未维护时的兜底 */
    private static final Set<String> FEICHUKE_USERNAMES = Set.of("sonmin");
    /** 用户信息维护新建账号默认权限（与用户管理勾选保持一致） */
    private static final String DEFAULT_PERMISSIONS = "dashboard,product,sales,purchase,settlement,settlement_sales,settlement_finance,purchase_payment_apply,purchase_invoice_track,purchase_payment_finance,cooperation";

    @Autowired
    private PartnerInfoRepository partnerInfoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

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

    /** 合作方主数据允许所有已登录用户查看，便于跨主体直接引用已有账号/抬头。 */
    public boolean canView(User user, PartnerInfo info) {
        return user != null && info != null;
    }

    /** 编辑/删除仍保留权限边界：admin/飞础科可操作全部，其余仅能操作自己创建的记录。 */
    public boolean canModify(User user, PartnerInfo info) {
        if (user == null || info == null) return false;
        if (isAdminOrFeichuke(user)) return true;
        return info.getCreatedBy() != null && info.getCreatedBy().equals(user.getId());
    }

    public List<PartnerInfo> getAllPartnerInfo() {
        return partnerInfoRepository.findAll();
    }

    /**
     * 分页查询用户信息（合作管理-用户信息维护）。
     * 改为全局可查看，便于各主体直接引用库里已维护的账号/抬头。
     * 编辑/删除权限仍按 canModify 控制。
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

    /**
     * 按抬头查询一条记录，供跨主体直接引用已有抬头资料。
     * 用于「抬头一致自动带出」税号、开户行等。
     */
    public PartnerInfo getByTitleForCurrentUser(String title) {
        if (title == null || title.trim().isEmpty()) return null;
        User currentUser = getCurrentUser();
        return partnerInfoRepository.findOneByTitleTrimmed(title.trim())
                .filter(info -> canView(currentUser, info))
                .orElse(null);
    }

    /**
     * 按抬头/名称查询该抬头下全部记录（同一公司多用户时可多选一）。
     * 用于指派时「乙方业务员」下拉：选到唯一用户名后联系人等从该条准确带出。
     */
    public List<PartnerInfo> getListByTitleForCurrentUser(String title) {
        if (title == null || title.trim().isEmpty()) return List.of();
        String t = title.trim();
        User currentUser = getCurrentUser();
        List<PartnerInfo> all = partnerInfoRepository.findAllByTitleOrNameTrimmed(t);
        if (all == null) return List.of();
        return all.stream().filter(info -> canView(currentUser, info)).toList();
    }

    /** 按用户名查该用户对应的 PartnerInfo 列表，用于新建销售订单展示业务员对应的联系人。 */
    public List<PartnerInfo> getListByUsernameForCurrentUser(String username) {
        if (username == null || username.trim().isEmpty()) return List.of();
        User currentUser = getCurrentUser();
        List<PartnerInfo> all = partnerInfoRepository.findByUsername(username.trim());
        if (all == null) return List.of();
        return all.stream().filter(info -> canView(currentUser, info)).toList();
    }

    /** 供「用户信息维护」下拉全局引用已有系统账号：所有启用账号均可见，避免跨公司已存在账号仍只能手输。 */
    public List<Map<String, Object>> getReferenceUsers() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return List.of();
        }
        return userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("username", user.getUsername() != null ? user.getUsername().trim() : "");
                    row.put("realName", user.getRealName() != null ? user.getRealName().trim() : "");
                    row.put("phone", user.getPhone() != null ? user.getPhone().trim() : "");
                    row.put("companyTitle", user.getCompanyTitle() != null ? user.getCompanyTitle().trim() : "");
                    return row;
                })
                .filter(row -> row.get("username") != null && !String.valueOf(row.get("username")).isBlank())
                .toList();
    }

    public PartnerInfo getPartnerInfoById(Long id) {
        return partnerInfoRepository.findById(id).orElse(null);
    }

    public PartnerInfo savePartnerInfo(PartnerInfo partnerInfo) {
        validateDuplicateRisk(partnerInfo, null);
        normalizeAndValidateUsernameUnique(partnerInfo, null);
        validateUsernameExistsAndEnabled(partnerInfo.getUsername());
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) return partnerInfoRepository.save(partnerInfo);
        if (partnerInfo.getId() == null) {
            partnerInfo.setCreatedBy(currentUser.getId());
        }
        PartnerInfo saved = partnerInfoRepository.save(partnerInfo);
        subjectAccountGroupService.syncPartnerInfo(saved, currentUser.getId());
        return saved;
    }

    /** 用户名作为唯一标识：去空格并校验在合作管理-用户信息维护中不重复（另一条记录已占用该用户名则报错）。excludeId 为更新时当前记录 id。 */
    private void normalizeAndValidateUsernameUnique(PartnerInfo partnerInfo, Long excludeId) {
        String raw = partnerInfo.getUsername();
        if (raw != null && !raw.trim().isEmpty()) {
            String uname = raw.trim();
            partnerInfo.setUsername(uname);
            List<PartnerInfo> existing = partnerInfoRepository.findAllByUsernameTrimmed(uname);
            if (existing != null && !existing.isEmpty()) {
                for (PartnerInfo p : existing) {
                    if (p.getId() != null && !p.getId().equals(excludeId)) {
                        throw new IllegalArgumentException("用户名「" + uname + "」已在用户信息维护中被其他记录使用，请使用其他登录名或编辑原记录。用户名作为唯一标识不可重复。");
                    }
                }
            }
        }
    }

    /** 若 username 非空，校验其在用户表中存在且启用，保证「交付方→业务员」链路有效 */
    private void validateUsernameExistsAndEnabled(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String uname = username.trim();
        User u = userRepository.findByUsername(uname).orElse(null);
        if (u == null) {
            throw new IllegalArgumentException("用户名「" + uname + "」在用户管理中不存在，请先在用户管理创建该账号后再保存。");
        }
        if (!Boolean.TRUE.equals(u.getEnabled())) {
            throw new IllegalArgumentException("用户名「" + uname + "」在用户管理中已禁用，请先启用该账号后再保存。");
        }
    }

    /**
     * 创建乙方登录账号：先创建 User，再保存 PartnerInfo。用户名必须唯一。
     * @return 保存后的 PartnerInfo
     * @throws IllegalArgumentException 当用户名为空或已存在时
     */
    public PartnerInfo savePartnerInfoWithAccount(PartnerInfo partnerInfo, String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("创建账号时密码不能为空");
        }
        validateDuplicateRisk(partnerInfo, null);
        String username = partnerInfo.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("创建账号时用户名不能为空");
        }
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new IllegalArgumentException("未登录或用户信息异常");
        }
        User newUser = new User();
        newUser.setUsername(username.trim());
        newUser.setPassword(rawPassword);
        newUser.setRealName(partnerInfo.getName());
        newUser.setPhone(partnerInfo.getContactPhone());
        newUser.setDepartment("运营部");
        newUser.setCompanyTitle(partnerInfo.getTitle());
        newUser.setPermissions(DEFAULT_PERMISSIONS);
        // 用户信息维护创建的业务员账号统一为交付方
        newUser.setRole("ROLE_DELIVERY");
        newUser.setEnabled(true);
        userService.saveUser(newUser);
        partnerInfo.setCreatedBy(currentUser.getId());
        PartnerInfo saved = partnerInfoRepository.save(partnerInfo);
        subjectAccountGroupService.syncPartnerInfo(saved, currentUser.getId());
        return saved;
    }

    /**
     * 更新合作方信息；若 newPassword 非空且当前用户为本人或创建人，则同时更新对应用户的密码。
     */
    public PartnerInfo updatePartnerInfo(Long id, PartnerInfo partnerInfo, String newPassword) {
        User currentUser = getCurrentUser();
        PartnerInfo existing = partnerInfoRepository.findById(id).orElse(null);
        if (existing == null) return null;
        if (!canModify(currentUser, existing)) {
            return null;
        }
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            boolean isSelf = currentUser.getUsername() != null && currentUser.getUsername().equals(existing.getUsername());
            boolean isCreator = existing.getCreatedBy() != null && existing.getCreatedBy().equals(currentUser.getId());
            if (isSelf || isCreator) {
                userRepository.findByUsername(existing.getUsername()).ifPresent(u -> {
                    u.setPassword(passwordEncoder.encode(newPassword.trim()));
                    userRepository.save(u);
                });
            }
        }
        normalizeAndValidateUsernameUnique(partnerInfo, id);
        validateDuplicateRisk(partnerInfo, id);
        validateUsernameExistsAndEnabled(partnerInfo.getUsername());
        partnerInfo.setId(id);
        partnerInfo.setCreatedBy(existing.getCreatedBy());
        partnerInfo.setCreateTime(existing.getCreateTime());
        PartnerInfo saved = partnerInfoRepository.save(partnerInfo);
        subjectAccountGroupService.syncPartnerInfo(saved, currentUser != null ? currentUser.getId() : existing.getCreatedBy());
        return saved;
    }

    public PartnerInfo updatePartnerInfo(Long id, PartnerInfo partnerInfo) {
        return updatePartnerInfo(id, partnerInfo, null);
    }

    public boolean deletePartnerInfo(Long id) {
        User currentUser = getCurrentUser();
        PartnerInfo existing = partnerInfoRepository.findById(id).orElse(null);
        if (existing == null) return false;
        if (!canModify(currentUser, existing)) {
            return false;
        }
        partnerInfoRepository.deleteById(id);
        return true;
    }

    public SubjectAccountGroupService.DuplicateCheckResult checkDuplicateRisk(PartnerInfo partnerInfo, Long excludeId) {
        if (partnerInfo == null) {
            return new SubjectAccountGroupService.DuplicateCheckResult(false, false, "", null, List.of());
        }
        return subjectAccountGroupService.checkDuplicateRisk(
                partnerInfo.getTitle(),
                partnerInfo.getContactPerson(),
                partnerInfo.getContactPhone(),
                partnerInfo.getUsername(),
                excludeId
        );
    }

    private void validateDuplicateRisk(PartnerInfo partnerInfo, Long excludeId) {
        SubjectAccountGroupService.DuplicateCheckResult result = checkDuplicateRisk(partnerInfo, excludeId);
        if (result.block()) {
            String suggested = result.suggestedPrimaryUsername();
            String suffix = (suggested != null && !suggested.isBlank())
                    ? " 建议直接引用已有主账号「" + suggested + "」。"
                    : "";
            throw new IllegalArgumentException(result.message() + suffix);
        }
    }
}
