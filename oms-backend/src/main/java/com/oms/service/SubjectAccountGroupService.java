package com.oms.service;

import com.oms.entity.PartnerInfo;
import com.oms.entity.SubjectAccountGroup;
import com.oms.entity.SubjectAccountMember;
import com.oms.entity.User;
import com.oms.repository.PartnerInfoRepository;
import com.oms.repository.SubjectAccountGroupRepository;
import com.oms.repository.SubjectAccountMemberRepository;
import com.oms.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubjectAccountGroupService {
    @Autowired
    private SubjectAccountGroupRepository subjectAccountGroupRepository;

    @Autowired
    private SubjectAccountMemberRepository subjectAccountMemberRepository;

    @Autowired
    private PartnerInfoRepository partnerInfoRepository;

    @Autowired
    private UserRepository userRepository;

    public record SubjectAccountContext(Long groupId,
                                        String groupKey,
                                        String primaryUsername,
                                        boolean primaryActor,
                                        List<String> usernames,
                                        List<Long> userIds) {
    }

    public record DuplicateCheckResult(boolean matched,
                                       boolean block,
                                       String message,
                                       String suggestedPrimaryUsername,
                                       List<Map<String, Object>> candidates) {
    }

    public boolean canManageGroups(User user) {
        if (user == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        String companyTitle = normalize(user.getCompanyTitle());
        return companyTitle != null && companyTitle.contains("飞础科智慧科技（上海）有限公司");
    }

    public void assertCanManageGroups(User user) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        if (!canManageGroups(user)) {
            throw new IllegalArgumentException("仅管理员或飞础科账号可执行归组");
        }
    }

    public SubjectAccountContext resolveContext(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername == null) {
            return new SubjectAccountContext(null, null, null, false, List.of(), List.of());
        }
        Optional<SubjectAccountMember> direct = subjectAccountMemberRepository.findByUsername(normalizedUsername);
        if (direct.isEmpty()) {
            bootstrapGroupForUsername(normalizedUsername);
            direct = subjectAccountMemberRepository.findByUsername(normalizedUsername);
        }
        if (direct.isEmpty()) {
            Long userId = userRepository.findByUsername(normalizedUsername).map(User::getId).orElse(null);
            return new SubjectAccountContext(null, null, normalizedUsername, true,
                    List.of(normalizedUsername), userId != null ? List.of(userId) : List.of());
        }
        SubjectAccountMember self = direct.get();
        SubjectAccountGroup group = subjectAccountGroupRepository.findById(self.getGroupId()).orElse(null);
        if (group == null) {
            Long userId = userRepository.findByUsername(normalizedUsername).map(User::getId).orElse(null);
            return new SubjectAccountContext(null, null, normalizedUsername, true,
                    List.of(normalizedUsername), userId != null ? List.of(userId) : List.of());
        }
        List<SubjectAccountMember> members = subjectAccountMemberRepository.findByGroupId(group.getId());
        LinkedHashSet<String> usernames = members.stream()
                .map(SubjectAccountMember::getUsername)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        usernames.add(normalizedUsername);
        List<Long> userIds = usernames.stream()
                .map(u -> userRepository.findByUsername(u).map(User::getId).orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        String primaryUsername = normalize(group.getPrimaryUsername());
        if (primaryUsername == null || !usernames.contains(primaryUsername)) {
            primaryUsername = normalize(normalizedUsername);
        }
        return new SubjectAccountContext(
                group.getId(),
                group.getGroupKey(),
                primaryUsername,
                normalizedUsername.equals(primaryUsername),
                new ArrayList<>(usernames),
                userIds
        );
    }

    public SubjectAccountContext resolveManualContext(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername == null) {
            return new SubjectAccountContext(null, null, null, false, List.of(), List.of());
        }
        Optional<SubjectAccountMember> direct = subjectAccountMemberRepository.findByUsername(normalizedUsername);
        if (direct.isEmpty()) {
            Long userId = userRepository.findByUsername(normalizedUsername).map(User::getId).orElse(null);
            return new SubjectAccountContext(null, null, normalizedUsername, true,
                    List.of(normalizedUsername), userId != null ? List.of(userId) : List.of());
        }
        SubjectAccountMember self = direct.get();
        SubjectAccountGroup group = subjectAccountGroupRepository.findById(self.getGroupId()).orElse(null);
        if (group == null || !isManualGroup(group)) {
            Long userId = userRepository.findByUsername(normalizedUsername).map(User::getId).orElse(null);
            return new SubjectAccountContext(null, null, normalizedUsername, true,
                    List.of(normalizedUsername), userId != null ? List.of(userId) : List.of());
        }
        List<SubjectAccountMember> members = subjectAccountMemberRepository.findByGroupId(group.getId());
        LinkedHashSet<String> usernames = members.stream()
                .map(SubjectAccountMember::getUsername)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        usernames.add(normalizedUsername);
        List<Long> userIds = usernames.stream()
                .map(u -> userRepository.findByUsername(u).map(User::getId).orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        String primaryUsername = normalize(group.getPrimaryUsername());
        if (primaryUsername == null || !usernames.contains(primaryUsername)) {
            primaryUsername = normalize(normalizedUsername);
        }
        return new SubjectAccountContext(
                group.getId(),
                group.getGroupKey(),
                primaryUsername,
                normalizedUsername.equals(primaryUsername),
                new ArrayList<>(usernames),
                userIds
        );
    }

    public List<String> sharedUsernames(String username) {
        return resolveContext(username).usernames();
    }

    public List<Long> sharedUserIds(String username) {
        return resolveContext(username).userIds();
    }

    public List<String> manuallySharedUsernames(String username) {
        return resolveManualContext(username).usernames();
    }

    public List<Long> manuallySharedUserIds(String username) {
        return resolveManualContext(username).userIds();
    }

    public Page<Map<String, Object>> listManualGroups(String keyword, Pageable pageable) {
        List<SubjectAccountGroup> manualGroups = subjectAccountGroupRepository.findAll(Sort.by(Sort.Direction.DESC, "updateTime"))
                .stream()
                .filter(this::isManualGroup)
                .toList();
        String normalizedKeyword = normalize(keyword);
        List<Map<String, Object>> rows = manualGroups.stream()
                .map(this::toManualGroupSummary)
                .filter(row -> matchesManualGroupKeyword(row, normalizedKeyword))
                .toList();
        int fromIndex = Math.toIntExact(pageable.getOffset());
        if (fromIndex >= rows.size()) {
            return new PageImpl<>(List.of(), pageable, rows.size());
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(fromIndex, toIndex), pageable, rows.size());
    }

    public Map<String, Object> getManualGroupDetail(String username) {
        SubjectAccountContext context = resolveManualContext(username);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("groupId", context.groupId());
        row.put("groupKey", context.groupKey());
        row.put("primaryUsername", context.primaryUsername());
        row.put("primaryActor", context.primaryActor());
        row.put("usernames", context.usernames());
        row.put("userIds", context.userIds());
        row.put("manualBound", context.groupId() != null && context.groupKey() != null && context.groupKey().startsWith("MANUAL:"));
        row.put("queriedUsername", normalize(username));
        return row;
    }

    public Map<String, Object> getManualGroupMap(Collection<String> usernames) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (usernames == null) {
            return result;
        }
        for (String username : usernames) {
            String normalized = normalize(username);
            if (normalized == null || result.containsKey(normalized)) {
                continue;
            }
            result.put(normalized, getManualGroupDetail(normalized));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> removeFromManualGroup(String username, Long operatorUserId) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("请提供要移除的账号");
        }
        SubjectAccountMember member = subjectAccountMemberRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("该账号当前未归组"));
        SubjectAccountGroup group = subjectAccountGroupRepository.findById(member.getGroupId()).orElse(null);
        if (group == null || !isManualGroup(group)) {
            throw new IllegalArgumentException("该账号当前不在手工归组中");
        }
        List<SubjectAccountMember> members = subjectAccountMemberRepository.findByGroupId(group.getId());
        List<String> remainingUsernames = members.stream()
                .map(SubjectAccountMember::getUsername)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .filter(item -> !item.equals(normalizedUsername))
                .distinct()
                .toList();
        subjectAccountMemberRepository.deleteByUsername(normalizedUsername);
        if (remainingUsernames.size() <= 1) {
            for (String remain : remainingUsernames) {
                subjectAccountMemberRepository.deleteByUsername(remain);
            }
            subjectAccountGroupRepository.delete(group);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("removedUsername", normalizedUsername);
            row.put("groupDeleted", true);
            row.put("remainingUsernames", remainingUsernames);
            return row;
        }
        String nextPrimary = remainingUsernames.contains(normalize(group.getPrimaryUsername()))
                ? normalize(group.getPrimaryUsername())
                : remainingUsernames.get(0);
        group.setPrimaryUsername(nextPrimary);
        if (group.getCreatedBy() == null) {
            group.setCreatedBy(operatorUserId);
        }
        subjectAccountGroupRepository.save(group);
        for (String remain : remainingUsernames) {
            subjectAccountMemberRepository.findByUsername(remain).ifPresent(existing -> {
                existing.setPrimaryMember(remain.equals(nextPrimary));
                subjectAccountMemberRepository.save(existing);
            });
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("removedUsername", normalizedUsername);
        row.put("groupDeleted", false);
        row.put("remainingUsernames", remainingUsernames);
        row.put("primaryUsername", nextPrimary);
        row.put("groupId", group.getId());
        return row;
    }

    public boolean isPrimaryActor(String username) {
        return resolveContext(username).primaryActor();
    }

    public void assertPrimaryActor(User currentUser, String actionName) {
        if (currentUser == null) {
            throw new IllegalArgumentException("请先登录");
        }
        if ("ROLE_ADMIN".equals(currentUser.getRole())) {
            return;
        }
        SubjectAccountContext context = resolveContext(currentUser.getUsername());
        if (context.groupId() != null && !context.primaryActor()) {
            String primary = context.primaryUsername() != null ? context.primaryUsername() : "主账号";
            throw new IllegalArgumentException("当前账号属于共享主体，只允许主账号「" + primary + "」执行" + (actionName == null ? "写操作" : actionName));
        }
    }

    public DuplicateCheckResult checkDuplicateRisk(String title,
                                                   String contactPerson,
                                                   String contactPhone,
                                                   String username,
                                                   Long excludePartnerInfoId) {
        String normalizedTitle = normalize(title);
        String normalizedContactPerson = normalize(contactPerson);
        String normalizedContactPhone = normalize(contactPhone);
        String normalizedUsername = normalize(username);

        LinkedHashMap<Long, PartnerInfo> matched = new LinkedHashMap<>();
        if (normalizedTitle != null && normalizedContactPerson != null) {
            for (PartnerInfo item : partnerInfoRepository.findAllByTitleAndContactPerson(normalizedTitle, normalizedContactPerson)) {
                if (!sameId(item.getId(), excludePartnerInfoId)) matched.put(item.getId(), item);
            }
        }
        if (normalizedTitle != null && normalizedContactPhone != null) {
            for (PartnerInfo item : partnerInfoRepository.findAllByTitleAndContactPhone(normalizedTitle, normalizedContactPhone)) {
                if (!sameId(item.getId(), excludePartnerInfoId)) matched.put(item.getId(), item);
            }
        }
        if (normalizedUsername != null) {
            for (PartnerInfo item : partnerInfoRepository.findAllByUsernameTrimmed(normalizedUsername)) {
                if (!sameId(item.getId(), excludePartnerInfoId)) matched.put(item.getId(), item);
            }
        }

        List<PartnerInfo> candidates = new ArrayList<>(matched.values());
        boolean sameSubjectDuplicate = candidates.stream().anyMatch(item -> sameSubject(item, normalizedTitle, normalizedContactPerson, normalizedContactPhone));
        boolean sameUsernameDuplicate = candidates.stream().anyMatch(item -> normalizedUsername != null && normalizedUsername.equals(normalize(item.getUsername())));

        String suggestedPrimary = choosePrimaryUsername(candidates);
        String message = "";
        boolean block = false;
        if (sameUsernameDuplicate) {
            block = true;
            message = "该用户名已在用户信息维护中存在，请直接编辑原记录，不要重复创建。";
        } else if (sameSubjectDuplicate) {
            block = true;
            message = "检测到相同抬头和联系人/手机号的已有主体，请优先引用已有账号，避免重复建号。";
        }

        List<Map<String, Object>> out = candidates.stream()
                .sorted(Comparator.comparing((PartnerInfo p) -> normalize(p.getUsername()) == null)
                        .thenComparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId()))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", p.getId());
                    row.put("name", safe(p.getName()));
                    row.put("title", safe(p.getTitle()));
                    row.put("contactPerson", safe(p.getContactPerson()));
                    row.put("contactPhone", safe(p.getContactPhone()));
                    row.put("username", safe(p.getUsername()));
                    row.put("primary", normalize(p.getUsername()) != null && normalize(p.getUsername()).equals(suggestedPrimary));
                    return row;
                })
                .toList();

        return new DuplicateCheckResult(!out.isEmpty(), block, message, suggestedPrimary, out);
    }

    @Transactional
    public void syncPartnerInfo(PartnerInfo partnerInfo, Long operatorUserId) {
        if (partnerInfo == null) {
            return;
        }
        bootstrapGroupForPartnerInfo(partnerInfo, operatorUserId);
    }

    @Transactional
    public SubjectAccountContext bindUsernames(String leftUsername,
                                               String rightUsername,
                                               String preferredPrimaryUsername,
                                               Long operatorUserId) {
        String left = normalize(leftUsername);
        String right = normalize(rightUsername);
        if (left == null || right == null) {
            throw new IllegalArgumentException("请提供两个要归组的账号");
        }
        if (userRepository.findByUsername(left).isEmpty() || userRepository.findByUsername(right).isEmpty()) {
            throw new IllegalArgumentException("归组账号必须先存在于用户表");
        }
        SubjectAccountContext leftContext = resolveContext(left);
        SubjectAccountContext rightContext = resolveContext(right);
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        usernames.addAll(leftContext.usernames());
        usernames.addAll(rightContext.usernames());
        usernames.add(left);
        usernames.add(right);
        String primaryUsername = normalize(preferredPrimaryUsername);
        if (primaryUsername == null || !usernames.contains(primaryUsername)) {
            primaryUsername = normalize(leftContext.primaryUsername());
        }
        if (primaryUsername == null || !usernames.contains(primaryUsername)) {
            primaryUsername = normalize(rightContext.primaryUsername());
        }
        if (primaryUsername == null) {
            primaryUsername = usernames.iterator().next();
        }

        SubjectAccountGroup group = null;
        if (leftContext.groupId() != null) {
            group = subjectAccountGroupRepository.findById(leftContext.groupId()).orElse(null);
        }
        if (group == null && rightContext.groupId() != null) {
            group = subjectAccountGroupRepository.findById(rightContext.groupId()).orElse(null);
        }
        if (group == null) {
            group = new SubjectAccountGroup();
            group.setGroupKey("MANUAL:" + left + "|" + right);
            group.setGroupName("手工归组");
            group.setCreatedBy(operatorUserId);
        }
        if (group.getTitle() == null || group.getTitle().isBlank()) {
            PartnerInfo ref = firstPartnerInfoForUsernames(usernames);
            if (ref != null) {
                group.setTitle(ref.getTitle());
                group.setContactPerson(ref.getContactPerson());
                group.setContactPhone(ref.getContactPhone());
                if (group.getGroupName() == null || group.getGroupName().isBlank() || "手工归组".equals(group.getGroupName())) {
                    group.setGroupName(buildGroupName(normalize(ref.getTitle()), normalize(ref.getContactPerson()), normalize(ref.getContactPhone())));
                }
            }
        }
        group.setPrimaryUsername(primaryUsername);
        SubjectAccountGroup savedGroup = subjectAccountGroupRepository.save(group);

        for (String username : usernames) {
            SubjectAccountMember member = subjectAccountMemberRepository.findByUsername(username).orElseGet(SubjectAccountMember::new);
            member.setGroupId(savedGroup.getId());
            member.setUsername(username);
            member.setPrimaryMember(username.equals(primaryUsername));
            if (member.getCreatedBy() == null) {
                member.setCreatedBy(operatorUserId);
            }
            subjectAccountMemberRepository.save(member);
        }
        return resolveContext(primaryUsername);
    }

    private void bootstrapGroupForUsername(String username) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername == null) {
            return;
        }
        List<PartnerInfo> infos = partnerInfoRepository.findByUsername(normalizedUsername);
        for (PartnerInfo info : infos) {
            bootstrapGroupForPartnerInfo(info, info.getCreatedBy());
        }
    }

    private void bootstrapGroupForPartnerInfo(PartnerInfo partnerInfo, Long operatorUserId) {
        if (partnerInfo == null) {
            return;
        }
        DuplicateCluster cluster = buildCluster(partnerInfo);
        if (cluster.usernames().size() < 2 || cluster.groupKey() == null) {
            return;
        }
        SubjectAccountGroup group = subjectAccountGroupRepository.findByGroupKey(cluster.groupKey())
                .orElseGet(SubjectAccountGroup::new);
        group.setGroupKey(cluster.groupKey());
        group.setGroupName(cluster.groupName());
        group.setTitle(cluster.title());
        group.setContactPerson(cluster.contactPerson());
        group.setContactPhone(cluster.contactPhone());
        if (group.getCreatedBy() == null) {
            group.setCreatedBy(operatorUserId);
        }
        String primaryUsername = choosePrimaryUsername(cluster.partnerInfos());
        group.setPrimaryUsername(primaryUsername);
        SubjectAccountGroup savedGroup = subjectAccountGroupRepository.save(group);

        List<SubjectAccountMember> existingMembers = subjectAccountMemberRepository.findByGroupId(savedGroup.getId());
        Map<String, SubjectAccountMember> byUsername = existingMembers.stream()
                .filter(item -> normalize(item.getUsername()) != null)
                .collect(Collectors.toMap(item -> normalize(item.getUsername()), item -> item, (a, b) -> a, LinkedHashMap::new));

        for (String username : cluster.usernames()) {
            SubjectAccountMember member = byUsername.getOrDefault(username, new SubjectAccountMember());
            member.setGroupId(savedGroup.getId());
            member.setUsername(username);
            member.setPrimaryMember(username.equals(primaryUsername));
            if (member.getCreatedBy() == null) {
                member.setCreatedBy(operatorUserId);
            }
            subjectAccountMemberRepository.save(member);
        }

        for (SubjectAccountMember existing : existingMembers) {
            if (!cluster.usernames().contains(normalize(existing.getUsername()))) {
                continue;
            }
            existing.setPrimaryMember(normalize(existing.getUsername()) != null && normalize(existing.getUsername()).equals(primaryUsername));
            subjectAccountMemberRepository.save(existing);
        }
    }

    private DuplicateCluster buildCluster(PartnerInfo anchor) {
        String title = normalize(anchor.getTitle());
        String contactPerson = normalize(anchor.getContactPerson());
        String contactPhone = normalize(anchor.getContactPhone());
        LinkedHashMap<Long, PartnerInfo> merged = new LinkedHashMap<>();
        if (title != null && contactPerson != null) {
            for (PartnerInfo item : partnerInfoRepository.findAllByTitleAndContactPerson(title, contactPerson)) {
                if (item.getId() != null) merged.put(item.getId(), item);
            }
        }
        if (title != null && contactPhone != null) {
            for (PartnerInfo item : partnerInfoRepository.findAllByTitleAndContactPhone(title, contactPhone)) {
                if (item.getId() != null) merged.put(item.getId(), item);
            }
        }
        if (anchor.getId() != null) {
            merged.put(anchor.getId(), anchor);
        }
        List<PartnerInfo> partnerInfos = merged.values().stream()
                .filter(item -> normalize(item.getUsername()) != null)
                .toList();
        LinkedHashSet<String> usernames = partnerInfos.stream()
                .map(PartnerInfo::getUsername)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new DuplicateCluster(
                buildGroupKey(title, contactPerson, contactPhone),
                buildGroupName(title, contactPerson, contactPhone),
                title,
                contactPerson,
                contactPhone,
                partnerInfos,
                usernames
        );
    }

    private PartnerInfo firstPartnerInfoForUsernames(Collection<String> usernames) {
        return usernames.stream()
                .map(partnerInfoRepository::findByUsername)
                .flatMap(List::stream)
                .sorted(Comparator
                        .comparing((PartnerInfo item) -> item.getCreateTime() == null ? java.time.LocalDateTime.MAX : item.getCreateTime())
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .findFirst()
                .orElse(null);
    }

    private String choosePrimaryUsername(Collection<PartnerInfo> partnerInfos) {
        return partnerInfos.stream()
                .filter(item -> normalize(item.getUsername()) != null)
                .sorted(Comparator
                        .comparing((PartnerInfo item) -> item.getCreateTime() == null ? java.time.LocalDateTime.MAX : item.getCreateTime())
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .map(PartnerInfo::getUsername)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean sameSubject(PartnerInfo info, String title, String contactPerson, String contactPhone) {
        if (info == null || title == null) {
            return false;
        }
        boolean sameTitle = title.equals(normalize(info.getTitle()));
        boolean samePerson = contactPerson != null && contactPerson.equals(normalize(info.getContactPerson()));
        boolean samePhone = contactPhone != null && contactPhone.equals(normalize(info.getContactPhone()));
        return sameTitle && (samePerson || samePhone);
    }

    private String buildGroupKey(String title, String contactPerson, String contactPhone) {
        if (title == null) {
            return null;
        }
        if (contactPerson != null) {
            return "TITLE:" + title + "|PERSON:" + contactPerson;
        }
        if (contactPhone != null) {
            return "TITLE:" + title + "|PHONE:" + contactPhone;
        }
        return null;
    }

    private String buildGroupName(String title, String contactPerson, String contactPhone) {
        if (title == null) {
            return "共享主体";
        }
        if (contactPerson != null) {
            return title + " / " + contactPerson;
        }
        if (contactPhone != null) {
            return title + " / " + contactPhone;
        }
        return title;
    }

    private boolean isManualGroup(SubjectAccountGroup group) {
        String groupKey = group != null ? normalize(group.getGroupKey()) : null;
        return groupKey != null && groupKey.startsWith("MANUAL:");
    }

    private Map<String, Object> toManualGroupSummary(SubjectAccountGroup group) {
        Map<String, Object> row = new LinkedHashMap<>();
        List<String> usernames = group == null || group.getId() == null
                ? List.of()
                : subjectAccountMemberRepository.findByGroupId(group.getId()).stream()
                .map(SubjectAccountMember::getUsername)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        row.put("id", group != null ? group.getId() : null);
        row.put("groupKey", group != null ? safe(group.getGroupKey()) : "");
        row.put("groupName", group != null ? safe(group.getGroupName()) : "");
        row.put("title", group != null ? safe(group.getTitle()) : "");
        row.put("contactPerson", group != null ? safe(group.getContactPerson()) : "");
        row.put("contactPhone", group != null ? safe(group.getContactPhone()) : "");
        row.put("primaryUsername", group != null ? safe(group.getPrimaryUsername()) : "");
        row.put("usernames", usernames);
        row.put("createTime", group != null ? group.getCreateTime() : null);
        row.put("updateTime", group != null ? group.getUpdateTime() : null);
        return row;
    }

    private boolean matchesManualGroupKeyword(Map<String, Object> row, String keyword) {
        if (keyword == null) {
            return true;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(normalize((String) row.get("groupKey")));
        candidates.add(normalize((String) row.get("groupName")));
        candidates.add(normalize((String) row.get("title")));
        candidates.add(normalize((String) row.get("contactPerson")));
        candidates.add(normalize((String) row.get("contactPhone")));
        candidates.add(normalize((String) row.get("primaryUsername")));
        Object usernames = row.get("usernames");
        if (usernames instanceof Collection<?> collection) {
            for (Object item : collection) {
                candidates.add(normalize(item == null ? null : String.valueOf(item)));
            }
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> value.contains(keyword));
    }

    private boolean sameId(Long left, Long right) {
        return left != null && right != null && left.equals(right);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record DuplicateCluster(String groupKey,
                                    String groupName,
                                    String title,
                                    String contactPerson,
                                    String contactPhone,
                                    List<PartnerInfo> partnerInfos,
                                    Set<String> usernames) {
    }
}
