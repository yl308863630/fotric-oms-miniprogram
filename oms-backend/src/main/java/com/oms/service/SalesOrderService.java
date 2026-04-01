package com.oms.service;

import com.github.promeg.pinyinhelper.Pinyin;
import com.oms.entity.Contract;
import com.oms.entity.PurchaseInputInvoiceItem;
import com.oms.entity.PurchaseInputInvoiceRecord;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.PurchaseReconciliationItem;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.repository.ContractRepository;
import com.oms.repository.PurchaseInputInvoiceItemRepository;
import com.oms.repository.PurchaseInputInvoiceRecordRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.PurchaseReconciliationItemRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SalesOrderService {
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseReconciliationItemRepository purchaseReconciliationItemRepository;

    @Autowired
    private PurchaseInputInvoiceItemRepository purchaseInputInvoiceItemRepository;

    @Autowired
    private PurchaseInputInvoiceRecordRepository purchaseInputInvoiceRecordRepository;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private ExpectedRefundDateService expectedRefundDateService;

    @Autowired
    private SalesOrderMasterService salesOrderMasterService;

    @Value("${oms.sales-order.restricted-usernames:热像科技-财务,热像科技-仓库}")
    private String restrictedUsernames;

    @Value("${oms.sales-order.restricted-party-a-title:飞础科智慧科技（上海）有限公司}")
    private String restrictedPartyATitle;
    
    @Value("${oms.sales-order.warehouse-visible-party-a-titles:上海热像科技股份有限公司,飞础科智慧科技（上海）有限公司}")
    private String warehouseVisiblePartyATitles;

    @Value("${oms.notifications.reconciliation-ready-extra-at-real-names:张敏芳}")
    private String reconciliationReadyExtraAtRealNames;

    @Value("${oms.notifications.receipt-upload-at-real-names:杨旺}")
    private String receiptUploadAtRealNames;

    public Page<SalesOrder> searchOrders(String omsOrderNo, String platformOrderNo, String partyATitleKeyword, String deliveryPartyKeyword, String status, String excludeStatuses, String platformRefundStatus,
                                         String offlineSales, String ecommerceSalesName, String needReceiptSlip, String receiptFilter,
                                         String erpEntryStatus, Boolean expectedRefundOverdue, Boolean excludeChainOrders, Pageable pageable) {
        // 获取当前登录用户（未登录时为空，避免 NPE）
        String currentUsername = null;
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        }
        if (currentUsername == null) currentUsername = "anonymousUser";
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        Set<String> sharedUsernames = new LinkedHashSet<>(subjectAccountGroupService.manuallySharedUsernames(currentUsername));
        if (currentUsername != null && !currentUsername.isBlank()) sharedUsernames.add(currentUsername.trim());
        Set<Long> sharedUserIds = new LinkedHashSet<>(subjectAccountGroupService.manuallySharedUserIds(currentUsername));
        if (currentUser != null && currentUser.getId() != null) sharedUserIds.add(currentUser.getId());
        System.out.println("Current user: " + (currentUser != null ? currentUser.getRealName() + ", role: " + currentUser.getRole() : "null"));
        Set<Long> warehouseVisibleCreatorIds = getWarehouseVisibleCreatorIds();
        
        return salesOrderRepository.findAll((Specification<SalesOrder>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 采购/指派列表去重：
            // 默认不展示「下一层」链式订单（CHAIN_FROM:），但要保留“当前登录人自己创建的链式单”。
            // 否则转派后被指派方会看不到自己的待指派链式单，出现“没有形成销售订单指派数据”的假象。
            if (Boolean.TRUE.equals(excludeChainOrders)) {
                if (!sharedUserIds.isEmpty()) {
                    predicates.add(cb.or(
                            cb.isNull(root.get("purchaseOrderNo")),
                            cb.not(cb.like(root.get("purchaseOrderNo"), "CHAIN_FROM:%")),
                            root.get("createdBy").in(sharedUserIds)
                    ));
                } else {
                    predicates.add(cb.or(
                            cb.isNull(root.get("purchaseOrderNo")),
                            cb.not(cb.like(root.get("purchaseOrderNo"), "CHAIN_FROM:%"))
                    ));
                }
            }
            
            if (omsOrderNo != null && !omsOrderNo.isEmpty()) {
                predicates.add(cb.like(root.get("omsOrderNo"), "%" + omsOrderNo + "%"));
            }
            if (platformOrderNo != null && !platformOrderNo.isEmpty()) {
                predicates.add(cb.like(root.get("platformOrderNo"), "%" + platformOrderNo + "%"));
            }
            if (partyATitleKeyword != null && !partyATitleKeyword.isBlank()) {
                Predicate partyATitlePredicate = buildPartyATitleKeywordPredicate(root, cb, partyATitleKeyword);
                if (partyATitlePredicate != null) {
                    predicates.add(partyATitlePredicate);
                }
            }
            if (deliveryPartyKeyword != null && !deliveryPartyKeyword.isBlank()) {
                Predicate deliveryPartyPredicate = buildDeliveryPartyKeywordPredicate(root, cb, deliveryPartyKeyword);
                if (deliveryPartyPredicate != null) {
                    predicates.add(deliveryPartyPredicate);
                }
            }
            if (status != null && !status.isEmpty()) {
                List<String> statusList = expandSalesOrderStatusFilter(status);
                if (statusList.size() == 1) {
                    predicates.add(cb.equal(root.get("status"), statusList.get(0)));
                } else {
                    predicates.add(root.get("status").in(statusList));
                }
                if (containsStatusFilter(status, "已开票待结算")) {
                    predicates.add(pendingSettlementRefundPredicate(root, cb));
                }
            }
            if (excludeStatuses != null && !excludeStatuses.isBlank()) {
                List<String> excludedStatusList = java.util.Arrays.stream(excludeStatuses.split(",\\s*"))
                        .map(value -> value == null ? "" : value.trim())
                        .filter(value -> !value.isEmpty())
                        .toList();
                if (!excludedStatusList.isEmpty()) {
                    predicates.add(cb.not(root.get("status").in(excludedStatusList)));
                }
            }
            if (platformRefundStatus != null && !platformRefundStatus.isEmpty()) {
                if (platformRefundStatus.contains(",")) {
                    List<String> list = java.util.Arrays.asList(platformRefundStatus.split(",\\s*"));
                    predicates.add(root.get("platformRefundStatus").in(list));
                } else {
                    predicates.add(cb.equal(root.get("platformRefundStatus"), platformRefundStatus));
                }
            }
            if (offlineSales != null && !offlineSales.isEmpty()) {
                if ("1".equals(offlineSales) || "true".equalsIgnoreCase(offlineSales) || "是".equals(offlineSales)) {
                    predicates.add(cb.and(cb.isNotNull(root.get("offlineSales")), cb.notEqual(root.get("offlineSales"), "")));
                } else if ("0".equals(offlineSales) || "false".equalsIgnoreCase(offlineSales) || "否".equals(offlineSales)) {
                    predicates.add(cb.or(cb.isNull(root.get("offlineSales")), cb.equal(root.get("offlineSales"), "")));
                }
                // 其它非空值（历史误传）不追加条件，等价于「全部」，避免误筛成「无线下销售」
            }
            if (ecommerceSalesName != null && !ecommerceSalesName.isBlank()) {
                String nm = ecommerceSalesName.trim();
                predicates.add(cb.like(root.get("ecommerceSalesName"), "%" + nm + "%"));
            }
            // 需签收单（待妥投）：status=已发货 且 未上传签收单（receiptUrl 为空），与 Dashboard 待妥投统计一致
            if ("1".equals(needReceiptSlip) || "true".equalsIgnoreCase(needReceiptSlip) || "receipt".equalsIgnoreCase(needReceiptSlip)) {
                predicates.add(cb.equal(root.get("status"), "已发货"));
                predicates.add(cb.or(cb.isNull(root.get("receiptUrl")), cb.equal(root.get("receiptUrl"), "")));
            }
            // 签收/妥投细分：与 Dashboard / 定时流转共用同一套谓词
            if (receiptFilter != null && !receiptFilter.isBlank() && !"all".equalsIgnoreCase(receiptFilter.trim())) {
                Specification<SalesOrder> receiptSpec = SalesOrderReceiptFlowHelper.receiptFilterSpec(receiptFilter);
                if (receiptSpec != null) {
                    Predicate receiptPredicate = receiptSpec.toPredicate(root, query, cb);
                    if (receiptPredicate != null) {
                        predicates.add(receiptPredicate);
                    }
                }
            }
            if (erpEntryStatus != null && !erpEntryStatus.isBlank()) {
                predicates.add(cb.equal(root.get("erpEntryStatus"), erpEntryStatus.trim()));
            }
            if (Boolean.TRUE.equals(expectedRefundOverdue)) {
                predicates.add(cb.isNotNull(root.get("expectedRefundDate")));
                predicates.add(cb.lessThan(root.get("expectedRefundDate"), LocalDate.now()));
                predicates.add(cb.or(
                        cb.isNull(root.get("platformRefundStatus")),
                        cb.equal(root.get("platformRefundStatus"), ""),
                        cb.equal(root.get("platformRefundStatus"), "未回款"),
                        cb.equal(root.get("platformRefundStatus"), "部分回款")
                ));
                predicates.add(cb.not(root.get("status").in("已取消", "已退回", "已结算")));
            }

            // 一账户一套数据：销售列表每人只看自己那套
            // 特殊限制：热像科技-财务、热像科技-仓库仅可见甲方抬头为飞础科的订单
            if (isRestrictedHotUser(currentUser)) {
                String partyATitle = getRestrictedPartyATitle();
                Predicate byPartyATitle = cb.like(root.get("partyATitle"), "%" + partyATitle + "%");
                Predicate byPlatformName = cb.like(root.get("platformName"), "%" + partyATitle + "%");
                Predicate byOperationEntity = cb.like(root.get("operationEntityTitle"), "%" + partyATitle + "%");
                predicates.add(cb.or(byPartyATitle, byPlatformName, byOperationEntity));
            }

            boolean isAdmin = currentUser != null && "ROLE_ADMIN".equals(currentUser.getRole());
            // 销售列表「仓库可见范围」：ROLE_WAREHOUSE 或与之前端勾选的 permissions=warehouse 一致
            boolean isWarehouseScopeUser = hasWarehouseScope(currentUser);
            boolean hasAllOrdersPermission = hasAllOrdersScope(currentUser);
            // 仓库权限：不是全量可见，仅可见配置抬头范围内的订单（默认：上海热像科技、飞础科）
            if (isWarehouseScopeUser && !isAdmin && !hasAllOrdersPermission && currentUser != null) {
                List<Predicate> warehouseVisible = new ArrayList<>();
                warehouseVisible.add(buildWarehousePartyATitlePredicate(root, cb));
                if (!warehouseVisibleCreatorIds.isEmpty()) {
                    warehouseVisible.add(root.get("createdBy").in(warehouseVisibleCreatorIds));
                    warehouseVisible.add(root.get("ecommerceSalesId").in(warehouseVisibleCreatorIds));
                }
                predicates.add(cb.or(warehouseVisible.toArray(new Predicate[0])));
            }
            // - 管理员：全量订单
            // - 仓库权限：可见指定交付方
            // - 显式权限 sales_all_orders：全量订单（用于热像/飞础科商务）
            // - 其他用户：只看自己创建，或明确指派给自己的订单
            boolean canSeeAllOrders = isAdmin || isWarehouseScopeUser || hasAllOrdersPermission;
            if (currentUser != null && !canSeeAllOrders) {
                // 用户名作为唯一标识：仅用 username 判断是否为被指派方，联系人和真名可能重复故不参与
                List<Predicate> assigneePredicates = new ArrayList<>();
                if (!sharedUsernames.isEmpty()) assigneePredicates.add(root.get("assignedUsername").in(sharedUsernames));
                List<Predicate> visibilityOr = new ArrayList<>();
                if (!sharedUserIds.isEmpty()) {
                    visibilityOr.add(root.get("createdBy").in(sharedUserIds));
                } else {
                    visibilityOr.add(cb.equal(root.get("createdBy"), currentUser.getId()));
                }
                if (!assigneePredicates.isEmpty()) visibilityOr.add(cb.or(assigneePredicates.toArray(new Predicate[0])));
                predicates.add(cb.or(visibilityOr.toArray(new Predicate[0])));
                // 已退回订单仅对指派方（订单创建人）可见，避免被指派方在销售列表/采购指派列表看到并跳转导致暴露指派方信息
                predicates.add(cb.or(
                    cb.notEqual(root.get("status"), "已退回"),
                    !sharedUserIds.isEmpty() ? root.get("createdBy").in(sharedUserIds) : cb.equal(root.get("createdBy"), currentUser.getId())
                ));
                // 待指派订单仅对创建人（指派方）可见，避免被指派方因「交付方=本公司」等规则看到指派方的待指派列表
                predicates.add(cb.or(
                    cb.notEqual(root.get("status"), "待指派"),
                    !sharedUserIds.isEmpty() ? root.get("createdBy").in(sharedUserIds) : cb.equal(root.get("createdBy"), currentUser.getId())
                ));

                // 被指派方去重：若当前账号已存在对应链式单，则隐藏该主单，避免销售列表出现「主单+链式单」双份展示。
                // 这里用同字段（omsOrderNo）关联，避免与字符串拼接表达式比较导致 collation 冲突。
                if (!sharedUsernames.isEmpty()) {
                    jakarta.persistence.criteria.Subquery<Long> myChainExists = query.subquery(Long.class);
                    var chainRoot = myChainExists.from(SalesOrder.class);
                    myChainExists.select(cb.literal(1L));
                    myChainExists.where(
                            !sharedUserIds.isEmpty() ? chainRoot.get("createdBy").in(sharedUserIds) : cb.equal(chainRoot.get("createdBy"), currentUser.getId()),
                            cb.like(chainRoot.get("purchaseOrderNo"), "CHAIN_FROM:%"),
                            cb.equal(chainRoot.get("omsOrderNo"), root.get("omsOrderNo"))
                    );

                    Predicate isMainAssignedToMe = cb.and(
                            root.get("assignedUsername").in(sharedUsernames),
                            !sharedUserIds.isEmpty() ? cb.not(root.get("createdBy").in(sharedUserIds)) : cb.notEqual(root.get("createdBy"), currentUser.getId()),
                            cb.or(
                                    cb.isNull(root.get("purchaseOrderNo")),
                                    cb.not(cb.like(root.get("purchaseOrderNo"), "CHAIN_FROM:%"))
                            )
                    );
                    predicates.add(cb.not(cb.and(isMainAssignedToMe, cb.exists(myChainExists))));
                }
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).map(order -> {
            SalesOrder sanitized = sanitizeErpEntryVisibility(order, currentUser);
            expectedRefundDateService.populateTransientFields(sanitized);
            return sanitized;
        });
    }

    private List<String> expandSalesOrderStatusFilter(String status) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String raw : status.split(",\\s*")) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            expanded.add(value);
            if ("已开票待结算".equals(value)) {
                expanded.add("已开票");
            } else if ("已结算".equals(value)) {
                expanded.add("已回款");
            }
        }
        return new ArrayList<>(expanded);
    }

    private boolean containsStatusFilter(String status, String expected) {
        if (status == null || status.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        return Arrays.stream(status.split(",\\s*"))
                .map(value -> value == null ? "" : value.trim())
                .anyMatch(expected::equals);
    }

    private Predicate pendingSettlementRefundPredicate(Root<SalesOrder> root, CriteriaBuilder cb) {
        return cb.or(
                cb.isNull(root.get("platformRefundStatus")),
                cb.equal(root.get("platformRefundStatus"), ""),
                cb.equal(root.get("platformRefundStatus"), "未回款"),
                cb.equal(root.get("platformRefundStatus"), "部分回款")
        );
    }

    /** 判断用户是否拥有某权限（permissions 逗号分隔，包含即视为拥有） */
    private boolean hasPermission(com.oms.entity.User user, String permission) {
        if (user == null || permission == null || permission.isBlank()) return false;
        String perms = user.getPermissions();
        if (perms == null || perms.isBlank()) return false;
        for (String p : perms.split(",")) {
            if (permission.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    /** 仓库角色 或 勾选「仓库（可见全量销售订单便于发货）」权限，销售列表走同一套可见范围 */
    private boolean hasWarehouseScope(User user) {
        if (user == null) return false;
        return "ROLE_WAREHOUSE".equals(user.getRole()) || hasPermission(user, "warehouse");
    }

    /** 全量订单查看与处理（商务权限）：仅认显式权限，不再按公司抬头自动放开。 */
    private boolean hasAllOrdersScope(User user) {
        return hasPermission(user, "sales_all_orders");
    }

    private List<String> resolveReceiptUploadAtMobiles() {
        LinkedHashSet<String> atPhones = new LinkedHashSet<>();
        for (String realName : splitCommaSeparatedValues(receiptUploadAtRealNames)) {
            List<User> matchedUsers = userRepository.findByRealName(realName);
            if (matchedUsers == null) continue;
            for (User matched : matchedUsers) {
                if (matched == null || !Boolean.TRUE.equals(matched.getEnabled())) continue;
                String phone = matched.getPhone();
                if (phone != null && phone.replaceAll("\\D", "").length() >= 6) {
                    atPhones.add(phone.trim());
                }
            }
        }
        return new ArrayList<>(atPhones);
    }

    private boolean isRestrictedHotUser(User user) {
        if (user == null) return false;
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String realName = user.getRealName() == null ? "" : user.getRealName().trim();
        if (restrictedUsernames == null || restrictedUsernames.isBlank()) return false;
        for (String item : restrictedUsernames.split(",")) {
            String identity = item == null ? "" : item.trim();
            if (identity.isBlank()) continue;
            if (identity.equals(username) || identity.equals(realName)) return true;
        }
        return false;
    }

    private String getRestrictedPartyATitle() {
        String configured = restrictedPartyATitle == null ? "" : restrictedPartyATitle.trim();
        return configured.isBlank() ? "飞础科智慧科技（上海）有限公司" : configured;
    }

    private List<String> getWarehouseVisiblePartyATitles() {
        if (warehouseVisiblePartyATitles == null || warehouseVisiblePartyATitles.isBlank()) {
            return List.of("上海热像科技股份有限公司", "飞础科智慧科技（上海）有限公司");
        }
        return Arrays.stream(warehouseVisiblePartyATitles.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private Set<Long> getWarehouseVisibleCreatorIds() {
        List<String> allowedTitles = getWarehouseVisiblePartyATitles();
        if (allowedTitles.isEmpty()) return Set.of();
        Set<Long> ids = new HashSet<>();
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (user == null || user.getId() == null) continue;
            String companyTitle = user.getCompanyTitle() == null ? "" : user.getCompanyTitle().trim();
            if (companyTitle.isBlank()) continue;
            for (String allowed : allowedTitles) {
                if (allowed == null || allowed.isBlank()) continue;
                if (companyTitle.contains(allowed) || allowed.contains(companyTitle)) {
                    ids.add(user.getId());
                    break;
                }
            }
        }
        return ids;
    }

    private Predicate buildWarehousePartyATitlePredicate(Root<SalesOrder> root, CriteriaBuilder cb) {
        List<String> allowedTitles = getWarehouseVisiblePartyATitles();
        if (allowedTitles.isEmpty()) return cb.disjunction();
        Expression<String> partyATitle = root.get("partyATitle");
        Expression<String> platformName = root.get("platformName");
        Expression<String> operationEntityTitle = root.get("operationEntityTitle");
        List<Predicate> titlePredicates = new ArrayList<>();
        for (String companyTitle : allowedTitles) {
            String escaped = companyTitle.replace("%", "\\%").replace("_", "\\_");
            Predicate byPartyATitle = cb.and(
                    cb.isNotNull(partyATitle),
                    cb.notEqual(partyATitle, ""),
                    cb.like(partyATitle, "%" + escaped + "%")
            );
            Predicate byPlatformName = cb.and(
                    cb.isNotNull(platformName),
                    cb.notEqual(platformName, ""),
                    cb.like(platformName, "%" + escaped + "%")
            );
            Predicate byOperationEntity = cb.and(
                    cb.isNotNull(operationEntityTitle),
                    cb.notEqual(operationEntityTitle, ""),
                    cb.like(operationEntityTitle, "%" + escaped + "%")
            );
            Predicate orderTitleContainsConfigured = cb.or(byPartyATitle, byPlatformName, byOperationEntity);
            Predicate configuredContainsOrderTitle = cb.or(
                    cb.and(
                            cb.isNotNull(partyATitle),
                            cb.notEqual(partyATitle, ""),
                            cb.like(cb.literal(companyTitle), cb.concat(cb.concat(cb.literal("%"), partyATitle), cb.literal("%")))
                    ),
                    cb.and(
                            cb.isNotNull(platformName),
                            cb.notEqual(platformName, ""),
                            cb.like(cb.literal(companyTitle), cb.concat(cb.concat(cb.literal("%"), platformName), cb.literal("%")))
                    ),
                    cb.and(
                            cb.isNotNull(operationEntityTitle),
                            cb.notEqual(operationEntityTitle, ""),
                            cb.like(cb.literal(companyTitle), cb.concat(cb.concat(cb.literal("%"), operationEntityTitle), cb.literal("%")))
                    )
            );
            titlePredicates.add(cb.or(orderTitleContainsConfigured, configuredContainsOrderTitle));
        }
        return cb.or(titlePredicates.toArray(new Predicate[0]));
    }

    private Predicate buildPartyATitleKeywordPredicate(Root<SalesOrder> root, CriteriaBuilder cb, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return null;
        }
        Expression<String> partyATitle = root.get("partyATitle");
        Expression<String> platformName = root.get("platformName");
        Expression<String> operationEntityTitle = root.get("operationEntityTitle");
        return cb.or(
                cb.and(cb.isNotNull(partyATitle), cb.notEqual(partyATitle, ""), cb.like(partyATitle, "%" + normalizedKeyword + "%")),
                cb.and(cb.isNotNull(platformName), cb.notEqual(platformName, ""), cb.like(platformName, "%" + normalizedKeyword + "%")),
                cb.and(cb.isNotNull(operationEntityTitle), cb.notEqual(operationEntityTitle, ""), cb.like(operationEntityTitle, "%" + normalizedKeyword + "%"))
        );
    }

    private Predicate buildDeliveryPartyKeywordPredicate(Root<SalesOrder> root, CriteriaBuilder cb, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return null;
        }
        Expression<String> deliveryParty = root.get("deliveryParty");
        Expression<String> shippingParty = root.get("shippingParty");
        return cb.or(
                cb.and(cb.isNotNull(deliveryParty), cb.notEqual(deliveryParty, ""), cb.like(deliveryParty, "%" + normalizedKeyword + "%")),
                cb.and(cb.isNotNull(shippingParty), cb.notEqual(shippingParty, ""), cb.like(shippingParty, "%" + normalizedKeyword + "%"))
        );
    }

    private boolean isOrderVisibleToWarehouse(SalesOrder order) {
        if (order == null) return false;
        String realPartyA = order.getPartyATitle() == null ? "" : order.getPartyATitle().trim();
        String platformName = order.getPlatformName() == null ? "" : order.getPlatformName().trim();
        String operationEntityTitle = order.getOperationEntityTitle() == null ? "" : order.getOperationEntityTitle().trim();
        for (String companyTitle : getWarehouseVisiblePartyATitles()) {
            if (companyTitle.isBlank()) continue;
            if ((!realPartyA.isBlank() && (realPartyA.contains(companyTitle) || companyTitle.contains(realPartyA)))
                    || (!platformName.isBlank() && (platformName.contains(companyTitle) || companyTitle.contains(platformName)))
                    || (!operationEntityTitle.isBlank() && (operationEntityTitle.contains(companyTitle) || companyTitle.contains(operationEntityTitle)))) {
                return true;
            }
        }
        if (order.getCreatedBy() != null) {
            User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
            if (creator != null) {
                String creatorCompany = creator.getCompanyTitle() == null ? "" : creator.getCompanyTitle().trim();
                for (String companyTitle : getWarehouseVisiblePartyATitles()) {
                    if (!creatorCompany.isBlank() && !companyTitle.isBlank()
                            && (creatorCompany.contains(companyTitle) || companyTitle.contains(creatorCompany))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean canCurrentUserViewOrder(SalesOrder order) {
        String currentUsername = SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (currentUsername == null || currentUsername.isBlank() || order == null) return false;
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        if (currentUser == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(currentUser.getRole())) {
            return true;
        }
        if (currentUser != null
                && (hasWarehouseScope(currentUser) || hasAllOrdersScope(currentUser))) {
            if (hasAllOrdersScope(currentUser)) {
                return true;
            }
            return isOrderVisibleToWarehouse(order);
        }
        Set<String> sharedUsernames = new LinkedHashSet<>(subjectAccountGroupService.manuallySharedUsernames(currentUsername));
        sharedUsernames.add(currentUsername.trim());
        Set<Long> sharedUserIds = new LinkedHashSet<>(subjectAccountGroupService.manuallySharedUserIds(currentUsername));
        if (currentUser.getId() != null) {
            sharedUserIds.add(currentUser.getId());
        }
        boolean creatorVisible = order.getCreatedBy() != null && sharedUserIds.contains(order.getCreatedBy());
        boolean assignedVisible = order.getAssignedUsername() != null && sharedUsernames.contains(order.getAssignedUsername().trim());
        if (!creatorVisible && !assignedVisible) {
            return false;
        }
        if (!isRestrictedHotUser(currentUser)) {
            return true;
        }
        String partyATitle = getRestrictedPartyATitle();
        String realPartyA = order.getPartyATitle() == null ? "" : order.getPartyATitle().trim();
        String platformName = order.getPlatformName() == null ? "" : order.getPlatformName().trim();
        String operationEntityTitle = order.getOperationEntityTitle() == null ? "" : order.getOperationEntityTitle().trim();
        return realPartyA.contains(partyATitle) || platformName.contains(partyATitle) || operationEntityTitle.contains(partyATitle);
    }

    /**
     * 合同文件预览：path 形如 /uploads/yyyyMMdd/xxx.pdf 已与某销售订单 contractUrl 关联，且当前用户对该订单有查看权限时允许。
     * 用于修复「业务员公司抬头未维护但本人可查看自己订单」、以及 JWT 生效后的鉴权补充。
     */
    public boolean currentUserMayPreviewContractUploadPath(User currentUser, String pathParam) {
        if (currentUser == null || pathParam == null || pathParam.isBlank()) {
            return false;
        }
        String path = pathParam.trim();
        if (!path.startsWith("/uploads/") || path.contains("..")) {
            return false;
        }
        Set<SalesOrder> candidates = new LinkedHashSet<>();
        candidates.addAll(salesOrderRepository.findByContractUrl(path));
        if (path.startsWith("/")) {
            candidates.addAll(salesOrderRepository.findByContractUrl(path.substring(1)));
        }
        String relative = path.length() > "/uploads/".length()
                ? path.substring("/uploads/".length())
                : "";
        if (!relative.isBlank()) {
            candidates.addAll(salesOrderRepository.findByContractUrlEndingWith(relative));
            int slash = relative.lastIndexOf('/');
            if (slash >= 0 && slash < relative.length() - 1) {
                String filePart = relative.substring(slash + 1);
                candidates.addAll(salesOrderRepository.findByContractUrlEndingWith(filePart));
            }
        }
        for (SalesOrder o : candidates) {
            if (o != null && canCurrentUserViewOrder(o)) {
                return true;
            }
        }
        return false;
    }

    public boolean canCurrentUserViewErpEntryScreenshot(SalesOrder order) {
        return canCurrentUserViewErpEntryScreenshot(getCurrentUser(), order);
    }

    public boolean currentUserMayPreviewErpEntryScreenshotPath(User currentUser, String rawPath) {
        String path = normalizeUploadPath(rawPath);
        if (currentUser == null || path == null) {
            return false;
        }
        List<SalesOrder> orders = salesOrderRepository.findAll();
        for (SalesOrder order : orders) {
            String screenshotPath = normalizeUploadPath(order.getErpEntryScreenshotUrl());
            if (path.equals(screenshotPath) && canCurrentUserViewErpEntryScreenshot(currentUser, order)) {
                return true;
            }
        }
        return false;
    }

    public boolean canCurrentUserMaintainErpEntry(SalesOrder order) {
        return canCurrentUserMaintainErpEntry(getCurrentUser(), order);
    }

    public SalesOrder sanitizeErpEntryVisibility(SalesOrder order) {
        return sanitizeErpEntryVisibility(order, getCurrentUser());
    }

    @Transactional
    public SalesOrder updateErpEntry(Long id, String screenshotUrl, String operator, LocalDateTime entryTime) {
        User currentUser = getCurrentUser();
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("销售订单不存在"));
        if (!canCurrentUserMaintainErpEntry(currentUser, order)) {
            throw new RuntimeException("无权维护该订单的商务ERP录单信息");
        }

        String normalizedOperator = defaultOperatorName(currentUser, operator);
        LocalDateTime normalizedTime = entryTime != null ? entryTime : LocalDateTime.now();
        String normalizedScreenshot = normalizeBlankToNull(screenshotUrl);
        String normalizedStatus = normalizedScreenshot != null ? "已录单" : "待系统录单";

        syncSalesErpEntryByOmsOrderNo(order.getOmsOrderNo(), normalizedStatus, normalizedScreenshot, normalizedOperator, normalizedTime);
        return salesOrderRepository.findById(id)
                .map(saved -> sanitizeErpEntryVisibility(saved, currentUser))
                .orElseGet(() -> sanitizeErpEntryVisibility(order, currentUser));
    }

    @Transactional
    public void markErpEntryPendingForOmsOrder(String omsOrderNo) {
        String oms = normalizeBlankToNull(omsOrderNo);
        if (oms == null) {
            return;
        }
        List<SalesOrder> salesOrders = salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(oms);
        for (SalesOrder salesOrder : salesOrders) {
            if (!"已录单".equals(salesOrder.getErpEntryStatus())) {
                salesOrder.setErpEntryStatus("待系统录单");
                salesOrderRepository.save(salesOrder);
            }
        }
        List<com.oms.entity.PurchaseOrder> purchaseOrders = purchaseOrderRepository.findByOmsOrderNo(oms);
        for (com.oms.entity.PurchaseOrder purchaseOrder : purchaseOrders) {
            if (!"已录单".equals(purchaseOrder.getErpEntryStatus())) {
                purchaseOrder.setErpEntryStatus("待系统录单");
                purchaseOrderRepository.save(purchaseOrder);
            }
        }
    }

    private void syncSalesErpEntryByOmsOrderNo(String omsOrderNo, String status, String screenshotUrl,
                                               String operator, LocalDateTime entryTime) {
        String oms = normalizeBlankToNull(omsOrderNo);
        if (oms == null) {
            return;
        }
        List<SalesOrder> salesOrders = salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(oms);
        for (SalesOrder salesOrder : salesOrders) {
            applyErpEntry(salesOrder, status, screenshotUrl, operator, entryTime);
            salesOrderRepository.save(salesOrder);
        }
    }

    private void applyErpEntry(SalesOrder salesOrder, String status, String screenshotUrl,
                               String operator, LocalDateTime entryTime) {
        salesOrder.setErpEntryStatus(status);
        salesOrder.setErpEntryScreenshotUrl(screenshotUrl);
        salesOrder.setErpEntryOperator(operator);
        salesOrder.setErpEntryTime(entryTime);
    }

    private SalesOrder sanitizeErpEntryVisibility(SalesOrder order, User currentUser) {
        if (order == null) {
            return null;
        }
        boolean canEdit = canCurrentUserMaintainErpEntry(currentUser, order);
        boolean canPreview = canCurrentUserViewErpEntryScreenshot(currentUser, order);
        order.setErpEntryCanEdit(canEdit);
        order.setErpEntryCanPreviewScreenshot(canPreview);
        if (!canPreview) {
            order.setErpEntryScreenshotUrl(null);
        }
        return order;
    }

    private boolean canCurrentUserMaintainErpEntry(User currentUser, SalesOrder order) {
        if (order == null || currentUser == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(currentUser.getRole())) {
            return true;
        }
        if (isRexiangCompany(currentUser)) {
            // 上海热像全员可查看/维护 ERP 录单截图
            return true;
        }
        if (isFeichukeCompany(currentUser)) {
            return isRelatedFeichukeSalesOwner(currentUser, order);
        }
        return false;
    }

    private boolean isRelatedFeichukeSalesOwner(User currentUser, SalesOrder order) {
        if (currentUser == null || order == null) {
            return false;
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo != null) {
            return salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(omsOrderNo).stream()
                    .anyMatch(item -> matchesErpSalesOwner(currentUser, item.getCreatedBy(), item.getEcommerceSalesName()));
        }
        return matchesErpSalesOwner(currentUser, order.getCreatedBy(), order.getEcommerceSalesName());
    }

    private boolean matchesErpSalesOwner(User currentUser, Long createdBy, String ecommerceSalesName) {
        if (currentUser == null) {
            return false;
        }
        if (createdBy != null && createdBy.equals(currentUser.getId())) {
            return true;
        }
        String currentRealName = normalizeBlankToNull(currentUser.getRealName());
        String currentUsername = normalizeBlankToNull(currentUser.getUsername());
        String salesName = normalizeBlankToNull(ecommerceSalesName);
        if (salesName == null) {
            return false;
        }
        return salesName.equals(currentRealName) || salesName.equals(currentUsername);
    }

    private String normalizeUploadPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String path = rawPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                path = java.net.URI.create(path).getPath();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (path.startsWith("uploads/")) {
            path = "/" + path;
        }
        return path;
    }

    private boolean canCurrentUserViewErpEntryScreenshot(User currentUser, SalesOrder order) {
        return canCurrentUserMaintainErpEntry(currentUser, order);
    }

    private boolean isFeichukeCompany(User user) {
        return user != null && user.getCompanyTitle() != null && user.getCompanyTitle().contains("飞础科智慧科技（上海）有限公司");
    }

    private boolean isRexiangCompany(User user) {
        return user != null && user.getCompanyTitle() != null && user.getCompanyTitle().contains("上海热像科技股份有限公司");
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> splitCommaSeparatedValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private String defaultOperatorName(User currentUser, String operator) {
        String normalized = normalizeBlankToNull(operator);
        if (normalized != null) {
            return normalized;
        }
        if (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank()) {
            return currentUser.getRealName().trim();
        }
        return currentUser != null ? currentUser.getUsername() : "";
    }

    private User getCurrentUser() {
        String currentUsername = SecurityContextHolder.getContext() != null
                && SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (currentUsername == null || currentUsername.isBlank() || "anonymousUser".equalsIgnoreCase(currentUsername)) {
            return null;
        }
        return userRepository.findByUsername(currentUsername).orElse(null);
    }

    public List<SalesOrder> getAllOrders() {
        return salesOrderRepository.findAll();
    }

    /**
     * 生成工业电商销售订单号
     * 逻辑：(D?) + YYMMDD + 真实姓名首字母 + 01 (两位流水)
     */
    public String generateOmsOrderNo(Long userId, String orderType) {
        return generateOmsOrderNo(userId, orderType, LocalDate.now());
    }

    public String generateOmsOrderNo(Long userId, String orderType, LocalDate orderDate) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        String realName = user.getRealName();
        LocalDate effectiveDate = orderDate != null ? orderDate : LocalDate.now();
        
        boolean isThirdPartyOrder = orderType != null && orderType.trim().contains("第三方");
        // 1. 日期段：第三方用 YYMMDD（D260317...），自营用 YYYYMMDD（20260317...）
        String datePart = isThirdPartyOrder
                ? effectiveDate.format(DateTimeFormatter.ofPattern("yyMMdd"))
                : effectiveDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 2. 获取姓名首字母 (简单处理：取每个字符的拼音首字母，这里简化为取每个字符的前缀)
        // 实际项目中建议使用 Pinyin4j 等库，这里暂用简单逻辑
        String nameInitial = getInitials(realName);
        
        // 3. 构造前缀：第三方订单加 D；自营不加前缀。
        // 第三方与自营通过不同前缀独立计数：D260317SM01 / 260317SM01
        String prefix = (isThirdPartyOrder ? "D" : "") + datePart + nameInitial;
        
        // 4. 查询当日该前缀的最大流水号
        return salesOrderRepository.findFirstByOmsOrderNoStartingWithOrderByOmsOrderNoDesc(prefix)
                .map(order -> {
                    String lastNo = order.getOmsOrderNo();
                    String seqStr = lastNo.substring(lastNo.length() - 2);
                    int seq = Integer.parseInt(seqStr) + 1;
                    return prefix + String.format("%02d", seq);
                })
                .orElse(prefix + "01");
    }

    public String getCodeInitials(String name) {
        return getInitials(name);
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "XX";

        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (Pinyin.isChinese(c)) {
                String py = Pinyin.toPinyin(String.valueOf(c), "");
                if (py != null && !py.isBlank()) {
                    sb.append(Character.toUpperCase(py.charAt(0)));
                }
            } else if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
            }
            // 与前端保持一致：最多取 3 位姓名首字母（例如 王潇龙 -> WXL）
            if (sb.length() >= 3) break;
        }
        return sb.length() > 0 ? sb.toString() : "JS";
    }

    /** 仅更新签收单相关字段（签收单URL、签收时间、签收状态） */
    @Transactional
    public SalesOrder updateReceipt(Long id, String receiptUrl, LocalDateTime receiptTime, String receiptStatus) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        String oldReceiptUrl = order.getReceiptUrl();
        String oldReceiptStatus = order.getReceiptStatus();
        LocalDateTime oldReceiptTime = order.getReceiptTime();
        if (receiptUrl != null) order.setReceiptUrl(receiptUrl);
        if (receiptTime != null) order.setReceiptTime(receiptTime);
        if (receiptStatus != null && !receiptStatus.isBlank()) order.setReceiptStatus(receiptStatus);
        String targetStage = SalesOrderReceiptFlowHelper.determineReceiptFlowStage(order);
        if (targetStage != null) {
            order.setReceiptFlowStage(SalesOrderReceiptFlowHelper.advanceStage(order.getReceiptFlowStage(), targetStage));
        }
        SalesOrder saved = salesOrderRepository.save(order);
        boolean newlyUploaded = (oldReceiptUrl == null || oldReceiptUrl.isBlank())
                && saved.getReceiptUrl() != null && !saved.getReceiptUrl().isBlank();
        boolean statusChanged = saved.getReceiptStatus() != null
                && !saved.getReceiptStatus().isBlank()
                && !saved.getReceiptStatus().equals(oldReceiptStatus);
        boolean timeChanged = saved.getReceiptTime() != null
                && !saved.getReceiptTime().equals(oldReceiptTime);
        if (newlyUploaded || statusChanged || timeChanged) {
            try {
                dingTalkService.sendReceiptUploadNotification(
                        saved.getOmsOrderNo(),
                        "已上传".equals(saved.getReceiptStatus()) ? "签收单已上传" : saved.getReceiptStatus(),
                        saved.getReceiptTime() != null ? saved.getReceiptTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "",
                        resolveReceiptUploadAtMobiles()
                );
            } catch (Exception e) {
                System.err.println("发送签收单上传钉钉通知失败: " + e.getMessage());
            }
        }
        return saved;
    }

    /** 仅更新物流信息（收货人、电话、收货地址），供发货弹窗同步到链式单前回写主单 */
    @Transactional
    public SalesOrder updateReceiver(Long id, String receiverName, String receiverPhone, String receiverAddress) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        if (receiverName != null) order.setReceiverName(receiverName);
        if (receiverPhone != null) order.setReceiverPhone(receiverPhone);
        if (receiverAddress != null) order.setReceiverAddress(receiverAddress);
        return salesOrderRepository.save(order);
    }

    @Transactional
    public SalesOrder saveOrder(SalesOrder order) {
        if (order == null) return null;
        System.out.println("saveOrder called with order: " + order);
        System.out.println("Order ID: " + order.getId());
        System.out.println("Order OMS No: " + order.getOmsOrderNo());

        // 兼容旧前端：未传真实甲方时，默认用 platformName 回填
        if ((order.getPartyATitle() == null || order.getPartyATitle().isBlank())
                && order.getPlatformName() != null && !order.getPlatformName().isBlank()) {
            order.setPartyATitle(order.getPlatformName().trim());
        }
        // 工业电商新建订单：顶层客户名=平台名（仅本组织合同用于结算方式及期限；链式订单不继承）
        if (order.getId() == null && (order.getTopLevelCustomerName() == null || order.getTopLevelCustomerName().isBlank())
                && order.getPartyATitle() != null && !order.getPartyATitle().isBlank()) {
            order.setTopLevelCustomerName(order.getPartyATitle());
        }
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        // 如果是更新订单，检查权限并处理字段更新
        if (order.getId() != null) {
            SalesOrder existingOrder = salesOrderRepository.findById(order.getId()).orElse(null);
            if (existingOrder != null) {
                // 检查权限：管理员、创建者或被指派用户可以编辑
                if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                    boolean isCreator = existingOrder.getCreatedBy() != null && existingOrder.getCreatedBy().equals(currentUser.getId());
                    boolean isAssigned = existingOrder.getAssignedUsername() != null && 
                                        existingOrder.getAssignedUsername().equals(currentUsername);
                    boolean canManageAll = hasWarehouseScope(currentUser) || hasAllOrdersScope(currentUser);
                    if (!isCreator && !isAssigned && !canManageAll) {
                        throw new RuntimeException("无权修改该订单");
                    }
                }
                
                // 保留核心字段不被覆盖
                order.setCreatedBy(existingOrder.getCreatedBy());
                order.setCreateTime(existingOrder.getCreateTime());
                // 工业电商销售订单号：编辑时若请求中带了 omsOrderNo 则允许修改并持久化，保证列表与编辑框一致
                if (order.getOmsOrderNo() != null && !order.getOmsOrderNo().isBlank()) {
                    order.setOmsOrderNo(order.getOmsOrderNo().trim());
                } else {
                    order.setOmsOrderNo(existingOrder.getOmsOrderNo());
                }
                // 编辑接口未显式传 status 时，沿用原状态，避免触发数据库非空约束
                if (order.getStatus() == null || order.getStatus().isBlank()) {
                    order.setStatus(existingOrder.getStatus());
                }
                
                // 保留指派信息（除非显式更新）
                if (order.getAssignedUsername() == null && existingOrder.getAssignedUsername() != null) {
                    order.setAssignedUsername(existingOrder.getAssignedUsername());
                    order.setAssignTime(existingOrder.getAssignTime());
                }
                
                // 保留业务员信息（除非显式更新）
                if (order.getEcommerceSalesId() == null && existingOrder.getEcommerceSalesId() != null) {
                    order.setEcommerceSalesId(existingOrder.getEcommerceSalesId());
                    order.setEcommerceSalesName(existingOrder.getEcommerceSalesName());
                }
                
                // 保留订单详情（除非显式更新）
                if (order.getOrderDetails() == null && existingOrder.getOrderDetails() != null) {
                    order.setOrderDetails(existingOrder.getOrderDetails());
                }
                if (order.getMasterId() == null && existingOrder.getMasterId() != null) {
                    order.setMasterId(existingOrder.getMasterId());
                }
                if (order.getAllocationId() == null && existingOrder.getAllocationId() != null) {
                    order.setAllocationId(existingOrder.getAllocationId());
                }
                if (order.getLineNo() == null && existingOrder.getLineNo() != null) {
                    order.setLineNo(existingOrder.getLineNo());
                }
                if ((order.getLineStatus() == null || order.getLineStatus().isBlank()) && existingOrder.getLineStatus() != null) {
                    order.setLineStatus(existingOrder.getLineStatus());
                }
                if (order.getIsMasterPrimaryLine() == null && existingOrder.getIsMasterPrimaryLine() != null) {
                    order.setIsMasterPrimaryLine(existingOrder.getIsMasterPrimaryLine());
                }
                if (order.getDeliveryMethod() == null && existingOrder.getDeliveryMethod() != null) {
                    order.setDeliveryMethod(existingOrder.getDeliveryMethod());
                }
                if (order.getReceiptFlowStage() == null && existingOrder.getReceiptFlowStage() != null) {
                    order.setReceiptFlowStage(existingOrder.getReceiptFlowStage());
                }
                if (order.getMotherDeliveredAt() == null && existingOrder.getMotherDeliveredAt() != null) {
                    order.setMotherDeliveredAt(existingOrder.getMotherDeliveredAt());
                }
                if (order.getReturnDeliveredAt() == null && existingOrder.getReturnDeliveredAt() != null) {
                    order.setReturnDeliveredAt(existingOrder.getReturnDeliveredAt());
                }
                // 保留发货要求相关字段（编辑订单接口通常不传这些字段，避免误清空导致列表“发货要求”消失）。
                if (order.getDeliveryNoteUrl() == null && existingOrder.getDeliveryNoteUrl() != null) {
                    order.setDeliveryNoteUrl(existingOrder.getDeliveryNoteUrl());
                }
                if (order.getBoxLabelUrls() == null && existingOrder.getBoxLabelUrls() != null) {
                    order.setBoxLabelUrls(existingOrder.getBoxLabelUrls());
                }
                if (order.getDeliveryNotePrintQuantity() == null && existingOrder.getDeliveryNotePrintQuantity() != null) {
                    order.setDeliveryNotePrintQuantity(existingOrder.getDeliveryNotePrintQuantity());
                }
                if (order.getForbiddenCouriers() == null && existingOrder.getForbiddenCouriers() != null) {
                    order.setForbiddenCouriers(existingOrder.getForbiddenCouriers());
                }
                if (order.getPrintBoxLabel() == null && existingOrder.getPrintBoxLabel() != null) {
                    order.setPrintBoxLabel(existingOrder.getPrintBoxLabel());
                }
                if (order.getPrintBarcode128() == null && existingOrder.getPrintBarcode128() != null) {
                    order.setPrintBarcode128(existingOrder.getPrintBarcode128());
                }
                if (order.getSnCode() == null && existingOrder.getSnCode() != null) {
                    order.setSnCode(existingOrder.getSnCode());
                }
                
                // 添加日志记录更新
                System.out.println("Updating order, deliveryPartyPurchasePrice: " + order.getDeliveryPartyPurchasePrice());
                System.out.println("Updating order, deductionRate: " + order.getDeductionRate());
            }
        }
        
        // 如果是新订单，设置创建用户ID
        if (order.getId() == null && currentUser != null) {
            order.setCreatedBy(currentUser.getId());
        }
        
        // 新建订单优先沿用前端已生成/用户已确认的 OMS 订单号，避免弹窗显示与落库结果不一致。
        if (order.getId() == null) {
            if (order.getOmsOrderNo() != null && !order.getOmsOrderNo().isBlank()) {
                order.setOmsOrderNo(order.getOmsOrderNo().trim());
            } else if (order.getEcommerceSalesId() != null) {
                order.setOmsOrderNo(generateOmsOrderNo(order.getEcommerceSalesId(), order.getOrderType()));
            } else {
                // 兜底：既没有前端号也没有业务员时，临时号仅用于避免入库失败。
                order.setOmsOrderNo("TEMP_" + System.currentTimeMillis());
            }
            // 新建销售订单统一从待指派开始，避免旧字段/展示占位值把新单带偏出待指派列表。
            if (order.getStatus() == null || order.getStatus().isBlank() || "待审核".equals(order.getStatus().trim())) {
                order.setStatus("待指派");
            }
        }
        
        // 自动设置业务员姓名（仅在新建时或ecommerceSalesId改变时）
        if (order.getEcommerceSalesId() != null) {
            User user = userRepository.findById(order.getEcommerceSalesId()).orElse(null);
            if (user != null) {
                order.setEcommerceSalesName(user.getRealName());
            }
        }
        
        // 自动计算含税总价（仅在新建时或价格/数量改变时）
        if (order.getTaxIncludedPrice() != null && order.getQuantity() != null) {
            order.setTaxIncludedTotal(order.getTaxIncludedPrice().multiply(new java.math.BigDecimal(order.getQuantity())));
            // 设置amount字段，保持兼容
            order.setAmount(order.getTaxIncludedTotal());
            
            // 仅当请求未显式传交付方采购价时，才按扣点自动计算（指派/转派时前端会传 3550 等，不得覆盖）
            if (order.getDeliveryPartyPurchasePrice() == null && order.getDeductionRate() != null) {
                try {
                    java.math.BigDecimal rate = order.getDeductionRate().divide(new java.math.BigDecimal("100"));
                    java.math.BigDecimal purchasePrice = order.getTaxIncludedTotal().multiply(java.math.BigDecimal.ONE.subtract(rate));
                    order.setDeliveryPartyPurchasePrice(purchasePrice);
                } catch (Exception e) {
                    System.err.println("Error calculating delivery party purchase price: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else if (order.getId() == null) {
            // 如果是新订单且没有价格和数量，设置默认值
            order.setAmount(java.math.BigDecimal.ZERO);
            order.setTaxIncludedTotal(java.math.BigDecimal.ZERO);
        }

        expectedRefundDateService.refreshExpectedRefundFields(order);
        
        System.out.println("Saving order to database...");
        SalesOrder savedOrder = salesOrderRepository.save(order);
        savedOrder = salesOrderMasterService.ensureAnchorsForOrder(savedOrder, currentUser);
        System.out.println("Order saved successfully, ID: " + savedOrder.getId());
        return savedOrder;
    }

    public void applySettlementAnchorDates(SalesOrder order, String prevPlatformReconciliationNo, String prevInvoiceNumber) {
        if (order == null) {
            return;
        }
        String oldPrn = trimToEmpty(prevPlatformReconciliationNo);
        String newPrn = trimToEmpty(order.getPlatformReconciliationNo());
        if (!newPrn.isEmpty() && oldPrn.isEmpty() && order.getReconciliationDate() == null) {
            order.setReconciliationDate(LocalDate.now());
        }

        String oldInv = trimToEmpty(prevInvoiceNumber);
        String newInv = trimToEmpty(order.getInvoiceNumber());
        if (!newInv.isEmpty() && oldInv.isEmpty() && order.getInvoiceIssuedDate() == null) {
            order.setInvoiceIssuedDate(LocalDate.now());
        }
    }

    public void ensureSettlementDocumentNumbers(SalesOrder order) {
        if (order == null) {
            return;
        }
        String invoiceNo = normalizeBlankToNull(order.getInvoiceNumber());
        String reconciliationNo = normalizeBlankToNull(order.getPlatformReconciliationNo());
        if (invoiceNo != null && reconciliationNo == null) {
            order.setPlatformReconciliationNo(buildAutoFinanceDocumentNo(order, "SR"));
        }

        String refundStatus = normalizeBlankToNull(order.getPlatformRefundStatus());
        String settlementNo = normalizeBlankToNull(order.getSettlementNo());
        if (refundStatus != null && settlementNo == null) {
            order.setSettlementNo(buildAutoFinanceDocumentNo(order, "SET"));
        }
    }

    private String buildAutoFinanceDocumentNo(SalesOrder order, String prefix) {
        String base = normalizeBlankToNull(order.getOmsOrderNo());
        if (base == null) {
            base = normalizeBlankToNull(order.getPlatformOrderNo());
        }
        if (base == null && order.getId() != null) {
            base = "ORDER-" + order.getId();
        }
        if (base == null) {
            base = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis();
        }
        base = base.replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("-{2,}", "-");
        base = base.replaceAll("^-+", "").replaceAll("-+$", "");
        if (base.isEmpty()) {
            base = String.valueOf(System.currentTimeMillis());
        }
        return prefix + "-" + base;
    }

    private boolean reconcileSettlementStatus(SalesOrder order) {
        if (order == null) {
            return false;
        }
        String currentStatus = trimToEmpty(order.getStatus());
        if (currentStatus.isEmpty() || "已退回".equals(currentStatus) || "已取消".equals(currentStatus)) {
            return false;
        }

        String targetStatus = null;
        String refundStatus = trimToEmpty(order.getPlatformRefundStatus());
        String invoiceNo = trimToEmpty(order.getInvoiceNumber());
        String reconciliationNo = trimToEmpty(order.getPlatformReconciliationNo());

        if ("已回款".equals(refundStatus)) {
            targetStatus = "已结算";
        } else if (!invoiceNo.isEmpty()) {
            targetStatus = "已开票待结算";
        } else if (!reconciliationNo.isEmpty()) {
            targetStatus = "已对账未开票";
        }

        if (targetStatus == null || targetStatus.equals(currentStatus)) {
            return false;
        }
        order.setStatus(targetStatus);
        return true;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullToDash(String s) {
        if (s == null || s.isBlank()) {
            return "-";
        }
        return s.trim();
    }

    private String resolveNotifyPartyATitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        return nullToDash(firstNonBlank(
                normalizeBlankToNull(order.getPartyATitle()),
                normalizeBlankToNull(order.getPlatformName())
        ));
    }

    private String resolveNotifyPartyBTitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String fromOrder = firstNonBlank(
                normalizeBlankToNull(order.getOperationEntityTitle()),
                normalizeBlankToNull(order.getDeliveryParty())
        );
        if (fromOrder != null) {
            return nullToDash(fromOrder);
        }
        if (order.getCreatedBy() != null) {
            User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
            if (creator != null) {
                String companyTitle = normalizeBlankToNull(creator.getCompanyTitle());
                if (companyTitle != null) {
                    return nullToDash(companyTitle);
                }
            }
        }
        return "-";
    }

    private String resolveNotifySalesSellerTitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String seller = firstNonBlank(
                normalizeBlankToNull(order.getOperationEntityTitle()),
                resolveCreatorCompanyTitle(order)
        );
        return seller == null ? "-" : nullToDash(seller);
    }

    private String resolveNotifySalesDeliveryPartyTitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String delivery = firstNonBlank(
                normalizeBlankToNull(order.getDeliveryParty()),
                normalizeBlankToNull(order.getShippingParty())
        );
        return delivery == null ? "-" : nullToDash(delivery);
    }

    private String resolveReadyForInvoiceNotifyPartyATitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String currentSubject = firstNonBlank(
                normalizeBlankToNull(order.getOperationEntityTitle()),
                resolveCreatorCompanyTitle(order)
        );
        if (currentSubject != null) {
            return nullToDash(currentSubject);
        }
        return resolveNotifyPartyATitle(order);
    }

    private String resolveReadyForInvoiceNotifyPartyBTitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        List<PurchaseOrder> purchaseOrders = resolveRelevantPurchaseOrdersForOutputReminder(order);
        if (purchaseOrders != null && !purchaseOrders.isEmpty()) {
            LinkedHashSet<String> suppliers = new LinkedHashSet<>();
            for (PurchaseOrder purchaseOrder : purchaseOrders) {
                if (purchaseOrder == null) {
                    continue;
                }
                String supplier = normalizeBlankToNull(purchaseOrder.getSupplier());
                if (supplier != null) {
                    suppliers.add(supplier);
                }
            }
            if (!suppliers.isEmpty()) {
                return String.join("，", suppliers);
            }
        }
        String fromOrder = firstNonBlank(
                normalizeBlankToNull(order.getDeliveryParty()),
                normalizeBlankToNull(order.getShippingParty())
        );
        if (fromOrder != null) {
            return nullToDash(fromOrder);
        }
        return resolveNotifyPartyBTitle(order);
    }

    private String resolveCreatorCompanyTitle(SalesOrder order) {
        if (order == null || order.getCreatedBy() == null) {
            return null;
        }
        User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
        if (creator == null) {
            return null;
        }
        return normalizeBlankToNull(creator.getCompanyTitle());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 甲方对账单号、发票号、甲方回款状态变更后的状态与钉钉：
     * 首次填写对账单号 → 已对账未开票并通知财务+飞础科业务员；
     * 首次填写发票号 → 已开票待结算；
     * 甲方回款状态首次变为已回款 → 已结算。
     * 须在订单已持久化后调用，prev* 为本次请求开始前的库中值。
     */
    @Transactional
    public void handleSettlementLifecycleAfterPersist(Long orderId, String prevPlatformReconciliationNo,
            String prevInvoiceNumber, String prevPlatformRefundStatus) {
        if (orderId == null) {
            return;
        }
        SalesOrder after = salesOrderRepository.findById(orderId).orElse(null);
        if (after == null) {
            return;
        }

        String oldPrn = trimToEmpty(prevPlatformReconciliationNo);
        String newPrn = trimToEmpty(after.getPlatformReconciliationNo());
        String oldInv = trimToEmpty(prevInvoiceNumber);
        String newInv = trimToEmpty(after.getInvoiceNumber());
        String oldRefund = trimToEmpty(prevPlatformRefundStatus);
        String newRefund = trimToEmpty(after.getPlatformRefundStatus());

        boolean statusChanged = reconcileSettlementStatus(after);
        if (statusChanged) {
            after = salesOrderRepository.save(after);
        }

        // 甲方已回款后 → 已结算（优先于已开票待结算/已对账未开票）
        if ("已回款".equals(newRefund) && !"已回款".equals(oldRefund)) {
            return;
        }

        // 财务维护发票号后 → 已开票待结算（优先于对账待开票）
        if (!newInv.isEmpty() && oldInv.isEmpty()) {
            sendInvoiceIssuedDingTalk(salesOrderRepository.findById(orderId).orElse(after));
            return;
        }

        if (!newPrn.isEmpty() && oldPrn.isEmpty() && newInv.isEmpty()) {
            if (!isReadyForInvoiceReminder(after)) {
                return;
            }
            sendReconciliationReadyForInvoiceDingTalk(salesOrderRepository.findById(orderId).orElse(after));
        }
    }

    @Transactional
    public void notifyReadyForInvoiceIfPurchaseChainCompleted(List<Long> salesOrderIds) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(salesOrderIds);
        for (Long orderId : uniqueIds) {
            if (orderId == null) {
                continue;
            }
            SalesOrder order = salesOrderRepository.findById(orderId).orElse(null);
            if (order == null || !isReadyForInvoiceReminder(order)) {
                continue;
            }
            String status = trimToEmpty(order.getStatus());
            if (!"已对账未开票".equals(status)) {
                order.setStatus("已对账未开票");
                order = salesOrderRepository.save(order);
            }
            sendReconciliationReadyForInvoiceDingTalk(order);
        }
    }

    private boolean isReadyForInvoiceReminder(SalesOrder order) {
        if (order == null) {
            return false;
        }
        if (normalizeBlankToNull(order.getPlatformReconciliationNo()) == null) {
            return false;
        }
        if (normalizeBlankToNull(order.getInvoiceNumber()) != null) {
            return false;
        }
        String status = trimToEmpty(order.getStatus());
        if (Set.of("已退回", "已取消", "已结算").contains(status)) {
            return false;
        }
        List<PurchaseOrder> purchaseOrders = resolveRelevantPurchaseOrdersForOutputReminder(order);
        if (purchaseOrders.isEmpty()) {
            return true;
        }
        return purchaseOrders.stream().allMatch(this::hasCompletedPurchaseInvoiceChainForReminder);
    }

    private List<PurchaseOrder> resolveRelevantPurchaseOrdersForOutputReminder(SalesOrder order) {
        if (order == null) {
            return List.of();
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo == null) {
            return List.of();
        }
        List<PurchaseOrder> candidates = purchaseOrderRepository.findByOmsOrderNo(omsOrderNo);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, PurchaseOrder> matched = new LinkedHashMap<>();
        Long salesOrderId = order.getId();
        if (salesOrderId != null) {
            for (PurchaseOrder candidate : candidates) {
                if (candidate == null || candidate.getId() == null) {
                    continue;
                }
                if (Objects.equals(candidate.getSourceSalesOrderId(), salesOrderId)
                        || parseMergedSalesOrderIds(candidate.getMergedSalesOrderIds()).contains(salesOrderId)) {
                    matched.put(candidate.getId(), candidate);
                }
            }
        }
        if (!matched.isEmpty()) {
            return new ArrayList<>(matched.values());
        }

        String expectedSupplier = normalizeBlankToNull(order.getDeliveryParty());
        if (expectedSupplier == null) {
            expectedSupplier = normalizeBlankToNull(order.getShippingParty());
        }
        if (expectedSupplier == null) {
            return List.of();
        }
        for (PurchaseOrder candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (expectedSupplier.equals(normalizeBlankToNull(candidate.getSupplier()))) {
                matched.put(candidate.getId(), candidate);
            }
        }
        return new ArrayList<>(matched.values());
    }

    private boolean hasCompletedPurchaseInvoiceChainForReminder(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getId() == null) {
            return false;
        }
        List<PurchaseReconciliationItem> reconciliationItems = purchaseReconciliationItemRepository.findAll().stream()
                .filter(item -> Objects.equals(item.getPurchaseOrderId(), purchaseOrder.getId()))
                .toList();
        if (reconciliationItems.isEmpty()) {
            return false;
        }
        for (PurchaseReconciliationItem reconciliationItem : reconciliationItems) {
            if (reconciliationItem == null
                    || reconciliationItem.getReconciliation() == null
                    || reconciliationItem.getReconciliation().getId() == null
                    || "已作废".equals(trimToEmpty(reconciliationItem.getReconciliation().getStatus()))) {
                continue;
            }
            Long reconciliationId = reconciliationItem.getReconciliation().getId();
            List<PurchaseInputInvoiceItem> invoiceItems = purchaseInputInvoiceItemRepository.findAll().stream()
                    .filter(item -> Objects.equals(item.getReconciliationId(), reconciliationId))
                    .toList();
            for (PurchaseInputInvoiceItem invoiceItem : invoiceItems) {
                if (invoiceItem == null || invoiceItem.getInvoice() == null || invoiceItem.getInvoice().getId() == null) {
                    continue;
                }
                PurchaseInputInvoiceRecord invoice = purchaseInputInvoiceRecordRepository.findById(invoiceItem.getInvoice().getId()).orElse(null);
                if (invoice == null || "已作废".equals(trimToEmpty(invoice.getStatus()))) {
                    continue;
                }
                if (hasActualPurchaseInvoiceNumberForReminder(invoice) && normalizeBlankToNull(invoice.getAttachmentUrl()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasActualPurchaseInvoiceNumberForReminder(PurchaseInputInvoiceRecord invoice) {
        if (invoice == null) {
            return false;
        }
        String invoiceNumber = normalizeBlankToNull(invoice.getInvoiceNumber());
        if (invoiceNumber == null) {
            return false;
        }
        String billNo = normalizeBlankToNull(invoice.getBillNo());
        return billNo == null || !invoiceNumber.equalsIgnoreCase(billNo);
    }

    private void sendReconciliationReadyForInvoiceDingTalk(SalesOrder order) {
        try {
            if (order == null) {
                return;
            }
            List<User> all = userRepository.findAll();
            LinkedHashSet<String> atPhones = new LinkedHashSet<>();
            List<String> financeNames = new ArrayList<>();
            List<String> feichukeNames = new ArrayList<>();

            for (User u : all) {
                if (u == null) {
                    continue;
                }
                String perm = u.getPermissions() != null ? u.getPermissions() : "";
                boolean finance = false;
                for (String p : perm.split(",")) {
                    String t = p.trim();
                    if ("settlement_finance".equalsIgnoreCase(t) || "platform_refund".equalsIgnoreCase(t)) {
                        finance = true;
                        break;
                    }
                }
                String ct = u.getCompanyTitle() != null ? u.getCompanyTitle().trim() : "";
                boolean feichuke = ct.contains("飞础科智慧科技（上海）");
                if (!finance && !feichuke) {
                    continue;
                }
                String disp = (u.getRealName() != null && !u.getRealName().isBlank())
                        ? u.getRealName().trim()
                        : (u.getUsername() != null ? u.getUsername() : "?");
                String un = u.getUsername() != null ? u.getUsername() : "";
                if (finance) {
                    financeNames.add(disp + "(" + un + ")");
                }
                if (feichuke) {
                    feichukeNames.add(disp + "(" + un + ")");
                }
                String phone = u.getPhone();
                if (phone != null && phone.replaceAll("\\D", "").length() >= 6) {
                    atPhones.add(phone.trim());
                }
            }

            for (String realName : splitCommaSeparatedValues(reconciliationReadyExtraAtRealNames)) {
                List<User> matchedUsers = userRepository.findByRealName(realName);
                for (User matched : matchedUsers) {
                    if (matched == null || !Boolean.TRUE.equals(matched.getEnabled())) {
                        continue;
                    }
                    String disp = (matched.getRealName() != null && !matched.getRealName().isBlank())
                            ? matched.getRealName().trim()
                            : (matched.getUsername() != null ? matched.getUsername() : "?");
                    String un = matched.getUsername() != null ? matched.getUsername() : "";
                    String display = disp + "(" + un + ")";
                    if (!financeNames.contains(display)) {
                        financeNames.add(display);
                    }
                    String phone = matched.getPhone();
                    if (phone != null && phone.replaceAll("\\D", "").length() >= 6) {
                        atPhones.add(phone.trim());
                    }
                }
            }

            String title = "🧾 订单已对账，待财务开票";
            String text = "## 🧾 订单已对账，待财务开票\n\n"
                    + "**OMS订单号：** " + nullToDash(order.getOmsOrderNo()) + "\n\n"
                    + "**甲方订单号：** " + nullToDash(order.getPlatformOrderNo()) + "\n\n"
                    + "**购买方抬头：** " + resolveReadyForInvoiceNotifyPartyATitle(order) + "\n\n"
                    + "**销售方抬头：** " + resolveReadyForInvoiceNotifyPartyBTitle(order) + "\n\n"
                    + "**交付方：** " + resolveNotifySalesDeliveryPartyTitle(order) + "\n\n"
                    + "**甲方对账单号：** " + nullToDash(order.getPlatformReconciliationNo()) + "\n\n"
                    + "**结算单号：** " + nullToDash(order.getSettlementNo()) + "\n\n"
                    + "**工业电商业务员：** " + nullToDash(order.getEcommerceSalesName()) + "\n\n"
                    + "**当前状态：** 已对账未开票\n\n"
                    + "---\n*来自 OMS 订单系统*";

            dingTalkService.sendMarkdownMessage(title, text,
                    atPhones.isEmpty() ? null : new ArrayList<>(atPhones));
        } catch (Exception e) {
            System.err.println("发送对账待开票钉钉通知失败: " + e.getMessage());
        }
    }

    private void sendInvoiceIssuedDingTalk(SalesOrder order) {
        try {
            if (order == null) {
                return;
            }
            List<User> all = userRepository.findAll();
            LinkedHashSet<String> atPhones = new LinkedHashSet<>();
            List<String> financeNames = new ArrayList<>();
            List<String> salesOwnerNames = new ArrayList<>();

            for (User u : all) {
                if (u == null || !Boolean.TRUE.equals(u.getEnabled())) {
                    continue;
                }
                String disp = (u.getRealName() != null && !u.getRealName().isBlank())
                        ? u.getRealName().trim()
                        : (u.getUsername() != null ? u.getUsername() : "?");
                String un = u.getUsername() != null ? u.getUsername() : "";
                String display = disp + "(" + un + ")";

                String perm = u.getPermissions() != null ? u.getPermissions() : "";
                boolean finance = false;
                for (String p : perm.split(",")) {
                    String t = p.trim();
                    if ("settlement_finance".equalsIgnoreCase(t) || "platform_refund".equalsIgnoreCase(t)) {
                        finance = true;
                        break;
                    }
                }
                if (finance && !financeNames.contains(display)) {
                    financeNames.add(display);
                    String phone = u.getPhone();
                    if (phone != null && phone.replaceAll("\\D", "").length() >= 6) {
                        atPhones.add(phone.trim());
                    }
                }

                if (matchesOrderSalesOwner(u, order) && !salesOwnerNames.contains(display)) {
                    salesOwnerNames.add(display);
                    String phone = u.getPhone();
                    if (phone != null && phone.replaceAll("\\D", "").length() >= 6) {
                        atPhones.add(phone.trim());
                    }
                }
            }

            String title = "🧾 订单已开票，待客户结算";
            String text = "## 🧾 订单已开票，待客户结算\n\n"
                    + "**OMS订单号：** " + nullToDash(order.getOmsOrderNo()) + "\n\n"
                    + "**甲方订单号：** " + nullToDash(order.getPlatformOrderNo()) + "\n\n"
                    + "**购买方抬头：** " + resolveNotifyPartyATitle(order) + "\n\n"
                    + "**销售方抬头：** " + resolveNotifySalesSellerTitle(order) + "\n\n"
                    + "**交付方：** " + resolveNotifySalesDeliveryPartyTitle(order) + "\n\n"
                    + "**甲方对账单号：** " + nullToDash(order.getPlatformReconciliationNo()) + "\n\n"
                    + "**发票号码：** " + nullToDash(order.getInvoiceNumber()) + "\n\n"
                    + "**工业电商业务员：** " + nullToDash(order.getEcommerceSalesName()) + "\n\n"
                    + "**当前状态：** 已开票待结算\n\n"
                    + "---\n*来自 OMS 订单系统*";

            dingTalkService.sendMarkdownMessage(title, text,
                    atPhones.isEmpty() ? null : new ArrayList<>(atPhones));
        } catch (Exception e) {
            System.err.println("发送已开票待结算钉钉通知失败: " + e.getMessage());
        }
    }

    private boolean matchesOrderSalesOwner(User currentUser, SalesOrder order) {
        if (currentUser == null || order == null) {
            return false;
        }
        String omsOrderNo = normalizeBlankToNull(order.getOmsOrderNo());
        if (omsOrderNo != null) {
            return salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(omsOrderNo).stream()
                    .anyMatch(item -> matchesErpSalesOwner(currentUser, item.getCreatedBy(), item.getEcommerceSalesName()));
        }
        return matchesErpSalesOwner(currentUser, order.getCreatedBy(), order.getEcommerceSalesName());
    }

    @Transactional
    public void deleteOrder(Long id) {
        if (id != null) {
            // 获取当前登录用户
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            
            SalesOrder order = salesOrderRepository.findById(id).orElse(null);
            
            // 权限检查：非管理员只能删除自己创建的订单
            if (order != null && currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                if (order.getCreatedBy() == null || !order.getCreatedBy().equals(currentUser.getId())) {
                    throw new RuntimeException("无权删除该订单");
                }
            }
            
            salesOrderRepository.deleteById(id);
        }
    }

    @Transactional
    public SalesOrder auditOrder(Long id, LocalDate deliveryDate) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        // 权限：管理员；或创建人（待指派时确认）；或被指派方（待确认订单时确认，如坚领确认指派过来的订单）
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId());
            boolean isAssignee = currentUsername != null && currentUsername.equals(order.getAssignedUsername());
            boolean canConfirm = isCreator || ("待确认订单".equals(order.getStatus()) && isAssignee);
            if (!canConfirm) {
                throw new RuntimeException("无权操作该订单");
            }
        }
        
        order.setStatus("待合同盖章");
        if (deliveryDate != null) {
            order.setDeliveryDate(deliveryDate);
        }
        expectedRefundDateService.refreshExpectedRefundFields(order);
        return salesOrderRepository.save(order);
    }

    @Transactional
    public SalesOrder updateStatus(Long id, String status) {
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        // 权限检查：管理员、仓库(全量可见)可改状态；其他用户仅能操作自己创建或被指派的订单
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            boolean isWarehouse = hasWarehouseScope(currentUser);
            boolean hasAllOrders = hasAllOrdersScope(currentUser);
            if (!isWarehouse && !hasAllOrders) {
                boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId());
                boolean isAssigned = order.getAssignedUsername() != null && order.getAssignedUsername().equals(currentUsername);
                if (!isCreator && !isAssigned) {
                    throw new RuntimeException("无权操作该订单");
                }
            }
        }
        
        String oldStatus = order.getStatus();
        order.setStatus(status);
        SalesOrder saved = salesOrderRepository.save(order);
        String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                ? currentUser.getRealName() : currentUsername;
        operationLogService.log(operatorName, "状态变更", "SALES_ORDER", String.valueOf(id),
                "状态由 " + (oldStatus != null ? oldStatus : "") + " 改为 " + status);
        return saved;
    }

    @Transactional
    public SalesOrder updateContractUrl(Long id, String contractUrl) {
        System.out.println("updateContractUrl called with id: " + id);
        System.out.println("contractUrl: " + contractUrl);
        
        // 获取当前登录用户
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        subjectAccountGroupService.assertPrimaryActor(currentUser, "合同附件维护");
        
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        
        // 权限检查：非管理员只能操作自己创建的订单
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            boolean canManageAll = hasWarehouseScope(currentUser) || hasAllOrdersScope(currentUser);
            List<Long> sharedUserIds = subjectAccountGroupService.sharedUserIds(currentUser.getUsername());
            boolean belongsToSharedCreator = order.getCreatedBy() != null && !sharedUserIds.isEmpty() && sharedUserIds.contains(order.getCreatedBy());
            if (!canManageAll && !belongsToSharedCreator && (order.getCreatedBy() == null || !order.getCreatedBy().equals(currentUser.getId()))) {
                throw new RuntimeException("无权操作该订单");
            }
        }
        
        order.setContractUrl(contractUrl);
        return salesOrderRepository.save(order);
    }

    @Transactional
    public SalesOrder returnOrder(Long id, String returnReason) {
        List<SalesOrder> returnedOrders = returnOrders(Collections.singletonList(id), returnReason);
        if (returnedOrders.isEmpty()) {
            throw new RuntimeException("订单不存在");
        }
        return returnedOrders.get(0);
    }

    @Transactional
    public List<SalesOrder> returnOrders(List<Long> ids, String returnReason) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        subjectAccountGroupService.assertPrimaryActor(currentUser, "订单退回");
        String operatorName = (currentUser != null && currentUser.getRealName() != null && !currentUser.getRealName().isBlank())
                ? currentUser.getRealName() : currentUsername;
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    uniqueIds.add(id);
                }
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new RuntimeException("请至少选择一条订单进行退回");
        }
        List<SalesOrder> returnedOrders = new ArrayList<>();
        for (Long id : uniqueIds) {
            returnedOrders.add(returnOrderInternal(id, returnReason, currentUsername, currentUser, operatorName));
        }
        notifyReturnBatch(returnedOrders, returnReason, operatorName);
        return returnedOrders;
    }

    private SalesOrder returnOrderInternal(Long id,
                                           String returnReason,
                                           String currentUsername,
                                           User currentUser,
                                           String operatorName) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        SalesOrder mainOrder;
        SalesOrder chainOrderReturner = null;
        if (order.getPurchaseOrderNo() != null && order.getPurchaseOrderNo().startsWith("CHAIN_FROM:")) {
            chainOrderReturner = order;
            String mainIdStr = order.getPurchaseOrderNo().substring("CHAIN_FROM:".length()).trim();
            try {
                long mainId = Long.parseLong(mainIdStr);
                mainOrder = salesOrderRepository.findById(mainId)
                        .orElseThrow(() -> new RuntimeException("主单不存在"));
            } catch (NumberFormatException e) {
                throw new RuntimeException("无法解析主单ID: " + mainIdStr);
            }
        } else {
            mainOrder = order;
        }

        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            List<String> sharedUsernames = subjectAccountGroupService.sharedUsernames(currentUsername);
            boolean isAssignedUser = mainOrder.getAssignedUsername() != null
                    && ((!sharedUsernames.isEmpty() && sharedUsernames.contains(mainOrder.getAssignedUsername()))
                    || mainOrder.getAssignedUsername().equals(currentUsername));
            boolean isDeliveryPartyCompany = false;
            if (!isAssignedUser && currentUser.getCompanyTitle() != null && !currentUser.getCompanyTitle().isBlank()
                    && mainOrder.getDeliveryParty() != null && !mainOrder.getDeliveryParty().isBlank()) {
                String ct = currentUser.getCompanyTitle().trim();
                String dp = mainOrder.getDeliveryParty().trim();
                isDeliveryPartyCompany = ct.equals(dp) || ct.contains(dp) || dp.contains(ct);
            }
            if (!isAssignedUser && !isDeliveryPartyCompany) {
                throw new RuntimeException("只有被指派的用户或交付方公司用户才能退回该订单");
            }
        }

        mainOrder.setReturnReason(returnReason);
        mainOrder.setReturnTime(LocalDateTime.now());
        mainOrder.setReturnedBy(currentUsername);
        String previousAssignee = mainOrder.getPreviousAssignedUsername();
        if (previousAssignee != null && !previousAssignee.isBlank()) {
            mainOrder.setAssignedUsername(previousAssignee);
            mainOrder.setPreviousAssignedUsername(null);
        } else {
            if (mainOrder.getCreatedBy() != null) {
                userRepository.findById(mainOrder.getCreatedBy())
                        .filter(u -> u.getUsername() != null && !u.getUsername().isBlank())
                        .ifPresent(u -> mainOrder.setAssignedUsername(u.getUsername().trim()));
            }
        }
        mainOrder.setAssignTime(null);
        mainOrder.setStatus("已退回");
        Long mainId = mainOrder.getId();

        cleanupContractsForReturnedOrder(mainOrder);
        // 与删除系统内合同记录一致：清空已上传合同附件 URL（含 pdf/word/图片/mp4 等），避免退回后仍指向旧文件或预览异常
        mainOrder.setContractUrl(null);

        salesOrderRepository.save(mainOrder);

        if (chainOrderReturner != null) {
            chainOrderReturner.setStatus("已退回");
            chainOrderReturner.setReturnReason(returnReason);
            chainOrderReturner.setReturnTime(LocalDateTime.now());
            chainOrderReturner.setReturnedBy(currentUsername);
            chainOrderReturner.setContractUrl(null);
            salesOrderRepository.save(chainOrderReturner);

            if (previousAssignee != null && !previousAssignee.isBlank()) {
                userRepository.findByUsername(previousAssignee).ifPresent(prevUser -> {
                    if (prevUser.getId() != null) {
                        salesOrderRepository.findFirstByPurchaseOrderNoAndCreatedByOrderByIdDesc("CHAIN_FROM:" + mainId, prevUser.getId())
                                .ifPresent(returnedToChain -> {
                                    returnedToChain.setStatus("待合同盖章");
                                    salesOrderRepository.save(returnedToChain);
                                });
                    }
                });
            }
        }

        operationLogService.log(operatorName, "订单退回", "SALES_ORDER", String.valueOf(mainId),
                "退回，原因：" + (returnReason != null ? returnReason : ""));
        return mainOrder;
    }

    private void cleanupContractsForReturnedOrder(SalesOrder returnedOrder) {
        if (returnedOrder == null || returnedOrder.getId() == null) {
            return;
        }
        LinkedHashSet<Contract> impactedContracts = new LinkedHashSet<>();
        LinkedHashSet<PurchaseOrder> impactedPurchaseOrders = new LinkedHashSet<>();
        List<Contract> directContracts = contractRepository.findAllBySalesOrderId(returnedOrder.getId());
        if (directContracts != null) {
            impactedContracts.addAll(directContracts);
        }
        if (returnedOrder.getOmsOrderNo() != null && !returnedOrder.getOmsOrderNo().isBlank()) {
            List<PurchaseOrder> directPurchaseOrders = purchaseOrderRepository.findByOmsOrderNo(returnedOrder.getOmsOrderNo());
            if (directPurchaseOrders != null) {
                for (PurchaseOrder purchaseOrder : directPurchaseOrders) {
                    if (purchaseOrder == null) {
                        continue;
                    }
                    if (purchaseOrderContainsOrderId(purchaseOrder, returnedOrder.getId())) {
                        impactedPurchaseOrders.add(purchaseOrder);
                    }
                }
            }
        }
        if (returnedOrder.getMasterId() != null) {
            List<Contract> masterContracts = contractRepository.findByMasterIdOrderByIdDesc(returnedOrder.getMasterId());
            if (masterContracts != null) {
                for (Contract contract : masterContracts) {
                    if (contract == null) {
                        continue;
                    }
                    if ("MASTER_SELECTION".equalsIgnoreCase(safe(contract.getContractScope()))
                            && contractContainsOrderId(contract, returnedOrder.getId())) {
                        impactedContracts.add(contract);
                    }
                }
            }
            List<PurchaseOrder> masterPurchaseOrders = purchaseOrderRepository.findByMasterIdOrderByIdDesc(returnedOrder.getMasterId());
            if (masterPurchaseOrders != null) {
                for (PurchaseOrder purchaseOrder : masterPurchaseOrders) {
                    if (purchaseOrder == null) {
                        continue;
                    }
                    if (purchaseOrderContainsOrderId(purchaseOrder, returnedOrder.getId())) {
                        impactedPurchaseOrders.add(purchaseOrder);
                    }
                }
            }
        }
        for (Contract contract : impactedContracts) {
            if (contract == null) {
                continue;
            }
            if ("MASTER_SELECTION".equalsIgnoreCase(safe(contract.getContractScope()))) {
                contract.setStatus(hasActiveContractOrders(contract)
                        ? ContractService.CONTRACT_STATUS_INVALID_PENDING_REGENERATE
                        : ContractService.CONTRACT_STATUS_INVALID);
                contractRepository.save(contract);
            } else {
                contract.setStatus(ContractService.CONTRACT_STATUS_INVALID);
                contractRepository.save(contract);
            }
        }
        for (PurchaseOrder purchaseOrder : impactedPurchaseOrders) {
            purchaseOrderRepository.delete(purchaseOrder);
        }
    }

    private boolean contractContainsOrderId(Contract contract, Long salesOrderId) {
        if (contract == null || salesOrderId == null) {
            return false;
        }
        if (salesOrderId.equals(contract.getSalesOrderId())) {
            return true;
        }
        for (Long id : parseMergedSalesOrderIds(contract.getMergedSalesOrderIds())) {
            if (salesOrderId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private List<Long> parseMergedSalesOrderIds(String mergedSalesOrderIds) {
        List<Long> result = new ArrayList<>();
        if (mergedSalesOrderIds == null || mergedSalesOrderIds.isBlank()) {
            return result;
        }
        for (String part : mergedSalesOrderIds.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignore) {
            }
        }
        return result;
    }

    private boolean purchaseOrderContainsOrderId(PurchaseOrder purchaseOrder, Long salesOrderId) {
        if (purchaseOrder == null || salesOrderId == null) {
            return false;
        }
        if (salesOrderId.equals(purchaseOrder.getSourceSalesOrderId())) {
            return true;
        }
        for (Long id : parseMergedSalesOrderIds(purchaseOrder.getMergedSalesOrderIds())) {
            if (salesOrderId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveContractOrders(Contract contract) {
        if (contract == null) {
            return false;
        }
        LinkedHashSet<Long> orderIds = new LinkedHashSet<>();
        if (contract.getSalesOrderId() != null) {
            orderIds.add(contract.getSalesOrderId());
        }
        orderIds.addAll(parseMergedSalesOrderIds(contract.getMergedSalesOrderIds()));
        for (Long orderId : orderIds) {
            SalesOrder order = salesOrderRepository.findById(orderId).orElse(null);
            if (order != null && !"已退回".equals(safe(order.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    private void notifyReturnBatch(List<SalesOrder> returnedOrders, String returnReason, String operatorName) {
        if (returnedOrders == null || returnedOrders.isEmpty()) {
            return;
        }
        SalesOrder first = returnedOrders.get(0);
        String operatorCompanyTitle = resolveCurrentOperatorCompanyTitle();
        if ("-".equals(safe(operatorCompanyTitle))) {
            operatorCompanyTitle = resolvePartyATitle(first);
        }
        String returnTargetCompanyTitle = resolveReturnTargetCompanyTitle(first);
        StringBuilder markdown = new StringBuilder();
        markdown.append("### 订单退回通知\n")
                .append("- OMS订单号：").append(safe(first.getOmsOrderNo())).append("\n")
                .append("- 甲方订单号：").append(safe(first.getPlatformOrderNo())).append("\n")
                .append("- 当前操作方所属抬头：").append(safe(operatorCompanyTitle)).append("\n")
                .append("- 退回至抬头：").append(safe(returnTargetCompanyTitle)).append("\n")
                .append("- 退回行数：").append(returnedOrders.size()).append("\n")
                .append("- 退回原因：").append(safe(returnReason)).append("\n")
                .append("- 操作人：").append(safe(operatorName)).append("\n")
                .append("- 明细：\n");
        for (SalesOrder order : returnedOrders) {
            markdown.append("  - ")
                    .append(safe(order.getProductName())).append(" / ")
                    .append(safe(order.getModel())).append(" / 数量 ")
                    .append(order.getQuantity() != null ? order.getQuantity() : 0)
                    .append("\n");
        }
        try {
            dingTalkService.sendMarkdownMessage("订单退回通知", markdown.toString());
        } catch (Exception e) {
            System.err.println("订单退回钉钉通知发送失败: " + e.getMessage());
        }
    }

    private String resolvePartyATitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String title = safe(order.getPartyATitle());
        if (!"-".equals(title)) {
            return title;
        }
        String customer = safe(order.getTopLevelCustomerName());
        if (!"-".equals(customer)) {
            return customer;
        }
        return safe(order.getPlatformName());
    }

    private String resolveCurrentOperatorCompanyTitle() {
        User currentUser = getCurrentUser();
        return currentUser == null ? "-" : safe(currentUser.getCompanyTitle());
    }

    private String resolveReturnTargetCompanyTitle(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String assignedUsername = order.getAssignedUsername();
        if (assignedUsername != null && !assignedUsername.isBlank()) {
            User assignedUser = userRepository.findByUsername(assignedUsername.trim()).orElse(null);
            if (assignedUser != null && assignedUser.getCompanyTitle() != null && !assignedUser.getCompanyTitle().isBlank()) {
                return assignedUser.getCompanyTitle().trim();
            }
        }
        if (order.getCreatedBy() != null) {
            User creator = userRepository.findById(order.getCreatedBy()).orElse(null);
            if (creator != null && creator.getCompanyTitle() != null && !creator.getCompanyTitle().isBlank()) {
                return creator.getCompanyTitle().trim();
            }
        }
        return resolvePartyATitle(order);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
