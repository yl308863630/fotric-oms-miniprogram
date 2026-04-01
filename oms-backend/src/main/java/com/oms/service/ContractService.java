package com.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.Contract;
import com.oms.entity.PartnerInfo;
import com.oms.entity.Product;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.config.ContractAccessPolicy;
import com.oms.entity.ContractTemplate;
import com.oms.repository.ContractRepository;
import com.oms.repository.ContractTemplateRepository;
import com.oms.repository.PartnerInfoRepository;
import com.oms.repository.ProductRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import com.oms.util.MoneyUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ContractService {

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderService salesOrderService;
    
    @Autowired
    private SalesOrderMasterService salesOrderMasterService;
    
    @Autowired
    private PdfService pdfService;

    @Autowired
    private PartnerInfoRepository partnerInfoRepository;

    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ContractTemplateRepository contractTemplateRepository;

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private SubjectAccountGroupService subjectAccountGroupService;

    @Value("${oms.notifications.contract-sign-fallback-at-mobiles:}")
    private String contractSignFallbackAtMobiles;

    @Value("${oms.notifications.contract-signed-extra-at-real-names:王文虎,李冰洁}")
    private String contractSignedExtraAtRealNames;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String FEICHUKE_TITLE = "飞础科智慧科技（上海）有限公司";
    private static final String REXIANG_TITLE = "上海热像科技股份有限公司";
    private static final String CHAIN_PREFIX = "CHAIN_FROM:";
    public static final String CONTRACT_STATUS_INVALID = "已失效";
    public static final String CONTRACT_STATUS_INVALID_PENDING_REGENERATE = "已失效待重生成";
    public static final String CONTRACT_STATUS_REGENERATED = "已重生成";
    private static final java.util.Set<String> FEICHUKE_USERNAMES = java.util.Set.of("sonmin");

    /**
     * 避免 findByTitle 在历史重复数据下抛 NonUniqueResultException：
     * 统一按 title/name 匹配列表，取 id 最大（最新）一条。
     */
    private PartnerInfo findLatestPartnerInfoByTitleOrName(String titleOrName) {
        if (titleOrName == null || titleOrName.isBlank()) return null;
        String key = titleOrName.trim();
        List<PartnerInfo> list = partnerInfoRepository.findAllByTitleOrNameTrimmed(key);
        if (list != null && !list.isEmpty()) {
            return list.stream()
                    .max(java.util.Comparator.comparingLong(p -> p.getId() != null ? p.getId() : 0L))
                    .orElse(null);
        }
        return partnerInfoRepository.findFirstByTitleOrNameContaining(key).orElse(null);
    }

    /** 安全获取当前用户名，未登录时返回 null */
    private String getCurrentUsernameSafe() {
        if (SecurityContextHolder.getContext() == null || SecurityContextHolder.getContext().getAuthentication() == null)
            return null;
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return (name != null && !name.isBlank()) ? name : null;
    }

    /** 当前用户是否为飞础科业务员（sonmin 或公司抬头为飞础科），仅此类用户合同 ${settlementParty} 填平台/结算方，其他用户为空 */
    private boolean isFeichukeOperator() {
        String name = getCurrentUsernameSafe();
        if (name == null) return false;
        User u = userRepository.findByUsername(name).orElse(null);
        if (u == null) return false;
        if ("ROLE_ADMIN".equals(u.getRole())) return true;
        String ct = u.getCompanyTitle();
        if (ct != null && !ct.isBlank() && ct.contains(FEICHUKE_TITLE)) return true;
        return FEICHUKE_USERNAMES.contains(name.trim());
    }

    private boolean isRexiangCompany(String companyTitle) {
        return companyTitle != null && REXIANG_TITLE.equals(companyTitle.trim());
    }

    public Page<Contract> getAllContracts(String contractNo, String status, Pageable pageable) {
        String name = getCurrentUsernameSafe();
        final String currentUsername = (name == null || name.isBlank()) ? "anonymousUser" : name;
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        List<String> sharedUsernames = currentUser != null ? subjectAccountGroupService.sharedUsernames(currentUsername) : List.of();
        List<Long> sharedUserIds = currentUser != null ? subjectAccountGroupService.sharedUserIds(currentUsername) : List.of();
        final String contractNoTrim = contractNo != null ? contractNo.trim() : "";
        final String statusTrim = status != null ? status.trim() : "";

        return contractRepository.findAll((Specification<Contract>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!contractNoTrim.isEmpty()) {
                predicates.add(cb.like(root.get("contractNo"), "%" + contractNoTrim + "%"));
            }
            if (!statusTrim.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), statusTrim));
            }

            if (currentUser != null && "ROLE_ADMIN".equals(currentUser.getRole())) {
                // 管理员：无额外条件，全表
            } else if (currentUser != null && ContractAccessPolicy.hasPlatformContractViewAll(currentUser)) {
                // 平台合同全量查看：全表只读
            } else if (currentUser != null && ContractAccessPolicy.hasRexiangFeichukeContractFullPortalWithoutAdmin(currentUser)) {
                // rxkj-sw / rxkj-cw 等：热像 + 飞础科相关合同全量
                predicates.add(contractTouchesRexiangOrFeichukePredicate(root, query, cb));
            } else if (currentUser != null) {
                List<Long> visibleSalesOrderIds = new ArrayList<>();

                List<SalesOrder> allSalesOrders = salesOrderRepository.findAll();
                for (SalesOrder order : allSalesOrders) {
                    if ("已退回".equals(order.getStatus())) {
                        continue;
                    }
                    if (order.getCreatedBy() != null && ((!sharedUserIds.isEmpty() && sharedUserIds.contains(order.getCreatedBy()))
                            || order.getCreatedBy().equals(currentUser.getId()))) {
                        visibleSalesOrderIds.add(order.getId());
                    } else if (order.getAssignedUsername() != null
                            && ((!sharedUsernames.isEmpty() && sharedUsernames.contains(order.getAssignedUsername()))
                            || order.getAssignedUsername().equals(currentUsername))) {
                        visibleSalesOrderIds.add(order.getId());
                    }
                }

                if (!visibleSalesOrderIds.isEmpty()) {
                    predicates.add(root.get("salesOrderId").in(visibleSalesOrderIds));
                } else {
                    predicates.add(cb.disjunction());
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /** 合同或关联销售订单任一方字段包含热像/飞础科全称 */
    private Predicate contractTouchesRexiangOrFeichukePredicate(Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String f = FEICHUKE_TITLE;
        String r = REXIANG_TITLE;
        Predicate partyA = cb.or(
                cb.and(cb.isNotNull(root.get("partyAName")), cb.like(root.get("partyAName"), "%" + f + "%")),
                cb.and(cb.isNotNull(root.get("partyAName")), cb.like(root.get("partyAName"), "%" + r + "%"))
        );
        Predicate partyB = cb.or(
                cb.and(cb.isNotNull(root.get("partyBName")), cb.like(root.get("partyBName"), "%" + f + "%")),
                cb.and(cb.isNotNull(root.get("partyBName")), cb.like(root.get("partyBName"), "%" + r + "%"))
        );
        Subquery<Long> sq = query.subquery(Long.class);
        Root<SalesOrder> so = sq.from(SalesOrder.class);
        sq.select(so.get("id"));
        Expression<String> pAt = so.get("partyATitle");
        Expression<String> pn = so.get("platformName");
        Expression<String> dp = so.get("deliveryParty");
        Expression<String> op = so.get("operationEntityTitle");
        Predicate orderMatch = cb.or(
                cb.and(cb.isNotNull(pAt), cb.like(pAt, "%" + f + "%")),
                cb.and(cb.isNotNull(pAt), cb.like(pAt, "%" + r + "%")),
                cb.and(cb.isNotNull(pn), cb.like(pn, "%" + f + "%")),
                cb.and(cb.isNotNull(pn), cb.like(pn, "%" + r + "%")),
                cb.and(cb.isNotNull(dp), cb.like(dp, "%" + f + "%")),
                cb.and(cb.isNotNull(dp), cb.like(dp, "%" + r + "%")),
                cb.and(cb.isNotNull(op), cb.like(op, "%" + f + "%")),
                cb.and(cb.isNotNull(op), cb.like(op, "%" + r + "%"))
        );
        sq.where(orderMatch);
        Predicate bySalesOrder = cb.and(cb.isNotNull(root.get("salesOrderId")), root.get("salesOrderId").in(sq));
        return cb.or(partyA, partyB, bySalesOrder);
    }

    private boolean orderFieldTouchesRexiangOrFeichuke(String field) {
        if (field == null || field.isBlank()) {
            return false;
        }
        String s = field.trim();
        return s.contains(FEICHUKE_TITLE) || s.contains(REXIANG_TITLE);
    }

    private boolean orderTouchesRexiangOrFeichuke(SalesOrder o) {
        if (o == null) {
            return false;
        }
        return orderFieldTouchesRexiangOrFeichuke(o.getPartyATitle())
                || orderFieldTouchesRexiangOrFeichuke(o.getPlatformName())
                || orderFieldTouchesRexiangOrFeichuke(o.getDeliveryParty())
                || orderFieldTouchesRexiangOrFeichuke(o.getOperationEntityTitle());
    }

    private boolean contractTouchesRexiangOrFeichuke(Contract c) {
        if (c == null) {
            return false;
        }
        if (orderFieldTouchesRexiangOrFeichuke(c.getPartyAName()) || orderFieldTouchesRexiangOrFeichuke(c.getPartyBName())) {
            return true;
        }
        Long sid = c.getSalesOrderId();
        if (sid == null) {
            return false;
        }
        return salesOrderRepository.findById(sid).map(this::orderTouchesRexiangOrFeichuke).orElse(false);
    }

    /**
     * 合同详情/下载/占位符/签署等：管理员；rxkj 全量门户且合同属热像/飞础科体系；或订单创建人/被指派方。
     */
    public boolean canCurrentUserAccessContract(User user, Contract contract) {
        if (user == null || contract == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (ContractAccessPolicy.hasRexiangFeichukeContractFullPortalWithoutAdmin(user) && contractTouchesRexiangOrFeichuke(contract)) {
            return true;
        }
        Long soId = contract.getSalesOrderId();
        if (soId == null) {
            return false;
        }
        SalesOrder o = salesOrderRepository.findById(soId).orElse(null);
        if (o == null) {
            return false;
        }
        List<Long> sharedUserIds = subjectAccountGroupService.sharedUserIds(user.getUsername());
        if (o.getCreatedBy() != null && ((!sharedUserIds.isEmpty() && sharedUserIds.contains(o.getCreatedBy()))
                || o.getCreatedBy().equals(user.getId()))) {
            return true;
        }
        String userCompanyTitle = normalizeBlankToNull(user.getCompanyTitle());
        if (userCompanyTitle != null) {
            if (isSameText(userCompanyTitle, contract.getPartyAName()) || isSameText(userCompanyTitle, contract.getPartyBName())) {
                return true;
            }
        }
        String contractPartyBRepresentative = normalizeBlankToNull(contract.getPartyBRepresentative());
        if (contractPartyBRepresentative != null) {
            String username = normalizeBlankToNull(user.getUsername());
            if (username != null && isSameText(username, contractPartyBRepresentative)) {
                return true;
            }
            String realName = normalizeBlankToNull(user.getRealName());
            if (realName != null && isSameText(realName, contractPartyBRepresentative)) {
                return true;
            }
        }
        String un = user.getUsername();
        List<String> sharedUsernames = subjectAccountGroupService.sharedUsernames(user.getUsername());
        return un != null && o.getAssignedUsername() != null
                && ((!sharedUsernames.isEmpty() && sharedUsernames.contains(o.getAssignedUsername().trim()))
                || un.trim().equals(o.getAssignedUsername().trim()));
    }

    /**
     * 合同只读权限：在原有可操作权限基础上，额外支持平台合同全量查看（不含写操作）。
     */
    public boolean canCurrentUserViewContract(User user, Contract contract) {
        if (user == null || contract == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (ContractAccessPolicy.hasPlatformContractViewAll(user)) {
            return true;
        }
        return canCurrentUserAccessContract(user, contract);
    }

    /** 创建/更新合同时：对销售订单有关联操作权（与列表可见性对齐并含 rxkj 全量） */
    public boolean canCurrentUserMutateContractForSalesOrder(User user, Long salesOrderId) {
        if (user == null || salesOrderId == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        if (!subjectAccountGroupService.isPrimaryActor(user.getUsername())) {
            return false;
        }
        SalesOrder o = salesOrderRepository.findById(salesOrderId).orElse(null);
        if (o == null) {
            return false;
        }
        if (ContractAccessPolicy.hasRexiangFeichukeContractFullPortalWithoutAdmin(user) && orderTouchesRexiangOrFeichuke(o)) {
            return true;
        }
        List<Long> sharedUserIds = subjectAccountGroupService.sharedUserIds(user.getUsername());
        if (o.getCreatedBy() != null && ((!sharedUserIds.isEmpty() && sharedUserIds.contains(o.getCreatedBy()))
                || o.getCreatedBy().equals(user.getId()))) {
            return true;
        }
        String un = user.getUsername();
        List<String> sharedUsernames = subjectAccountGroupService.sharedUsernames(user.getUsername());
        return un != null && o.getAssignedUsername() != null
                && ((!sharedUsernames.isEmpty() && sharedUsernames.contains(o.getAssignedUsername().trim()))
                || un.trim().equals(o.getAssignedUsername().trim()));
    }

    public Optional<Contract> getContractById(Long id) {
        return contractRepository.findById(id);
    }

    public Optional<Contract> getContractBySalesOrderId(Long salesOrderId) {
        java.util.List<Contract> list = contractRepository.findAllBySalesOrderId(salesOrderId);
        if (list == null || list.isEmpty()) return java.util.Optional.empty();
        return list.stream()
                .filter(contract -> !"MASTER_SELECTION".equalsIgnoreCase(safe(contract.getContractScope())))
                .max(java.util.Comparator.comparingLong(c -> c.getId() != null ? c.getId() : 0))
                .or(() -> list.stream().max(java.util.Comparator.comparingLong(c -> c.getId() != null ? c.getId() : 0)));
    }

    public Optional<Contract> getContractByContractNo(String contractNo) {
        if (contractNo == null || contractNo.isBlank()) return java.util.Optional.empty();
        return contractRepository.findByContractNo(contractNo.trim());
    }

    @Transactional
    public Contract createContractFromSalesOrder(Long salesOrderId,
                                                 String templateUrl,
                                                 String partyBRepresentative,
                                                 String platformName,
                                                 String paymentMethod,
                                                 String deliveryPartyFromRequest,
                                                 java.math.BigDecimal deliveryPartyPurchasePriceFromRequest,
                                                 String customContractNo,
                                                 String customPurchaseOrderNo) {
        System.out.println("=== createContractFromSalesOrder called ===");
        System.out.println("salesOrderId: " + salesOrderId);
        System.out.println("templateUrl (raw): " + templateUrl);
        System.out.println("partyBRepresentative: " + partyBRepresentative);
        System.out.println("platformName: " + platformName);
        System.out.println("paymentMethod: " + paymentMethod);
        System.out.println("deliveryPartyFromRequest: " + deliveryPartyFromRequest);
        System.out.println("deliveryPartyPurchasePriceFromRequest: " + deliveryPartyPurchasePriceFromRequest);
        // 若传入的是模板名称（无路径、非 URL），从合同模板表解析为实际 templateUrl；便于排查「模板未动却报错」时是否用错文件
        if (templateUrl != null && !templateUrl.isBlank() && !templateUrl.contains("/") && !templateUrl.startsWith("http")) {
            String name = templateUrl.trim();
            List<ContractTemplate> all = contractTemplateRepository.findAll();
            ContractTemplate match = all.stream().filter(t -> name.equals(t.getTemplateName() != null ? t.getTemplateName().trim() : null)).findFirst().orElse(null);
            if (match == null) match = all.stream().filter(t -> t.getTemplateName() != null && t.getTemplateName().contains(name)).findFirst().orElse(null);
            if (match != null && match.getTemplateUrl() != null && !match.getTemplateUrl().isBlank()) {
                String resolved = match.getTemplateUrl().trim();
                if (resolved.startsWith("http")) templateUrl = resolved;
                else if (resolved.startsWith("/")) templateUrl = resolved;
                else templateUrl = "/uploads/" + resolved;
                System.out.println("合同模板按名称解析: \"" + name + "\" -> templateUrl=\"" + templateUrl + "\" (模板表记录 id=" + (match.getId() != null ? match.getId() : "?") + ")");
            } else {
                System.err.println("合同模板按名称未解析到有效路径: \"" + name + "\"，将使用原值作为路径");
            }
        }
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new RuntimeException("销售订单不存在"));
        String portalName = getCurrentUsernameSafe();
        User portalUser = (portalName != null && !portalName.isBlank())
                ? userRepository.findByUsername(portalName.trim()).orElse(null) : null;
        if (!canCurrentUserMutateContractForSalesOrder(portalUser, salesOrderId)) {
            throw new RuntimeException("无权为该销售订单生成或维护合同");
        }
        // 一账户一套数据：主单只保留指派相关字段，不覆盖创建人的含税总价/业务员/支付方式；交付方采购价仅用于采购单与链式单
        if (deliveryPartyPurchasePriceFromRequest != null && deliveryPartyPurchasePriceFromRequest.compareTo(java.math.BigDecimal.ZERO) >= 0) {
            salesOrder.setDeliveryPartyPurchasePrice(deliveryPartyPurchasePriceFromRequest);
            System.out.println("=== 已用请求的交付方采购价回写主单(仅用于采购/链式单): " + deliveryPartyPurchasePriceFromRequest + " ===");
        }
        // 关键规则：
        // 1) platformName 仅用于飞础科合同占位符（settlementParty），不参与销售订单甲方字段覆盖。
        // 2) 非飞础科场景下，甲方始终应是当前账号（指派方）与乙方交易关系，不应被销售侧平台甲方污染。
        String currentUsername = getCurrentUsernameSafe();
        if (currentUsername == null) currentUsername = "";
        String requestPlatformName = (platformName != null) ? platformName.trim() : "";
        if (!requestPlatformName.isBlank()) {
            System.out.println("requestPlatformName (仅用于飞础科结算占位符): " + requestPlatformName);
        }
        // 一账户一套数据：主单不覆盖支付方式，由链式单和合同承载被指派方支付方式
        // 采购/指派列表有交付方但合同无乙方时：用请求体中的 deliveryParty 回填订单并保证合同能取到乙方
        if ((salesOrder.getDeliveryParty() == null || salesOrder.getDeliveryParty().isBlank()) && deliveryPartyFromRequest != null && !deliveryPartyFromRequest.isBlank()) {
            salesOrder.setDeliveryParty(deliveryPartyFromRequest.trim());
            salesOrder = salesOrderRepository.save(salesOrder);
            System.out.println("=== 订单 deliveryParty 原为空，已从请求体回填: " + salesOrder.getDeliveryParty() + " ===");
        } else {
            salesOrder = salesOrderRepository.save(salesOrder);
        }
        
        System.out.println("=== SalesOrder data ===");
        // 甲方 = 指派方身份（来自订单创建人/指派方的 PartnerInfo）；甲方代表 = 指派方业务员（创建人对应联系人或姓名）
        String assignerUsername = null;
        if (salesOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
            if (creator != null && creator.getUsername() != null && !creator.getUsername().isBlank())
                assignerUsername = creator.getUsername().trim();
        }
        if (assignerUsername == null || assignerUsername.isBlank())
            assignerUsername = getCurrentUsernameSafe();
        List<PartnerInfo> assignerPartners = (assignerUsername != null && !assignerUsername.isBlank())
                ? partnerInfoRepository.findByUsername(assignerUsername) : java.util.Collections.emptyList();
        PartnerInfo assignerPartner = (assignerPartners != null && !assignerPartners.isEmpty()) ? assignerPartners.get(0) : null;
        User assignerIdentityUser = (assignerUsername != null && !assignerUsername.isBlank())
                ? userRepository.findByUsername(assignerUsername).orElse(null)
                : null;
        String partyATitle;
        if (assignerPartner != null) {
            String t = (assignerPartner.getTitle() != null && !assignerPartner.getTitle().isBlank()) ? assignerPartner.getTitle().trim() : "";
            String n = (assignerPartner.getName() != null && !assignerPartner.getName().isBlank()) ? assignerPartner.getName().trim() : "";
            partyATitle = !t.isEmpty() ? t : n;
            if (partyATitle.isEmpty()) {
                if (assignerIdentityUser != null && assignerIdentityUser.getCompanyTitle() != null && !assignerIdentityUser.getCompanyTitle().isBlank()) {
                    partyATitle = assignerIdentityUser.getCompanyTitle().trim();
                } else {
                    partyATitle = resolvePartyATitle(salesOrder);
                }
            }
        } else {
            if (assignerIdentityUser != null && assignerIdentityUser.getCompanyTitle() != null && !assignerIdentityUser.getCompanyTitle().isBlank()) {
                partyATitle = assignerIdentityUser.getCompanyTitle().trim();
            } else {
                partyATitle = resolvePartyATitle(salesOrder);
            }
        }
        System.out.println("partyATitle (甲方，来自指派方): " + partyATitle);
        String effectiveDeliveryParty = (salesOrder.getDeliveryParty() != null && !salesOrder.getDeliveryParty().isBlank())
                ? salesOrder.getDeliveryParty() : (deliveryPartyFromRequest != null ? deliveryPartyFromRequest.trim() : null);
        System.out.println("deliveryParty (乙方): " + effectiveDeliveryParty);
        System.out.println("ecommerceSalesName: " + salesOrder.getEcommerceSalesName());
        System.out.println("model: " + salesOrder.getModel());
        System.out.println("productConfig: " + salesOrder.getProductConfig());
        System.out.println("warrantyPeriod: " + salesOrder.getWarrantyPeriod());
        System.out.println("quantity: " + salesOrder.getQuantity());
        System.out.println("taxIncludedPrice: " + salesOrder.getTaxIncludedPrice());
        System.out.println("deliveryPartyPurchasePrice: " + salesOrder.getDeliveryPartyPurchasePrice());
        System.out.println("taxIncludedTotal: " + salesOrder.getTaxIncludedTotal());
        System.out.println("partyATitle(real): " + salesOrder.getPartyATitle());
        System.out.println("platformName: " + salesOrder.getPlatformName());
        System.out.println("paymentMethod: " + salesOrder.getPaymentMethod());
        System.out.println("receiverAddress: " + salesOrder.getReceiverAddress());
        System.out.println("deliveryDate: " + salesOrder.getDeliveryDate());
        System.out.println("orderDate: " + salesOrder.getOrderDate());

        // 同一销售订单只保留一份合同：已有则更新；若有多条（历史数据）取 id 最大的一条，避免 NonUniqueResultException
        java.util.List<Contract> existingList = contractRepository.findAllBySalesOrderId(salesOrderId);
        boolean isNewContract = existingList == null || existingList.isEmpty();
        Contract contract;
        if (!isNewContract) {
            contract = existingList.stream().max(java.util.Comparator.comparingLong(c -> c.getId() != null ? c.getId() : 0)).orElseThrow();
            System.out.println("=== 该销售订单已有合同，复用并更新: contractId=" + contract.getId() + " contractNo=" + contract.getContractNo() + " ===");
        } else {
            contract = new Contract();
        }
        contract.setPartyA(partyATitle);
        contract.setPartyB(effectiveDeliveryParty);
        contract.setSalesOrderId(salesOrderId);
        contract.setSalesId(salesOrder.getEcommerceSalesId());
        // 甲方代表在下方从指派方(订单创建人) PartnerInfo.contactPerson 填入，此处不先用 ecommerceSalesName 覆盖
        contract.setPartyAName(partyATitle);
        contract.setPartyBName(effectiveDeliveryParty);
        // 甲方代表：优先用新建销售订单时填写的业务员（ecommerceSalesName），无则下方用指派方 PartnerInfo 联系人兜底
        if (salesOrder.getEcommerceSalesName() != null && !salesOrder.getEcommerceSalesName().isBlank())
            contract.setSalesName(salesOrder.getEcommerceSalesName().trim());
        // 产品名称：优先用销售订单手工填写的商品名称；为空时再按型号映射，最后回退 SKU/型号。
        String productNameVal = salesOrder.getProductName() == null ? "" : salesOrder.getProductName().trim();
        if (productNameVal.isEmpty() && salesOrder.getModel() != null && !salesOrder.getModel().isBlank()) {
            productNameVal = productRepository.findFirstByModel(salesOrder.getModel().trim())
                    .map(Product::getName).filter(n -> n != null && !n.isBlank()).orElse("");
        }
        if (productNameVal.isEmpty() && salesOrder.getPlatformSku() != null && !salesOrder.getPlatformSku().isBlank())
            productNameVal = salesOrder.getPlatformSku().trim();
        if (productNameVal.isEmpty() && salesOrder.getModel() != null)
            productNameVal = salesOrder.getModel();
        contract.setProductName(productNameVal != null ? productNameVal : "");
        contract.setProductModel(salesOrder.getModel());
        contract.setMaterialNo(salesOrder.getMaterialNo());
        contract.setProductConfig(salesOrder.getProductConfig());
        contract.setWarrantyPeriod(salesOrder.getWarrantyPeriod());
        contract.setQuantity(salesOrder.getQuantity());
        // 乙方采购价优先：有 deliveryPartyPurchasePrice 时合同单价/总价均用该值（如坚领-热像科技 3550），否则按扣点折算
        java.math.BigDecimal unitPriceDeducted = salesOrder.getTaxIncludedPrice();
        if (salesOrder.getTaxIncludedPrice() != null && salesOrder.getDeductionRate() != null) {
            try {
                java.math.BigDecimal rate = salesOrder.getDeductionRate().divide(new java.math.BigDecimal("100"));
                unitPriceDeducted = salesOrder.getTaxIncludedPrice().multiply(java.math.BigDecimal.ONE.subtract(rate));
                System.out.println("Calculated deducted unit price: " + unitPriceDeducted);
            } catch (Exception e) {
                System.err.println("Error calculating deducted unit price: " + e.getMessage());
            }
        }
        java.math.BigDecimal contractUnitPrice = salesOrder.getDeliveryPartyPurchasePrice() != null
                ? salesOrder.getDeliveryPartyPurchasePrice()
                : (unitPriceDeducted != null ? unitPriceDeducted : java.math.BigDecimal.ZERO);
        java.math.BigDecimal contractTotal = salesOrder.getDeliveryPartyPurchasePrice() != null && salesOrder.getQuantity() != null
                ? salesOrder.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(salesOrder.getQuantity()))
                : (salesOrder.getDeliveryPartyPurchasePrice() != null ? salesOrder.getDeliveryPartyPurchasePrice() : (unitPriceDeducted != null && salesOrder.getQuantity() != null ? unitPriceDeducted.multiply(new java.math.BigDecimal(salesOrder.getQuantity())) : java.math.BigDecimal.ZERO));
        contract.setUnitPrice(contractUnitPrice);
        contract.setTotalAmount(contractTotal);
        contract.setAmount(contractTotal);
        contract.setAmountCn(MoneyUtil.convertToChinese(contractTotal));
        // platformName 仅用于 ${settlementParty}；甲方/乙方已从指派方/被指派方 PartnerInfo 设置
        // 仅飞础科业务员（sonmin 或公司=飞础科）的合同填充 ${settlementParty}（平台/结算方）；其他用户合同该占位符为空
        String settlementParty = "";
        if (isFeichukeOperator()) {
            String top = requestPlatformName;
            if (top == null || top.isBlank()) {
                top = salesOrder.getTopLevelCustomerName();
            }
            if (top != null && !top.isBlank()) settlementParty = top.trim();
            else if (salesOrder.getPlatformName() != null && !salesOrder.getPlatformName().isBlank()) settlementParty = salesOrder.getPlatformName().trim();
            else if (salesOrder.getPartyATitle() != null && !salesOrder.getPartyATitle().isBlank()) settlementParty = salesOrder.getPartyATitle().trim();
        }
        contract.setSettlementPartyName(settlementParty);
        // ${platformName} 与 ${settlementParty} 统一为同一平台/结算方抬头，模板任选其一即可
        contract.setPlatformName(settlementParty != null ? settlementParty : (salesOrder.getPlatformName() != null ? salesOrder.getPlatformName().trim() : ""));
        contract.setPartyAOrderNo(salesOrder.getPlatformOrderNo());
        contract.setPaymentMethod(salesOrder.getPaymentMethod());
        contract.setDeliveryAddress(salesOrder.getReceiverAddress());
        contract.setDeliveryDate(salesOrder.getDeliveryDate());
        contract.setOrderDate(salesOrder.getOrderDate());
        
        if (salesOrder.getDeliveryDate() != null && salesOrder.getOrderDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(salesOrder.getOrderDate(), salesOrder.getDeliveryDate());
            contract.setDeliveryCycle((int) days);
        }
        contract.setSignDate(LocalDate.now());
        contract.setStatus("草稿");
        contract.setTemplateUrl(templateUrl);

        // 甲方资料与甲方代表（联系人）：优先用指派方 PartnerInfo；甲方代表仅在「销售订单未填业务员」时用指派方联系人
        if (assignerPartner != null) {
            System.out.println("=== PartyA 来自指派方 PartnerInfo ===");
            contract.setPartyAAddress(assignerPartner.getBankAddress());
            contract.setPartyABank(assignerPartner.getBankName());
            contract.setPartyAAccount(assignerPartner.getBankAccount());
            contract.setPartyATaxNo(assignerPartner.getTaxNumber());
            contract.setPartyAPhone(assignerPartner.getContactPhone());
            if (contract.getSalesName() == null || contract.getSalesName().isBlank())
                contract.setSalesName(assignerPartner.getContactPerson() != null && !assignerPartner.getContactPerson().isBlank()
                        ? assignerPartner.getContactPerson().trim() : "");
        } else if (partyATitle != null && !partyATitle.isBlank()) {
            String partyATitleTrimmed = partyATitle.trim();
            PartnerInfo partyA = findLatestPartnerInfoByTitleOrName(partyATitleTrimmed);
            if (partyA == null && !partyATitleTrimmed.equals(partyATitle)) {
                partyA = findLatestPartnerInfoByTitleOrName(partyATitle);
            }
            if (partyA != null) {
                System.out.println("=== PartyA PartnerInfo found by title ===");
                contract.setPartyAAddress(partyA.getBankAddress());
                contract.setPartyABank(partyA.getBankName());
                contract.setPartyAAccount(partyA.getBankAccount());
                contract.setPartyATaxNo(partyA.getTaxNumber());
                contract.setPartyAPhone(partyA.getContactPhone());
                if (contract.getSalesName() == null || contract.getSalesName().isBlank())
                    contract.setSalesName(partyA.getContactPerson() != null && !partyA.getContactPerson().isBlank()
                            ? partyA.getContactPerson().trim() : "");
            } else {
                System.out.println("=== PartyA PartnerInfo NOT found for title: " + partyATitle + " ===");
            }
        }
        // 甲方代表兜底：若仍未设置（订单未填业务员且无指派方联系人），用创建人真实姓名
        if (contract.getSalesName() == null || contract.getSalesName().isBlank()) {
            if (salesOrder.getCreatedBy() != null) {
                User creator = userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
                if (creator != null && creator.getRealName() != null && !creator.getRealName().isBlank())
                    contract.setSalesName(creator.getRealName().trim());
            }
            if (contract.getSalesName() == null || contract.getSalesName().isBlank())
                contract.setSalesName("");
        }

        User resolvedAssignUser = null;
        if (effectiveDeliveryParty != null && !effectiveDeliveryParty.isBlank()) {
            AssigneeResolution assigneeResolution = resolveAssigneeByDeliveryParty(effectiveDeliveryParty, partyBRepresentative);
            PartnerInfo partyB = assigneeResolution.partnerInfo;
            resolvedAssignUser = assigneeResolution.user;

            System.out.println("=== PartyB PartnerInfo found ===");
            contract.setPartyBAddress(partyB.getBankAddress());
            contract.setPartyBBank(partyB.getBankName());
            contract.setPartyBAccount(partyB.getBankAccount());
            contract.setPartyBTaxNo(partyB.getTaxNumber());
            contract.setPartyBPhone(partyB.getContactPhone());
            // 乙方代表 = 被指派方 PartnerInfo 的联系人
            contract.setPartyBRepresentative(resolvePartnerContact(partyB, resolvedAssignUser));

            System.out.println("=== Resolved assignee from 用户信息维护: assignedUsername=" + resolvedAssignUser.getUsername() + " ===");

            if (resolvedAssignUser.getUsername() != null && !resolvedAssignUser.getUsername().isBlank()) {
                // 指派后只形成「指派方 A 向 被指派方 B 采购」的采购单：首次指派 assigner=订单创建人，转派 assigner=原被指派方
                String previousAssignedUsername = salesOrder.getAssignedUsername();
                User assignerUser = null;
                if (previousAssignedUsername != null && !previousAssignedUsername.isBlank() && !previousAssignedUsername.equals(resolvedAssignUser.getUsername())) {
                    assignerUser = userRepository.findByUsername(previousAssignedUsername).orElse(null);
                }
                if (assignerUser == null && salesOrder.getCreatedBy() != null) {
                    assignerUser = userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
                }
                if (assignerUser != null && assignerUser.getId() != null) {
                    createOrUpdatePurchaseOrderForAssigner(salesOrder, assignerUser, customPurchaseOrderNo);
                }
                // 甲方代表/乙方代表已从各自 PartnerInfo 的 contactPerson 填入，此处不再覆盖
                contract.setSalesId(resolvedAssignUser.getId());
                contract.setPaymentMethod(paymentMethod != null && !paymentMethod.isEmpty() ? paymentMethod : salesOrder.getPaymentMethod());
                String previousAssignee = salesOrder.getAssignedUsername();
                if (previousAssignee != null && !previousAssignee.isBlank()) {
                    salesOrder.setPreviousAssignedUsername(previousAssignee);
                }
                salesOrder.setAssignedUsername(resolvedAssignUser.getUsername() != null ? resolvedAssignUser.getUsername().trim() : null);
                salesOrder.setAssignTime(LocalDateTime.now());
                // 一账户一套数据：主单不覆盖业务员，创建人列表始终看到自己填的业务员；被指派方看链式单
                if ("已退回".equals(salesOrder.getStatus())) {
                    salesOrder.setStatus("待确认订单");
                } else {
                    // 指派成功后由后端直接置为待合同盖章，避免前端再 PATCH 时因 assignedUsername 已改为新乙方而无权限导致 500
                    salesOrder.setStatus("待合同盖章");
                }
                salesOrderRepository.save(salesOrder);
                // 上海热像科技被指派/转派时发送钉钉提醒（用于仓库、业务等群内及时感知新单）
                notifyRexiangAssignChange(salesOrder, resolvedAssignUser, previousAssignee);
                // 转派（来源单已是链式单）时始终为被指派方生成链式单，否则其销售列表为空；仅当「主单直指源头工厂」时跳过
                String sourcePoNo = salesOrder.getPurchaseOrderNo();
                boolean sourceIsChain = sourcePoNo != null && sourcePoNo.startsWith("CHAIN_FROM:");
                boolean skipNextLevel = !sourceIsChain && isSourceFactory(resolvedAssignUser);
                if (!skipNextLevel) {
                    createOrUpdateNextLevelSalesOrder(salesOrder, resolvedAssignUser, assignerUser);
                } else {
                    System.out.println("=== Skip next-level order: assignee is source factory (出货方), main order direct assign ===");
                }
                System.out.println("=== SalesOrder assignedUsername set to: " + resolvedAssignUser.getUsername() + " ===");
                System.out.println("=== SalesOrder status set to: " + salesOrder.getStatus() + " ===");
            }
        }

        String normalizedCustomContractNo = normalizeBlankToNull(customContractNo);
        if (normalizedCustomContractNo != null) {
            contract.setContractNo(ensureContractNoAvailable(normalizedCustomContractNo, contract.getId()));
            contract.setName("销售合同-" + contract.getContractNo());
        } else if (isNewContract && (contract.getContractNo() == null || contract.getContractNo().isBlank())) {
            contract.setContractNo(generateContractNo(salesOrder, resolvedAssignUser));
            contract.setName("销售合同-" + contract.getContractNo());
        }
        
        System.out.println("=== Contract data before save ===");
        System.out.println("contract.getContractNo(): " + contract.getContractNo());
        System.out.println("contract.getPartyAName(): " + contract.getPartyAName());
        System.out.println("contract.getPartyBName(): " + contract.getPartyBName());
        System.out.println("contract.getProductModel(): " + contract.getProductModel());
        System.out.println("contract.getProductConfig(): " + contract.getProductConfig());
        System.out.println("contract.getWarrantyPeriod(): " + contract.getWarrantyPeriod());
        System.out.println("contract.getQuantity(): " + contract.getQuantity());
        System.out.println("contract.getUnitPrice(): " + contract.getUnitPrice());
        System.out.println("contract.getTotalAmount(): " + contract.getTotalAmount());
        System.out.println("contract.getAmount(): " + contract.getAmount());
        System.out.println("contract.getPlatformName(): " + contract.getPlatformName());
        System.out.println("contract.getPaymentMethod(): " + contract.getPaymentMethod());
        System.out.println("contract.getDeliveryAddress(): " + contract.getDeliveryAddress());
        System.out.println("contract.getDeliveryDate(): " + contract.getDeliveryDate());
        System.out.println("contract.getOrderDate(): " + contract.getOrderDate());
        System.out.println("contract.getDeliveryCycle(): " + contract.getDeliveryCycle());

        Contract savedContract = contractRepository.save(contract);

        List<Map<String, Object>> products = new ArrayList<>();
        if (salesOrder.getOrderDetails() != null && !salesOrder.getOrderDetails().isEmpty()) {
            try {
                Map<String, Object> details = objectMapper.readValue(salesOrder.getOrderDetails(), Map.class);
                if (details.get("products") != null) {
                    products = (List<Map<String, Object>>) details.get("products");
                    
                    System.out.println("=== Calculating deduction prices for products ===");
                    System.out.println("Global deductionRate: " + salesOrder.getDeductionRate());
                    
                    for (Map<String, Object> product : products) {
                        try {
                            String modelStr = getValue(product, "model", "");
                            if (modelStr != null && !modelStr.isBlank()) {
                                String fromProduct = productRepository.findFirstByModel(modelStr.trim())
                                        .map(Product::getName).filter(n -> n != null && !n.isBlank()).orElse("");
                                if (!fromProduct.isEmpty()) product.put("productName", fromProduct);
                            }
                            if (product.get("productName") == null || product.get("productName").toString().isBlank()) {
                                Object sku = product.get("platformSku");
                                if (sku != null && !sku.toString().isBlank()) product.put("productName", sku.toString());
                            }
                            if (salesOrder.getDeliveryPartyPurchasePrice() != null) {
                                java.math.BigDecimal unit = salesOrder.getDeliveryPartyPurchasePrice();
                                Object qtyObj = product.get("quantity");
                                int qty = (qtyObj != null && qtyObj.toString().matches("\\d+")) ? Integer.parseInt(qtyObj.toString()) : (salesOrder.getQuantity() != null ? salesOrder.getQuantity() : 1);
                                java.math.BigDecimal total = unit.multiply(new java.math.BigDecimal(qty));
                                product.put("taxIncludedPrice", unit.setScale(2, java.math.RoundingMode.HALF_UP).toString());
                                product.put("taxIncludedTotal", total.setScale(2, java.math.RoundingMode.HALF_UP).toString());
                                System.out.println("  Product price from deliveryPartyPurchasePrice: unit=" + unit + ", total=" + total);
                            } else {
                                String taxIncludedPriceStr = getValue(product, "taxIncludedPrice", "0");
                                String taxIncludedTotalStr = getValue(product, "taxIncludedTotal", "0");
                                String deductionRateStr = getValue(product, "deductionRate", salesOrder.getDeductionRate() != null ? salesOrder.getDeductionRate().toString() : "2");
                                java.math.BigDecimal taxIncludedPrice = new java.math.BigDecimal(taxIncludedPriceStr);
                                java.math.BigDecimal taxIncludedTotal = new java.math.BigDecimal(taxIncludedTotalStr);
                                java.math.BigDecimal rate = new java.math.BigDecimal(deductionRateStr.replace("%", "")).divide(new java.math.BigDecimal("100"));
                                java.math.BigDecimal deliveryPartyUnitPrice = taxIncludedPrice.multiply(java.math.BigDecimal.ONE.subtract(rate));
                                java.math.BigDecimal deliveryPartyTotalPrice = taxIncludedTotal.multiply(java.math.BigDecimal.ONE.subtract(rate));
                                product.put("taxIncludedPrice", deliveryPartyUnitPrice.setScale(2, java.math.RoundingMode.HALF_UP).toString());
                                product.put("taxIncludedTotal", deliveryPartyTotalPrice.setScale(2, java.math.RoundingMode.HALF_UP).toString());
                            }
                            if (salesOrder.getDeliveryDate() != null && salesOrder.getOrderDate() != null) {
                                long days = java.time.temporal.ChronoUnit.DAYS.between(salesOrder.getOrderDate(), salesOrder.getDeliveryDate());
                                product.put("deliveryCycle", String.valueOf(days));
                            }
                        } catch (Exception e) {
                            System.err.println("Error calculating price for product: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing order details: " + e.getMessage());
            }
        }

        if (templateUrl != null && !templateUrl.isEmpty()) {
            // 生成文档前兜底：若乙方名称为空则从订单交付方回填，便于追踪与修复
            ensureContractPartyBFromSalesOrder(savedContract, salesOrder);
            String generatedUrl;
            try {
                // 指派即生成「带甲方章」的合同（合作管理-合同管理可见），乙方签署后再生成带双章
                if (products != null && !products.isEmpty()) {
                    generatedUrl = documentService.generateContractDocumentWithProducts(savedContract, templateUrl, products, "A");
                } else {
                    generatedUrl = documentService.generateContractDocumentWithPartySeal(savedContract, templateUrl, "A");
                }
            } catch (Exception e) {
                System.err.println("合同文档生成失败（模板或甲方章等）: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("合同文档生成失败，请检查模板路径及甲方/乙方名称: " + e.getMessage(), e);
            }
            savedContract.setGeneratedUrl(generatedUrl);
            savedContract.setPartyASigned(true);
            savedContract.setPartyASignedTime(LocalDateTime.now());
            savedContract.setStatus("待签署");
            savedContract = contractRepository.save(savedContract);
            notifyContractWatchlistIfNeeded(savedOrderRefForNotify(salesOrder),
                    savedContract, "指派完成：已生成带甲方章合同，待乙方签署");
        }

        return savedContract;
    }

    @Transactional
    public Contract createContractFromMasterSelection(List<Long> salesOrderIds,
                                                      String templateUrl,
                                                      String partyBRepresentative,
                                                      String platformName,
                                                      String paymentMethod,
                                                      String deliveryPartyFromRequest,
                                                      java.math.BigDecimal deliveryPartyPurchasePriceFromRequest,
                                                      java.util.Map<Long, java.math.BigDecimal> deliveryPartyPurchasePriceByOrderId,
                                                      java.math.BigDecimal deductionRateFromRequest,
                                                      String offlineSalesFromRequest,
                                                      String customContractNo,
                                                      String customPurchaseOrderNo) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一条销售订单进行合并指派");
        }
        templateUrl = resolveTemplateUrlInput(templateUrl);
        if (templateUrl == null || templateUrl.isBlank()) {
            throw new IllegalArgumentException("请先选择合同模板");
        }

        java.util.LinkedHashSet<Long> uniqueIds = new java.util.LinkedHashSet<>();
        for (Long id : salesOrderIds) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("请选择有效的销售订单");
        }

        java.util.Map<Long, SalesOrder> orderMap = salesOrderRepository.findAllById(uniqueIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrder::getId, order -> order));
        List<SalesOrder> selectedOrders = new ArrayList<>();
        for (Long id : uniqueIds) {
            SalesOrder order = orderMap.get(id);
            if (order == null) {
                throw new IllegalArgumentException("销售订单不存在: " + id);
            }
            selectedOrders.add(order);
        }

        if (selectedOrders.isEmpty()) {
            throw new IllegalArgumentException("请选择有效的销售订单");
        }

        String portalName = getCurrentUsernameSafe();
        User portalUser = (portalName != null && !portalName.isBlank())
                ? userRepository.findByUsername(portalName.trim()).orElse(null) : null;
        if (portalUser == null) {
            throw new IllegalArgumentException("请先登录后再操作");
        }

        Long masterId = null;
        for (SalesOrder order : selectedOrders) {
            if (!canCurrentUserMutateContractForSalesOrder(portalUser, order.getId())) {
                throw new IllegalArgumentException("无权为所选销售订单执行合并指派");
            }
            if (!canOrderEnterMergeAssign(order, portalUser)) {
                throw new IllegalArgumentException("订单「" + order.getOmsOrderNo() + " / " + order.getModel() + "」当前状态不支持合并指派/转派");
            }
            if (order.getMasterId() == null) {
                throw new IllegalArgumentException("订单「" + order.getOmsOrderNo() + "」尚未挂接主单，暂不可合并指派");
            }
            if (masterId == null) {
                masterId = order.getMasterId();
            } else if (!masterId.equals(order.getMasterId())) {
                throw new IllegalArgumentException("合并指派只允许选择同一主单下的商品行");
            }
        }

        SalesOrder firstOrder = selectedOrders.get(0);
        String effectiveDeliveryParty = normalizeBlankToNull(deliveryPartyFromRequest);
        if (effectiveDeliveryParty == null) {
            effectiveDeliveryParty = normalizeBlankToNull(firstOrder.getDeliveryParty());
        }
        if (effectiveDeliveryParty == null) {
            throw new IllegalArgumentException("请先选择交付方后再执行合并指派");
        }

        AssigneeResolution assigneeResolution = resolveAssigneeByDeliveryPartyForPreview(effectiveDeliveryParty, partyBRepresentative);
        User resolvedAssignUser = assigneeResolution.user;
        PartnerInfo resolvedPartnerInfo = assigneeResolution.partnerInfo;
        String requestPaymentMethod = normalizeBlankToNull(paymentMethod);
        String requestOfflineSales = normalizeBlankToNull(offlineSalesFromRequest);
        String mergeSelectionKey = buildMergeSelectionKey(uniqueIds);

        List<SalesOrder> updatedOrders = new ArrayList<>();
        boolean singleSelection = selectedOrders.size() == 1;
        for (SalesOrder sourceOrder : selectedOrders) {
            SalesOrder order = salesOrderRepository.findById(sourceOrder.getId())
                    .orElseThrow(() -> new IllegalArgumentException("销售订单不存在: " + sourceOrder.getId()));
            java.math.BigDecimal perOrderPurchasePrice = deliveryPartyPurchasePriceByOrderId != null
                    ? deliveryPartyPurchasePriceByOrderId.get(order.getId())
                    : null;
            if (perOrderPurchasePrice != null && perOrderPurchasePrice.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                applyDeliveryPartyPurchasePrice(order, perOrderPurchasePrice);
            } else if (singleSelection
                    && deliveryPartyPurchasePriceFromRequest != null
                    && deliveryPartyPurchasePriceFromRequest.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                applyDeliveryPartyPurchasePrice(order, deliveryPartyPurchasePriceFromRequest);
            }
            if (deductionRateFromRequest != null && deductionRateFromRequest.compareTo(java.math.BigDecimal.ZERO) >= 0) {
                order.setDeductionRate(deductionRateFromRequest);
            }
            order.setDeliveryParty(effectiveDeliveryParty);
            if (requestPaymentMethod != null) {
                order.setPaymentMethod(requestPaymentMethod);
            }
            if (requestOfflineSales != null) {
                order.setOfflineSales(requestOfflineSales);
            }
            String previousAssignee = normalizeBlankToNull(order.getAssignedUsername());
            if (previousAssignee != null && !previousAssignee.equals(resolvedAssignUser.getUsername())) {
                order.setPreviousAssignedUsername(previousAssignee);
            }
            order.setAssignedUsername(resolvedAssignUser.getUsername() != null ? resolvedAssignUser.getUsername().trim() : null);
            order.setAssignTime(LocalDateTime.now());
            if ("已退回".equals(order.getStatus())) {
                order.setStatus("待确认订单");
            } else {
                order.setStatus("待合同盖章");
            }
            SalesOrder savedOrder = salesOrderRepository.save(order);
            updatedOrders.add(savedOrder);
        }

        List<User> assignerUsers = new ArrayList<>();
        for (SalesOrder order : updatedOrders) {
            User assignerUser = resolveAssignerUser(order, resolvedAssignUser);
            if (assignerUser != null && assignerUser.getId() != null) {
                assignerUsers.add(assignerUser);
            }
            boolean skipNextLevel = !isChainOrder(order) && isSourceFactory(resolvedAssignUser);
            if (!skipNextLevel) {
                createOrUpdateNextLevelSalesOrder(order, resolvedAssignUser, assignerUser);
            }
        }
        notifyRexiangAssignChangeBatch(updatedOrders, resolvedAssignUser);

        User purchaseCreator = assignerUsers.stream()
                .filter(user -> user != null && user.getId() != null)
                .findFirst()
                .orElseGet(() -> {
                    User creator = firstOrder.getCreatedBy() != null
                            ? userRepository.findById(firstOrder.getCreatedBy()).orElse(null)
                            : null;
                    return creator != null ? creator : portalUser;
                });
        createOrUpdateMergedPurchaseOrder(updatedOrders, purchaseCreator, effectiveDeliveryParty, mergeSelectionKey, customPurchaseOrderNo);

        String partyATitle = resolvePartyATitleForContract(firstOrder);
        String settlementParty = resolveSettlementParty(firstOrder, platformName);

        Contract contract = contractRepository
                .findFirstByMasterIdAndSalesIdAndMergeSelectionKeyOrderByIdDesc(masterId, resolvedAssignUser.getId(), mergeSelectionKey)
                .orElseGet(Contract::new);
        boolean isNewContract = contract.getId() == null;
        contract.setPartyA(partyATitle);
        contract.setPartyB(effectiveDeliveryParty);
        contract.setSalesOrderId(firstOrder.getId());
        contract.setMasterId(masterId);
        contract.setAllocationId(null);
        contract.setContractScope("MASTER_SELECTION");
        contract.setMergeSelectionKey(mergeSelectionKey);
        contract.setMergedSalesOrderIds(joinOrderIds(updatedOrders));
        contract.setSalesId(resolvedAssignUser.getId());
        contract.setPartyAName(partyATitle);
        contract.setPartyBName(effectiveDeliveryParty);
        contract.setSalesName(resolveContractSalesName(firstOrder, partyATitle));
        contract.setPartyBRepresentative(resolvePartnerContact(resolvedPartnerInfo, resolvedAssignUser));
        contract.setProductName(joinDistinctTexts(updatedOrders.stream().map(this::resolveProductNameForOrder).toList(), " / "));
        contract.setProductModel(joinDistinctTexts(updatedOrders.stream().map(SalesOrder::getModel).toList(), " / "));
        contract.setMaterialNo(joinDistinctTexts(updatedOrders.stream().map(SalesOrder::getMaterialNo).toList(), " / "));
        contract.setProductConfig(joinDistinctTexts(updatedOrders.stream().map(SalesOrder::getProductConfig).toList(), " / "));
        contract.setWarrantyPeriod(joinDistinctTexts(updatedOrders.stream().map(SalesOrder::getWarrantyPeriod).toList(), " / "));
        int totalQuantity = updatedOrders.stream().map(SalesOrder::getQuantity).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        contract.setQuantity(totalQuantity > 0 ? totalQuantity : null);
        java.math.BigDecimal contractTotal = updatedOrders.stream()
                .map(this::resolveContractTotalAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        contract.setTotalAmount(contractTotal);
        contract.setAmount(contractTotal);
        contract.setAmountCn(MoneyUtil.convertToChinese(contractTotal));
        if (contract.getQuantity() != null && contract.getQuantity() > 0) {
            contract.setUnitPrice(contractTotal.divide(new java.math.BigDecimal(contract.getQuantity()), 2, java.math.RoundingMode.HALF_UP));
        } else {
            contract.setUnitPrice(java.math.BigDecimal.ZERO);
        }
        contract.setSettlementPartyName(settlementParty);
        contract.setPlatformName(settlementParty != null ? settlementParty : normalizeBlankToNull(firstOrder.getPlatformName()));
        contract.setPartyAOrderNo(joinDistinctTexts(updatedOrders.stream().map(SalesOrder::getPlatformOrderNo).toList(), " / "));
        contract.setPaymentMethod(requestPaymentMethod != null ? requestPaymentMethod : firstOrder.getPaymentMethod());
        contract.setDeliveryAddress(joinDistinctTexts(updatedOrders.stream().map(SalesOrder::getReceiverAddress).toList(), " / "));
        contract.setDeliveryDate(updatedOrders.stream().map(SalesOrder::getDeliveryDate).filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(firstOrder.getDeliveryDate()));
        contract.setOrderDate(updatedOrders.stream().map(SalesOrder::getOrderDate).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(firstOrder.getOrderDate()));
        if (contract.getDeliveryDate() != null && contract.getOrderDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(contract.getOrderDate(), contract.getDeliveryDate());
            contract.setDeliveryCycle((int) days);
        }
        contract.setSignDate(LocalDate.now());
        contract.setStatus("草稿");
        contract.setTemplateUrl(templateUrl);
        applyPartyAInfo(contract, partyATitle, firstOrder);
        applyPartyBInfo(contract, resolvedPartnerInfo, resolvedAssignUser);
        String normalizedCustomContractNo = normalizeBlankToNull(customContractNo);
        if (normalizedCustomContractNo != null) {
            contract.setContractNo(ensureContractNoAvailable(normalizedCustomContractNo, contract.getId()));
            contract.setName("销售合同-" + contract.getContractNo());
        } else if (isNewContract && (contract.getContractNo() == null || contract.getContractNo().isBlank())) {
            contract.setContractNo(generateContractNo(firstOrder, resolvedAssignUser));
            contract.setName("销售合同-" + contract.getContractNo());
        }

        Contract savedContract = contractRepository.save(contract);
        List<Map<String, Object>> products = buildProductsForOrders(updatedOrders);
        ensureContractPartyBFromSalesOrder(savedContract, firstOrder);
        String generatedUrl;
        try {
            if (products != null && !products.isEmpty()) {
                generatedUrl = documentService.generateContractDocumentWithProducts(savedContract, templateUrl, products, "A");
            } else {
                generatedUrl = documentService.generateContractDocumentWithPartySeal(savedContract, templateUrl, "A");
            }
        } catch (Exception e) {
            throw new RuntimeException("合同文档生成失败，请检查模板路径及甲方/乙方名称: " + e.getMessage(), e);
        }
        savedContract.setGeneratedUrl(generatedUrl);
        savedContract.setPartyASigned(true);
        savedContract.setPartyASignedTime(LocalDateTime.now());
        savedContract.setStatus("待签署");
        savedContract = contractRepository.save(savedContract);
        notifyContractWatchlistIfNeeded(savedOrderRefForNotify(firstOrder),
                savedContract, "合并指派完成：已生成聚合采购单与带甲方章合同，待乙方签署");
        return savedContract;
    }

    /** 通知用：尽量使用库中最新订单行，避免内存实体字段滞后 */
    private SalesOrder savedOrderRefForNotify(SalesOrder inMemory) {
        if (inMemory != null && inMemory.getId() != null) {
            return salesOrderRepository.findById(inMemory.getId()).orElse(inMemory);
        }
        return inMemory;
    }

    /** 合同任一方名称包含飞础科智慧科技（上海）有限公司或上海热像科技股份有限公司时推送钉钉 */
    private boolean nameInvolvesWatchlistCompany(String name) {
        if (name == null || name.isBlank()) return false;
        String n = name.trim();
        return n.contains(FEICHUKE_TITLE) || n.contains(REXIANG_TITLE);
    }

    private boolean contractInvolvesWatchlistParty(Contract c) {
        if (c == null) return false;
        return nameInvolvesWatchlistCompany(c.getPartyAName()) || nameInvolvesWatchlistCompany(c.getPartyBName());
    }

    private String resolveNotifyOperatorDisplay() {
        String u = getCurrentUsernameSafe();
        if (u == null || u.isBlank()) return "系统";
        return userRepository.findByUsername(u.trim())
                .map(x -> x.getRealName() != null && !x.getRealName().isBlank() ? x.getRealName().trim() : u)
                .orElse(u);
    }

    private List<String> resolveNotifyOperatorAtMobiles(SalesOrder salesOrder, Contract contract) {
        if (contract != null && "已签署".equals(safe(contract.getStatus()))) {
            return resolveConfiguredUsersAtMobiles(contractSignedExtraAtRealNames);
        }
        if (contract == null || !"待签署".equals(safe(contract.getStatus())) || Boolean.TRUE.equals(contract.getPartyBSigned())) {
            return Collections.emptyList();
        }
        String currentUsername = getCurrentUsernameSafe();
        if (currentUsername != null && !currentUsername.isBlank()) {
            Optional<User> currentUser = userRepository.findByUsername(currentUsername.trim());
            if (currentUser.isPresent()) {
                String currentPhone = currentUser.get().getPhone();
                if (currentPhone != null && !currentPhone.isBlank()) {
                    return List.of(currentPhone.trim());
                }
            }
        }
        if (salesOrder != null && salesOrder.getCreatedBy() != null) {
            Optional<User> creator = userRepository.findById(salesOrder.getCreatedBy());
            if (creator.isPresent() && creator.get().getPhone() != null && !creator.get().getPhone().isBlank()) {
                return List.of(creator.get().getPhone().trim());
            }
        }
        return parseConfiguredMobiles(contractSignFallbackAtMobiles);
    }

    private List<String> resolveConfiguredUsersAtMobiles(String rawRealNames) {
        LinkedHashSet<String> mobiles = new LinkedHashSet<>();
        for (String realName : parseConfiguredMobiles(rawRealNames)) {
            for (User user : userRepository.findByRealName(realName)) {
                if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                    continue;
                }
                String phone = user.getPhone();
                if (phone != null && !phone.isBlank()) {
                    mobiles.add(phone.trim());
                }
            }
        }
        return mobiles.isEmpty() ? Collections.emptyList() : new ArrayList<>(mobiles);
    }

    private List<String> parseConfiguredMobiles(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split("[,，]"))
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * 飞础科/热像相关合同的签署与盖章动态推送到钉钉（与订单指派通知同一机器人配置）。
     */
    private void notifyContractWatchlistIfNeeded(SalesOrder salesOrder, Contract contract, String eventSummary) {
        try {
            if (contract == null || !contractInvolvesWatchlistParty(contract)) return;
            SalesOrder so = salesOrder;
            if (so != null && so.getId() != null) {
                so = salesOrderRepository.findById(so.getId()).orElse(so);
            }
            String title = "📝 合同签署提醒（飞础科/热像）";
            String text = "## 📝 合同签署提醒（飞础科/热像）\n\n"
                    + "**说明：** 合同甲方或乙方涉及飞础科智慧科技（上海）有限公司 / 上海热像科技股份有限公司\n\n"
                    + "**动态：** " + (eventSummary != null ? eventSummary : "-") + "\n\n"
                    + "**合同编号：** " + safe(contract.getContractNo()) + "\n\n"
                    + "**合同状态：** " + safe(contract.getStatus()) + "\n\n"
                    + "**甲方：** " + safe(contract.getPartyAName()) + "\n\n"
                    + "**乙方：** " + safe(contract.getPartyBName()) + "\n\n"
                    + "**OMS订单号：** " + safe(so != null ? so.getOmsOrderNo() : null) + "\n\n"
                    + "**甲方订单号：** " + safe(so != null ? so.getPlatformOrderNo() : null) + "\n\n"
                    + "**操作人：** " + safe(resolveNotifyOperatorDisplay()) + "\n\n"
                    + "---\n"
                    + "*来自 OMS 订单系统*";
            List<String> atMobiles = resolveNotifyOperatorAtMobiles(so, contract);
            dingTalkService.sendMarkdownMessage(title, text, atMobiles.isEmpty() ? null : atMobiles);
        } catch (Exception ex) {
            System.err.println("发送飞础科/热像合同签署钉钉提醒失败: " + ex.getMessage());
        }
    }

    /**
     * 钉钉通知「甲方抬头」与销售列表对齐：代运营场景下 party_a_title 常为飞础科，真实客户看在 platform_name。
     */
    private String resolveNotifyCustomerPartyATitle(SalesOrder o) {
        if (o == null) {
            return "-";
        }
        String platform = o.getPlatformName() == null ? "" : o.getPlatformName().trim();
        String partyA = o.getPartyATitle() == null ? "" : o.getPartyATitle().trim();
        if (!partyA.isEmpty() && partyA.contains(FEICHUKE_TITLE) && !platform.isEmpty()) {
            return platform;
        }
        if (!partyA.isEmpty()) {
            return partyA;
        }
        if (!platform.isEmpty()) {
            return platform;
        }
        String op = o.getOperationEntityTitle() == null ? "" : o.getOperationEntityTitle().trim();
        return !op.isEmpty() ? op : "-";
    }

    /**
     * 与 {@link com.oms.controller.SalesOrderController} 列表「甲方抬头」单条解析一致：party_a_title → platform_name → operation_entity_title。
     */
    private String displayPartyATitleLikeSalesList(SalesOrder order) {
        if (order == null) {
            return "-";
        }
        String stored = (order.getPartyATitle() != null && !order.getPartyATitle().isBlank())
                ? order.getPartyATitle().trim() : null;
        if (stored == null && order.getPlatformName() != null && !order.getPlatformName().isBlank()) {
            stored = order.getPlatformName().trim();
        }
        if (stored == null && order.getOperationEntityTitle() != null && !order.getOperationEntityTitle().isBlank()) {
            stored = order.getOperationEntityTitle().trim();
        }
        return stored != null ? stored : "-";
    }

    /**
     * 指派/转派钉钉通知应使用「主单」字段：链式单（CHAIN_FROM）上常为飞础科/热像，真实客户甲方在源头主单，
     * 与销售列表 {@code buildUnifiedDisplayPartyAForPage} 对非链式单行的归一逻辑一致。
     */
    private SalesOrder resolveCanonicalMainOrderForAssignNotify(SalesOrder order) {
        if (order == null) {
            return null;
        }
        String po = order.getPurchaseOrderNo();
        if (po != null && po.startsWith("CHAIN_FROM:")) {
            try {
                long mainId = Long.parseLong(po.substring("CHAIN_FROM:".length()).trim());
                return salesOrderRepository.findById(mainId).orElse(order);
            } catch (NumberFormatException e) {
                return order;
            }
        }
        String oms = order.getOmsOrderNo();
        if (oms == null || oms.isBlank()) {
            return order;
        }
        List<SalesOrder> group = salesOrderRepository.findAllByOmsOrderNoOrderByIdAsc(oms.trim());
        if (group == null || group.size() <= 1) {
            return order;
        }
        List<SalesOrder> mains = group.stream()
                .filter(o -> o.getPurchaseOrderNo() == null || !o.getPurchaseOrderNo().startsWith("CHAIN_FROM:"))
                .toList();
        if (!mains.isEmpty()) {
            return mains.get(0);
        }
        return group.get(0);
    }

    /**
     * 钉钉通知金额与销售列表「含税总价」一致：优先 含税单价×数量，避免 taxIncludedTotal 被链式/回写污染。
     */
    private java.math.BigDecimal resolveNotifyOrderAmount(SalesOrder o) {
        if (o == null) {
            return null;
        }
        if (o.getTaxIncludedPrice() != null && o.getQuantity() != null && o.getQuantity() > 0) {
            try {
                return o.getTaxIncludedPrice().multiply(new java.math.BigDecimal(o.getQuantity()));
            } catch (Exception ignored) {
                // fall through
            }
        }
        return o.getTaxIncludedTotal();
    }

    /** 指派至热像：以被指派用户公司抬头或订单「交付方」字段判定，避免用户表公司未填时漏发详情 */
    private boolean useRexiangRichAssignNotify(User assignee, SalesOrder order) {
        if (assignee != null) {
            String ct = assignee.getCompanyTitle();
            if (ct != null && ct.contains(REXIANG_TITLE)) return true;
        }
        if (order != null && order.getDeliveryParty() != null && order.getDeliveryParty().contains(REXIANG_TITLE)) {
            return true;
        }
        return false;
    }

    private String moneyOrDash(java.math.BigDecimal v) {
        return v != null ? "¥" + v.toPlainString() : "-";
    }

    private String deliveryPurchaseTotalLine(SalesOrder o) {
        if (o == null || o.getDeliveryPartyPurchasePrice() == null || o.getQuantity() == null || o.getQuantity() <= 0) {
            return "-";
        }
        try {
            java.math.BigDecimal t = o.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(o.getQuantity()));
            return "¥" + t.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return "-";
        }
    }

    /**
     * 指派/转派到上海热像时，需要把上游被指派方对应的公司抬头展示出来。
     * 优先取 PartnerInfo.title/name，其次取用户 companyTitle，最后回退用户名。
     */
    private String resolveNotifyUpstreamPartyTitle(String previousAssignee) {
        if (previousAssignee == null || previousAssignee.isBlank()) {
            return "-";
        }
        String username = previousAssignee.trim();
        List<PartnerInfo> partners = partnerInfoRepository.findByUsername(username);
        if (partners != null && !partners.isEmpty()) {
            PartnerInfo latest = partners.stream()
                    .max(java.util.Comparator.comparingLong(p -> p.getId() != null ? p.getId() : 0L))
                    .orElse(null);
            if (latest != null) {
                String title = latest.getTitle() != null ? latest.getTitle().trim() : "";
                if (!title.isEmpty()) return title;
                String name = latest.getName() != null ? latest.getName().trim() : "";
                if (!name.isEmpty()) return name;
            }
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.getCompanyTitle() != null && !user.getCompanyTitle().isBlank()) {
            return user.getCompanyTitle().trim();
        }
        return username;
    }

    private void notifyRexiangAssignChange(SalesOrder salesOrder, User assignee, String previousAssignee) {
        try {
            if (salesOrder == null || assignee == null) return;
            // 用库中最新一行拼装通知，避免内存实体字段滞后
            SalesOrder notifyOrder = salesOrder.getId() != null
                    ? salesOrderRepository.findById(salesOrder.getId()).orElse(salesOrder)
                    : salesOrder;
            SalesOrder canonicalForPartyA = resolveCanonicalMainOrderForAssignNotify(notifyOrder);
            String operatorCompanyTitle = resolveCurrentOperatorCompanyTitle();
            if (operatorCompanyTitle == null) {
                operatorCompanyTitle = displayPartyATitleLikeSalesList(canonicalForPartyA);
            }

            boolean isReassign = previousAssignee != null
                    && !previousAssignee.isBlank()
                    && !previousAssignee.trim().equalsIgnoreCase(
                    assignee.getUsername() == null ? "" : assignee.getUsername().trim()
            );
            String action = isReassign ? "转派" : "指派";
            String assigneeCompany = assignee.getCompanyTitle() == null ? "" : assignee.getCompanyTitle().trim();
            String model = notifyOrder.getModel() != null ? notifyOrder.getModel() : "-";
            java.math.BigDecimal amt = resolveNotifyOrderAmount(notifyOrder);
            String amount = amt != null ? "¥" + amt.toPlainString() : "-";
            String title = "📣 订单" + action + "通知";
            String text;
            if (useRexiangRichAssignNotify(assignee, notifyOrder)) {
                String upstreamPartyTitle = isReassign ? resolveNotifyUpstreamPartyTitle(previousAssignee) : "-";
                java.math.BigDecimal taxTotal = notifyOrder.getTaxIncludedTotal();
                if (taxTotal == null && notifyOrder.getTaxIncludedPrice() != null && notifyOrder.getQuantity() != null && notifyOrder.getQuantity() > 0) {
                    try {
                        taxTotal = notifyOrder.getTaxIncludedPrice().multiply(new java.math.BigDecimal(notifyOrder.getQuantity()));
                    } catch (Exception ignored) {
                        taxTotal = null;
                    }
                }
                text = "## 📣 订单" + action + "通知（上海热像）\n\n"
                        + "**OMS订单号：** " + safe(notifyOrder.getOmsOrderNo()) + "\n\n"
                        + "**甲方订单号：** " + safe(notifyOrder.getPlatformOrderNo()) + "\n\n"
                        + "**当前操作方所属抬头：** " + safe(operatorCompanyTitle) + "\n\n"
                        + (isReassign ? "**上游被指派方甲方：** " + safe(upstreamPartyTitle) + "\n\n" : "")
                        + "**乙方/交付方：** " + safe(notifyOrder.getDeliveryParty()) + "\n\n"
                        + "**型号：** " + model + "\n\n"
                        + "**数量：** " + (notifyOrder.getQuantity() != null ? String.valueOf(notifyOrder.getQuantity()) : "-") + "\n\n"
                        + "**支付方式：** " + safe(notifyOrder.getPaymentMethod()) + "\n\n"
                        + "**订单类型：** " + safe(notifyOrder.getOrderType()) + "\n\n"
                        + "**" + action + "至：** " + safe(assignee.getUsername())
                        + (assigneeCompany.isBlank() ? "" : "（" + assigneeCompany + "）") + "\n\n"
                        + (isReassign ? "**原被指派方账号：** " + safe(previousAssignee) + "\n\n" : "")
                        + "---\n"
                        + "*来自 OMS 订单系统*";
            } else {
                text = "## 📣 订单" + action + "通知\n\n"
                        + "**OMS订单号：** " + safe(notifyOrder.getOmsOrderNo()) + "\n\n"
                        + "**甲方订单号：** " + safe(notifyOrder.getPlatformOrderNo()) + "\n\n"
                        + "**当前操作方所属抬头：** " + safe(operatorCompanyTitle) + "\n\n"
                        + "**型号：** " + model + "\n\n"
                        + "**金额：** " + amount + "\n\n"
                        + "**交付方：** " + safe(notifyOrder.getDeliveryParty()) + "\n\n"
                        + "**" + action + "至：** " + safe(assignee.getUsername())
                        + (assigneeCompany.isBlank() ? "" : "（" + assigneeCompany + "）") + "\n\n"
                        + (isReassign ? "**原被指派方：** " + safe(previousAssignee) + "\n\n" : "")
                        + "---\n"
                        + "*来自 OMS 订单系统*";
            }
            dingTalkService.sendMarkdownMessage(title, text);
        } catch (Exception ex) {
            System.err.println("发送订单指派钉钉通知失败: " + ex.getMessage());
        }
    }

    private void notifyRexiangAssignChangeBatch(List<SalesOrder> orders, User assignee) {
        if (orders == null || orders.isEmpty() || assignee == null) {
            return;
        }
        if (orders.size() == 1) {
            SalesOrder only = orders.get(0);
            notifyRexiangAssignChange(only, assignee, only != null ? only.getPreviousAssignedUsername() : null);
            return;
        }
        try {
            List<SalesOrder> normalized = orders.stream()
                    .map(this::savedOrderRefForNotify)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (normalized.isEmpty()) {
                return;
            }
            SalesOrder first = normalized.get(0);
            SalesOrder canonicalForPartyA = resolveCanonicalMainOrderForAssignNotify(first);
            String operatorCompanyTitle = resolveCurrentOperatorCompanyTitle();
            if (operatorCompanyTitle == null) {
                operatorCompanyTitle = displayPartyATitleLikeSalesList(canonicalForPartyA);
            }
            LinkedHashSet<String> previousAssignees = normalized.stream()
                    .map(SalesOrder::getPreviousAssignedUsername)
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
            boolean isReassign = !previousAssignees.isEmpty();
            String action = isReassign ? "转派" : "指派";
            String assigneeCompany = assignee.getCompanyTitle() == null ? "" : assignee.getCompanyTitle().trim();
            int totalQty = normalized.stream()
                    .map(SalesOrder::getQuantity)
                    .filter(q -> q != null && q > 0)
                    .reduce(0, Integer::sum);
            java.math.BigDecimal totalAmount = normalized.stream()
                    .map(this::resolveNotifyOrderAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            String modelLines = normalized.stream()
                    .map(order -> "- " + safe(order.getModel())
                            + " × " + (order.getQuantity() != null ? order.getQuantity() : 1)
                            + "，" + moneyOrDash(resolveNotifyOrderAmount(order)))
                    .collect(java.util.stream.Collectors.joining("\n"));
            String title = "📣 订单" + action + "通知";
            String text;
            if (useRexiangRichAssignNotify(assignee, first)) {
                String upstreamPartyTitle = previousAssignees.size() == 1
                        ? resolveNotifyUpstreamPartyTitle(previousAssignees.iterator().next())
                        : previousAssignees.stream().map(this::resolveNotifyUpstreamPartyTitle).distinct().collect(java.util.stream.Collectors.joining(" / "));
                text = "## 📣 订单" + action + "通知（合并）\n\n"
                        + "**OMS订单号：** " + safe(first.getOmsOrderNo()) + "\n\n"
                        + "**甲方订单号：** " + safe(first.getPlatformOrderNo()) + "\n\n"
                        + "**当前操作方所属抬头：** " + safe(operatorCompanyTitle) + "\n\n"
                        + (isReassign ? "**上游被指派方甲方：** " + safe(upstreamPartyTitle) + "\n\n" : "")
                        + "**乙方/交付方：** " + safe(first.getDeliveryParty()) + "\n\n"
                        + "**商品行数：** " + normalized.size() + "\n\n"
                        + "**合计数量：** " + totalQty + "\n\n"
                        + "**支付方式：** " + safe(first.getPaymentMethod()) + "\n\n"
                        + "**订单类型：** " + safe(first.getOrderType()) + "\n\n"
                        + "**商品型号：**\n" + modelLines + "\n\n"
                        + "**" + action + "至：** " + safe(assignee.getUsername())
                        + (assigneeCompany.isBlank() ? "" : "（" + assigneeCompany + "）") + "\n\n"
                        + (isReassign ? "**原被指派方账号：** " + String.join(" / ", previousAssignees) + "\n\n" : "")
                        + "---\n"
                        + "*来自 OMS 订单系统*";
            } else {
                text = "## 📣 订单" + action + "通知（合并）\n\n"
                        + "**OMS订单号：** " + safe(first.getOmsOrderNo()) + "\n\n"
                        + "**甲方订单号：** " + safe(first.getPlatformOrderNo()) + "\n\n"
                        + "**当前操作方所属抬头：** " + safe(operatorCompanyTitle) + "\n\n"
                        + "**商品行数：** " + normalized.size() + "\n\n"
                        + "**合计数量：** " + totalQty + "\n\n"
                        + "**合计金额：** " + moneyOrDash(totalAmount) + "\n\n"
                        + "**型号明细：**\n" + modelLines + "\n\n"
                        + "**交付方：** " + safe(first.getDeliveryParty()) + "\n\n"
                        + "**" + action + "至：** " + safe(assignee.getUsername())
                        + (assigneeCompany.isBlank() ? "" : "（" + assigneeCompany + "）") + "\n\n"
                        + (isReassign ? "**原被指派方：** " + String.join(" / ", previousAssignees) + "\n\n" : "")
                        + "---\n"
                        + "*来自 OMS 订单系统*";
            }
            dingTalkService.sendMarkdownMessage(title, text);
        } catch (Exception ex) {
            System.err.println("发送聚合订单指派钉钉通知失败: " + ex.getMessage());
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String resolveCurrentOperatorCompanyTitle() {
        String currentUsername = getCurrentUsernameSafe();
        if (currentUsername == null || currentUsername.isBlank()) {
            return null;
        }
        User currentUser = userRepository.findByUsername(currentUsername.trim()).orElse(null);
        return currentUser != null ? normalizeBlankToNull(currentUser.getCompanyTitle()) : null;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveTemplateUrlInput(String templateUrl) {
        if (templateUrl != null && !templateUrl.isBlank() && !templateUrl.contains("/") && !templateUrl.startsWith("http")) {
            String name = templateUrl.trim();
            List<ContractTemplate> all = contractTemplateRepository.findAll();
            ContractTemplate match = all.stream()
                    .filter(t -> name.equals(t.getTemplateName() != null ? t.getTemplateName().trim() : null))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                match = all.stream()
                        .filter(t -> t.getTemplateName() != null && t.getTemplateName().contains(name))
                        .findFirst()
                        .orElse(null);
            }
            if (match != null && match.getTemplateUrl() != null && !match.getTemplateUrl().isBlank()) {
                String resolved = match.getTemplateUrl().trim();
                if (resolved.startsWith("http")) return resolved;
                if (resolved.startsWith("/")) return resolved;
                return "/uploads/" + resolved;
            }
        }
        return templateUrl;
    }

    private boolean canOrderEnterMergeAssign(SalesOrder order, User currentUser) {
        if (order == null || currentUser == null) {
            return false;
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole()) && !subjectAccountGroupService.isPrimaryActor(currentUser.getUsername())) {
            return false;
        }
        String status = safe(order.getStatus());
        List<Long> sharedUserIds = subjectAccountGroupService.sharedUserIds(currentUser.getUsername());
        List<String> sharedUsernames = subjectAccountGroupService.sharedUsernames(currentUser.getUsername());
        if ("待指派".equals(status)) {
            return order.getCreatedBy() != null
                    && ((!sharedUserIds.isEmpty() && sharedUserIds.contains(order.getCreatedBy()))
                    || order.getCreatedBy().equals(currentUser.getId()));
        }
        if ("已退回".equals(status)) {
            return order.getCreatedBy() != null
                    && ((!sharedUserIds.isEmpty() && sharedUserIds.contains(order.getCreatedBy()))
                    || order.getCreatedBy().equals(currentUser.getId()));
        }
        String username = normalizeBlankToNull(currentUser.getUsername());
        return username != null
                && normalizeBlankToNull(order.getAssignedUsername()) != null
                && ((!sharedUsernames.isEmpty() && sharedUsernames.contains(normalizeBlankToNull(order.getAssignedUsername())))
                || username.equals(normalizeBlankToNull(order.getAssignedUsername())));
    }

    private AssigneeResolution resolveAssigneeByDeliveryParty(String effectiveDeliveryParty, String partyBRepresentative) {
        String deliveryPartyTrimmed = normalizeBlankToNull(effectiveDeliveryParty);
        if (deliveryPartyTrimmed == null) {
            throw new IllegalArgumentException("请先选择交付方");
        }
        List<PartnerInfo> partyBList = partnerInfoRepository.findAllByTitleOrNameTrimmed(deliveryPartyTrimmed);
        if (partyBList == null || partyBList.isEmpty()) {
            partyBList = partnerInfoRepository.findFirstByTitleOrNameContaining(deliveryPartyTrimmed)
                    .map(first -> {
                        String ref = (first.getTitle() != null && !first.getTitle().isBlank()) ? first.getTitle().trim()
                                : (first.getName() != null ? first.getName().trim() : "");
                        if (!ref.isEmpty()) {
                            return partnerInfoRepository.findAllByTitleOrNameTrimmed(ref);
                        }
                        return java.util.Collections.<PartnerInfo>emptyList();
                    })
                    .orElse(java.util.Collections.emptyList());
        }
        if (partyBList == null || partyBList.isEmpty()) {
            throw new IllegalArgumentException("未找到交付方「" + deliveryPartyTrimmed + "」对应的乙方信息，请先维护用户信息。");
        }

        PartnerInfo partyB = null;
        if (partyBList.size() == 1) {
            partyB = partyBList.get(0);
        } else {
            String chosen = normalizeBlankToNull(partyBRepresentative);
            if (chosen == null) {
                throw new IllegalArgumentException("该交付方在用户信息维护中有多条记录，请选择要指派的乙方业务员后再提交。");
            }
            List<PartnerInfo> matches = new ArrayList<>();
            for (PartnerInfo item : partyBList) {
                if (matchesPartnerChoice(item, chosen)) {
                    matches.add(item);
                }
            }
            if (matches.size() == 1) {
                partyB = matches.get(0);
            } else if (matches.size() > 1) {
                throw new IllegalArgumentException("所选乙方业务员「" + chosen + "」匹配到多条用户信息，请改用登录用户名重新选择。");
            } else {
                throw new IllegalArgumentException("所选乙方业务员「" + chosen + "」不在该交付方对应的用户信息维护记录中。");
            }
        }
        String username = normalizeBlankToNull(partyB.getUsername());
        if (username == null) {
            throw new IllegalArgumentException("用户信息维护中该交付方未配置用户名，无法指派。");
        }
        User resolvedUser = userRepository.findByUsername(username).orElse(null);
        if (resolvedUser == null) {
            throw new IllegalArgumentException("用户信息维护中的用户名「" + username + "」在用户表中不存在，请先创建账号。");
        }
        return new AssigneeResolution(partyB, resolvedUser);
    }

    private boolean matchesPartnerChoice(PartnerInfo partnerInfo, String chosen) {
        String normalizedChoice = normalizeBlankToNull(chosen);
        if (partnerInfo == null || normalizedChoice == null) {
            return false;
        }
        if (normalizedChoice.equals(normalizeBlankToNull(partnerInfo.getUsername()))) {
            return true;
        }
        if (normalizedChoice.equals(normalizeBlankToNull(partnerInfo.getContactPerson()))) {
            return true;
        }
        String username = normalizeBlankToNull(partnerInfo.getUsername());
        if (username == null) {
            return false;
        }
        User linkedUser = userRepository.findByUsername(username).orElse(null);
        return linkedUser != null && normalizedChoice.equals(normalizeBlankToNull(linkedUser.getRealName()));
    }

    private User resolveAssignerUser(SalesOrder salesOrder, User resolvedAssignUser) {
        if (salesOrder == null) {
            return null;
        }
        String previousAssignedUsername = normalizeBlankToNull(salesOrder.getPreviousAssignedUsername());
        if (previousAssignedUsername != null
                && (resolvedAssignUser == null || !previousAssignedUsername.equals(normalizeBlankToNull(resolvedAssignUser.getUsername())))) {
            User previous = userRepository.findByUsername(previousAssignedUsername).orElse(null);
            if (previous != null) {
                return previous;
            }
        }
        if (salesOrder.getCreatedBy() != null) {
            return userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
        }
        return null;
    }

    private String buildMergeSelectionKey(java.util.Collection<Long> salesOrderIds) {
        String raw = salesOrderIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String joinOrderIds(List<SalesOrder> orders) {
        return orders.stream()
                .map(SalesOrder::getId)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String joinDistinctTexts(List<String> values, String delimiter) {
        return values.stream()
                .map(this::normalizeBlankToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(delimiter));
    }

    private String resolveProductNameForOrder(SalesOrder salesOrder) {
        if (salesOrder == null) {
            return "";
        }
        String productNameVal = salesOrder.getProductName() == null ? "" : salesOrder.getProductName().trim();
        if (productNameVal.isEmpty() && salesOrder.getModel() != null && !salesOrder.getModel().isBlank()) {
            productNameVal = productRepository.findFirstByModel(salesOrder.getModel().trim())
                    .map(Product::getName).filter(n -> n != null && !n.isBlank()).orElse("");
        }
        if (productNameVal.isEmpty() && salesOrder.getPlatformSku() != null && !salesOrder.getPlatformSku().isBlank()) {
            productNameVal = salesOrder.getPlatformSku().trim();
        }
        if (productNameVal.isEmpty() && salesOrder.getModel() != null) {
            productNameVal = salesOrder.getModel();
        }
        return productNameVal;
    }

    private java.math.BigDecimal resolveContractUnitPrice(SalesOrder salesOrder) {
        if (salesOrder == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (salesOrder.getDeliveryPartyPurchasePrice() != null
                && salesOrder.getDeliveryPartyPurchasePrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return salesOrder.getDeliveryPartyPurchasePrice();
        }
        if (salesOrder.getTaxIncludedPrice() != null && salesOrder.getDeductionRate() != null) {
            try {
                java.math.BigDecimal rate = salesOrder.getDeductionRate().divide(new java.math.BigDecimal("100"));
                return salesOrder.getTaxIncludedPrice().multiply(java.math.BigDecimal.ONE.subtract(rate))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }
        if (salesOrder.getTaxIncludedPrice() != null && salesOrder.getTaxIncludedPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return salesOrder.getTaxIncludedPrice();
        }
        SalesOrder parentSource = resolveParentChainSourceOrder(salesOrder);
        if (parentSource != null && parentSource != salesOrder) {
            return resolveContractUnitPrice(parentSource);
        }
        return salesOrder.getTaxIncludedPrice() != null ? salesOrder.getTaxIncludedPrice() : java.math.BigDecimal.ZERO;
    }

    /**
     * 生成下一跳链式订单时，优先沿用“当前这一跳已经确认的成交价”，
     * 不要回退到源头待指派销售单的金额，否则会把下游列表金额重新污染回旧口径。
     */
    private java.math.BigDecimal resolveCurrentLevelCarryForwardUnitPrice(SalesOrder salesOrder) {
        if (salesOrder == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (salesOrder.getDeliveryPartyPurchasePrice() != null
                && salesOrder.getDeliveryPartyPurchasePrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return salesOrder.getDeliveryPartyPurchasePrice().setScale(2, java.math.RoundingMode.HALF_UP);
        }
        java.math.BigDecimal detailUnit = resolveCurrentLevelDetailUnitPrice(salesOrder);
        if (detailUnit != null && detailUnit.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return detailUnit.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (salesOrder.getTaxIncludedPrice() != null
                && salesOrder.getTaxIncludedPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return salesOrder.getTaxIncludedPrice().setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (salesOrder.getTaxIncludedTotal() != null
                && salesOrder.getTaxIncludedTotal().compareTo(java.math.BigDecimal.ZERO) > 0
                && salesOrder.getQuantity() != null
                && salesOrder.getQuantity() > 0) {
            try {
                return salesOrder.getTaxIncludedTotal()
                        .divide(new java.math.BigDecimal(salesOrder.getQuantity()), 2, java.math.RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }
        return java.math.BigDecimal.ZERO;
    }

    private java.math.BigDecimal resolveCurrentLevelCarryForwardTotalAmount(SalesOrder salesOrder, java.math.BigDecimal unitPrice) {
        if (salesOrder == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (unitPrice != null
                && unitPrice.compareTo(java.math.BigDecimal.ZERO) > 0
                && salesOrder.getQuantity() != null
                && salesOrder.getQuantity() > 0) {
            return unitPrice.multiply(new java.math.BigDecimal(salesOrder.getQuantity()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        java.math.BigDecimal detailTotal = resolveCurrentLevelDetailTotalAmount(salesOrder);
        if (detailTotal != null && detailTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return detailTotal.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (salesOrder.getTaxIncludedTotal() != null && salesOrder.getTaxIncludedTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return salesOrder.getTaxIncludedTotal().setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return java.math.BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private void applyDeliveryPartyPurchasePrice(SalesOrder order, java.math.BigDecimal purchasePrice) {
        if (order == null || purchasePrice == null) {
            return;
        }
        java.math.BigDecimal normalized = purchasePrice.setScale(2, java.math.RoundingMode.HALF_UP);
        order.setDeliveryPartyPurchasePrice(normalized);
        if (order.getOrderDetails() == null || order.getOrderDetails().isBlank()) {
            return;
        }
        try {
            Map<String, Object> details = objectMapper.readValue(order.getOrderDetails(), Map.class);
            Object reconciliationsObj = details.get("reconciliations");
            if (reconciliationsObj instanceof List<?> reconciliations) {
                for (Object item : reconciliations) {
                    if (item instanceof Map<?, ?> raw) {
                        Map<String, Object> reconciliation = (Map<String, Object>) raw;
                        reconciliation.put("deliveryPartyPurchasePrice", normalized);
                    }
                }
            }
            Object productsObj = details.get("products");
            if (productsObj instanceof List<?> products) {
                for (Object item : products) {
                    if (item instanceof Map<?, ?> raw) {
                        Map<String, Object> product = (Map<String, Object>) raw;
                        product.put("deliveryPartyPurchasePrice", normalized);
                    }
                }
            }
            order.setOrderDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            System.err.println("applyDeliveryPartyPurchasePrice parse orderDetails error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private java.math.BigDecimal resolveCurrentLevelDetailUnitPrice(SalesOrder salesOrder) {
        if (salesOrder == null || salesOrder.getOrderDetails() == null || salesOrder.getOrderDetails().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> details = objectMapper.readValue(salesOrder.getOrderDetails(), Map.class);
            Object productsObj = details.get("products");
            if (!(productsObj instanceof List<?> products) || products.isEmpty()) {
                return null;
            }
            for (Object item : products) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> product = new java.util.LinkedHashMap<>();
                raw.forEach((key, value) -> product.put(String.valueOf(key), value));
                java.math.BigDecimal deliveryPartyUnit = toBigDecimalSafe(product.get("deliveryPartyPurchasePrice"));
                if (deliveryPartyUnit != null && deliveryPartyUnit.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    return deliveryPartyUnit;
                }
                java.math.BigDecimal detailUnit = toBigDecimalSafe(product.get("taxIncludedPrice"));
                if (detailUnit != null && detailUnit.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    return detailUnit;
                }
            }
        } catch (Exception e) {
            System.err.println("resolveCurrentLevelDetailUnitPrice parse orderDetails error: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private java.math.BigDecimal resolveCurrentLevelDetailTotalAmount(SalesOrder salesOrder) {
        if (salesOrder == null || salesOrder.getOrderDetails() == null || salesOrder.getOrderDetails().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> details = objectMapper.readValue(salesOrder.getOrderDetails(), Map.class);
            Object productsObj = details.get("products");
            if (!(productsObj instanceof List<?> products) || products.isEmpty()) {
                return null;
            }
            for (Object item : products) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> product = new java.util.LinkedHashMap<>();
                raw.forEach((key, value) -> product.put(String.valueOf(key), value));
                java.math.BigDecimal detailTotal = toBigDecimalSafe(product.get("taxIncludedTotal"));
                if (detailTotal != null && detailTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    return detailTotal;
                }
            }
        } catch (Exception e) {
            System.err.println("resolveCurrentLevelDetailTotalAmount parse orderDetails error: " + e.getMessage());
        }
        return null;
    }

    private java.math.BigDecimal resolveContractTotalAmount(SalesOrder salesOrder) {
        java.math.BigDecimal unit = resolveContractUnitPrice(salesOrder);
        Integer quantity = salesOrder != null ? salesOrder.getQuantity() : null;
        if (quantity == null) {
            if (salesOrder != null && salesOrder.getTaxIncludedTotal() != null
                    && salesOrder.getTaxIncludedTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
                return salesOrder.getTaxIncludedTotal().setScale(2, java.math.RoundingMode.HALF_UP);
            }
            return unit;
        }
        java.math.BigDecimal total = unit.multiply(new java.math.BigDecimal(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
        if (total.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return total;
        }
        if (salesOrder != null && salesOrder.getTaxIncludedTotal() != null
                && salesOrder.getTaxIncludedTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return salesOrder.getTaxIncludedTotal().setScale(2, java.math.RoundingMode.HALF_UP);
        }
        SalesOrder parentSource = resolveParentChainSourceOrder(salesOrder);
        if (parentSource != null && parentSource != salesOrder) {
            return resolveContractTotalAmount(parentSource);
        }
        return total;
    }

    private SalesOrder resolveParentChainSourceOrder(SalesOrder salesOrder) {
        if (salesOrder == null) {
            return null;
        }
        String marker = normalizeBlankToNull(salesOrder.getPurchaseOrderNo());
        if (marker == null || !marker.startsWith(CHAIN_PREFIX)) {
            return null;
        }
        try {
            Long sourceOrderId = Long.parseLong(marker.substring(CHAIN_PREFIX.length()).trim());
            return salesOrderRepository.findById(sourceOrderId).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolvePartyATitleForContract(SalesOrder salesOrder) {
        String assignerUsername = null;
        if (salesOrder != null && salesOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
            if (creator != null && creator.getUsername() != null && !creator.getUsername().isBlank()) {
                assignerUsername = creator.getUsername().trim();
            }
        }
        if (assignerUsername == null || assignerUsername.isBlank()) {
            assignerUsername = getCurrentUsernameSafe();
        }
        List<PartnerInfo> assignerPartners = (assignerUsername != null && !assignerUsername.isBlank())
                ? partnerInfoRepository.findByUsername(assignerUsername) : java.util.Collections.emptyList();
        PartnerInfo assignerPartner = (assignerPartners != null && !assignerPartners.isEmpty()) ? assignerPartners.get(0) : null;
        User assignerIdentityUser = (assignerUsername != null && !assignerUsername.isBlank())
                ? userRepository.findByUsername(assignerUsername).orElse(null)
                : null;
        if (assignerPartner != null) {
            String title = normalizeBlankToNull(assignerPartner.getTitle());
            String name = normalizeBlankToNull(assignerPartner.getName());
            if (title != null) return title;
            if (name != null) return name;
        }
        if (assignerIdentityUser != null && normalizeBlankToNull(assignerIdentityUser.getCompanyTitle()) != null) {
            return assignerIdentityUser.getCompanyTitle().trim();
        }
        return resolvePartyATitle(salesOrder);
    }

    private String resolveSettlementParty(SalesOrder salesOrder, String requestPlatformName) {
        if (!isFeichukeOperator()) {
            return "";
        }
        String top = normalizeBlankToNull(requestPlatformName);
        if (top == null && salesOrder != null) {
            top = normalizeBlankToNull(salesOrder.getTopLevelCustomerName());
        }
        if (top == null && salesOrder != null) {
            top = normalizeBlankToNull(salesOrder.getPlatformName());
        }
        if (top == null && salesOrder != null) {
            top = normalizeBlankToNull(salesOrder.getPartyATitle());
        }
        return top == null ? "" : top;
    }

    private String resolveContractSalesName(SalesOrder firstOrder, String partyATitle) {
        String salesName = normalizeBlankToNull(firstOrder != null ? firstOrder.getEcommerceSalesName() : null);
        if (salesName != null) {
            return salesName;
        }
        PartnerInfo partyA = findLatestPartnerInfoByTitleOrName(partyATitle);
        if (partyA != null && normalizeBlankToNull(partyA.getContactPerson()) != null) {
            return partyA.getContactPerson().trim();
        }
        if (firstOrder != null && firstOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(firstOrder.getCreatedBy()).orElse(null);
            if (creator != null && normalizeBlankToNull(creator.getRealName()) != null) {
                return creator.getRealName().trim();
            }
        }
        return "";
    }

    private String resolvePartnerContact(PartnerInfo partnerInfo, User fallbackUser) {
        String contact = partnerInfo != null ? normalizeBlankToNull(partnerInfo.getContactPerson()) : null;
        if (contact != null) {
            return contact;
        }
        if (fallbackUser != null && normalizeBlankToNull(fallbackUser.getRealName()) != null) {
            return fallbackUser.getRealName().trim();
        }
        return fallbackUser != null ? safe(fallbackUser.getUsername()) : "";
    }

    private void applyPartyAInfo(Contract contract, String partyATitle, SalesOrder firstOrder) {
        PartnerInfo partyA = findLatestPartnerInfoByTitleOrName(partyATitle);
        if (partyA != null) {
            contract.setPartyAAddress(partyA.getBankAddress());
            contract.setPartyABank(partyA.getBankName());
            contract.setPartyAAccount(partyA.getBankAccount());
            contract.setPartyATaxNo(partyA.getTaxNumber());
            contract.setPartyAPhone(partyA.getContactPhone());
            if (normalizeBlankToNull(contract.getSalesName()) == null) {
                contract.setSalesName(normalizeBlankToNull(partyA.getContactPerson()) == null ? "" : partyA.getContactPerson().trim());
            }
            return;
        }
        if (firstOrder != null && firstOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(firstOrder.getCreatedBy()).orElse(null);
            if (creator != null && normalizeBlankToNull(creator.getCompanyTitle()) != null) {
                PartnerInfo byCompany = findLatestPartnerInfoByTitleOrName(creator.getCompanyTitle());
                if (byCompany != null) {
                    contract.setPartyAAddress(byCompany.getBankAddress());
                    contract.setPartyABank(byCompany.getBankName());
                    contract.setPartyAAccount(byCompany.getBankAccount());
                    contract.setPartyATaxNo(byCompany.getTaxNumber());
                    contract.setPartyAPhone(byCompany.getContactPhone());
                }
            }
        }
    }

    private void applyPartyBInfo(Contract contract, PartnerInfo partyB, User resolvedAssignUser) {
        if (partyB != null) {
            contract.setPartyBAddress(partyB.getBankAddress());
            contract.setPartyBBank(partyB.getBankName());
            contract.setPartyBAccount(partyB.getBankAccount());
            contract.setPartyBTaxNo(partyB.getTaxNumber());
            contract.setPartyBPhone(partyB.getContactPhone());
        }
        contract.setPartyBRepresentative(resolvePartnerContact(partyB, resolvedAssignUser));
    }

    private List<Map<String, Object>> buildProductsForOrders(List<SalesOrder> orders) {
        List<Map<String, Object>> products = new ArrayList<>();
        if (orders == null) {
            return products;
        }
        for (SalesOrder order : orders) {
            products.addAll(extractProductsFromSalesOrder(order));
        }
        return products;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractProductsFromSalesOrder(SalesOrder salesOrder) {
        List<Map<String, Object>> products = new ArrayList<>();
        if (salesOrder == null) {
            return products;
        }
        if (salesOrder.getOrderDetails() != null && !salesOrder.getOrderDetails().isEmpty()) {
            try {
                Map<String, Object> details = objectMapper.readValue(salesOrder.getOrderDetails(), Map.class);
                Object productObj = details.get("products");
                if (productObj instanceof List<?> list && !list.isEmpty()) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> raw) {
                            Map<String, Object> product = new java.util.LinkedHashMap<>();
                            raw.forEach((key, value) -> product.put(String.valueOf(key), value));
                            enrichProductPriceAndName(product, salesOrder);
                            products.add(product);
                        }
                    }
                    return products;
                }
            } catch (Exception e) {
                System.err.println("Error parsing order details for merged contract: " + e.getMessage());
            }
        }
        Map<String, Object> fallback = new java.util.LinkedHashMap<>();
        fallback.put("model", salesOrder.getModel());
        fallback.put("quantity", salesOrder.getQuantity() == null ? 1 : salesOrder.getQuantity());
        fallback.put("productName", resolveProductNameForOrder(salesOrder));
        java.math.BigDecimal unit = resolveContractUnitPrice(salesOrder);
        fallback.put("taxIncludedPrice", unit.setScale(2, java.math.RoundingMode.HALF_UP).toString());
        fallback.put("taxIncludedTotal", resolveContractTotalAmount(salesOrder).toString());
        if (salesOrder.getDeliveryDate() != null && salesOrder.getOrderDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(salesOrder.getOrderDate(), salesOrder.getDeliveryDate());
            fallback.put("deliveryCycle", String.valueOf(days));
        }
        products.add(fallback);
        return products;
    }

    private void enrichProductPriceAndName(Map<String, Object> product, SalesOrder salesOrder) {
        try {
            String modelStr = getValue(product, "model", "");
            if (modelStr != null && !modelStr.isBlank()) {
                String fromProduct = productRepository.findFirstByModel(modelStr.trim())
                        .map(Product::getName).filter(n -> n != null && !n.isBlank()).orElse("");
                if (!fromProduct.isEmpty()) {
                    product.put("productName", fromProduct);
                }
            }
            if (product.get("productName") == null || product.get("productName").toString().isBlank()) {
                Object sku = product.get("platformSku");
                if (sku != null && !sku.toString().isBlank()) {
                    product.put("productName", sku.toString());
                } else {
                    product.put("productName", resolveProductNameForOrder(salesOrder));
                }
            }
            java.math.BigDecimal unit = toBigDecimalSafe(product.get("taxIncludedPrice"));
            java.math.BigDecimal total = toBigDecimalSafe(product.get("taxIncludedTotal"));
            Object qtyObj = product.get("quantity");
            int qty = (qtyObj != null && qtyObj.toString().matches("\\d+"))
                    ? Integer.parseInt(qtyObj.toString())
                    : (salesOrder.getQuantity() != null ? salesOrder.getQuantity() : 1);
            if (unit == null || unit.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                unit = resolveContractUnitPrice(salesOrder);
                product.put("taxIncludedPrice", unit.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            }
            if (total == null || total.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                total = unit.multiply(new java.math.BigDecimal(qty));
                product.put("taxIncludedTotal", total.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            }
            if (salesOrder.getDeliveryDate() != null && salesOrder.getOrderDate() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(salesOrder.getOrderDate(), salesOrder.getDeliveryDate());
                product.put("deliveryCycle", String.valueOf(days));
            }
        } catch (Exception e) {
            System.err.println("Error enriching merged product: " + e.getMessage());
        }
    }

    private java.math.BigDecimal toBigDecimalSafe(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            return new java.math.BigDecimal(text.replace(",", "").replace("，", "").replace("¥", "").replace("￥", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private void createOrUpdateMergedPurchaseOrder(List<SalesOrder> orders,
                                                   User assigner,
                                                   String supplier,
                                                   String mergeSelectionKey,
                                                   String customPurchaseOrderNo) {
        if (orders == null || orders.isEmpty() || assigner == null || assigner.getId() == null) {
            return;
        }
        SalesOrder firstOrder = orders.get(0);
        String omsOrderNo = normalizeBlankToNull(firstOrder.getOmsOrderNo());
        if (omsOrderNo == null) {
            return;
        }
        String uniquePurchaseNo = normalizeBlankToNull(customPurchaseOrderNo);
        if (uniquePurchaseNo == null) {
            uniquePurchaseNo = buildMergedPurchaseOrderNo(firstOrder, assigner, supplier, mergeSelectionKey);
        }
        PurchaseOrder po = purchaseOrderRepository.findFirstByPurchaseOrderNoOrderByIdDesc(uniquePurchaseNo)
                .orElseGet(PurchaseOrder::new);
        po.setPurchaseOrderNo(ensurePurchaseOrderNoAvailable(uniquePurchaseNo, po.getId()));
        po.setOmsOrderNo(omsOrderNo);
        po.setPurchaseType(omsOrderNo.startsWith("D") ? "第三方采购订单" : "常规采购订单");
        po.setStatus("待确认");
        po.setSupplier(supplier);
        po.setModel(orders.stream()
                .map(order -> {
                    String model = normalizeBlankToNull(order.getModel());
                    Integer qty = order.getQuantity();
                    return model == null ? null : model + (qty == null ? "" : " x" + qty);
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" / ")));
        int totalQuantity = orders.stream().map(SalesOrder::getQuantity).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        po.setQuantity(totalQuantity > 0 ? totalQuantity : null);
        java.math.BigDecimal totalAmount = orders.stream()
                .map(this::resolveContractTotalAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        po.setTaxIncludedPurchaseTotal(totalAmount);
        if (totalQuantity > 0) {
            po.setTaxIncludedPurchasePrice(totalAmount.divide(new java.math.BigDecimal(totalQuantity), 2, java.math.RoundingMode.HALF_UP));
        } else {
            po.setTaxIncludedPurchasePrice(java.math.BigDecimal.ZERO);
        }
        po.setDeliveryDate(orders.stream().map(SalesOrder::getDeliveryDate).filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(firstOrder.getDeliveryDate()));
        po.setSalesPerson(assigner.getRealName());
        po.setCreatedBy(assigner.getId());
        po.setCreator(assigner.getRealName());
        po.setSourceSalesOrderId(firstOrder.getId());
        po.setMasterId(firstOrder.getMasterId());
        po.setAllocationId(null);
        po.setMergeSelectionKey(mergeSelectionKey);
        po.setMergedSalesOrderIds(joinOrderIds(orders));
        purchaseOrderRepository.save(po);
    }

    private List<Long> parseMergedSalesOrderIds(String mergedSalesOrderIds) {
        if (mergedSalesOrderIds == null || mergedSalesOrderIds.isBlank()) {
            return java.util.Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : mergedSalesOrderIds.split(",")) {
            String text = normalizeBlankToNull(part);
            if (text == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private static class AssigneeResolution {
        private final PartnerInfo partnerInfo;
        private final User user;

        private AssigneeResolution(PartnerInfo partnerInfo, User user) {
            this.partnerInfo = partnerInfo;
            this.user = user;
        }
    }

    /**
     * 是否为源头工厂：用户列表中角色为「出货方」或公司抬头为上海热像科技股份有限公司。
     * 源头工厂被指派后不生成其采购单、不生成其待指派下一层订单。
     */
    private boolean isSourceFactory(User user) {
        if (user == null) return false;
        if ("ROLE_SHIPPING".equals(user.getRole())) return true;
        String ct = user.getCompanyTitle();
        return ct != null && ct.contains("上海热像科技股份有限公司");
    }

    /**
     * 指派成功后，为被指派方自动触发一条采购订单（按 omsOrderNo + createdBy 幂等更新）
     * 已废弃：现仅给指派方生成采购单，不再为被指派方生成。
     */
    @SuppressWarnings("unused")
    private void createOrUpdatePurchaseOrderForAssignee(SalesOrder salesOrder, User assignee, String partyBRepresentative) {
        if (salesOrder == null || assignee == null || assignee.getId() == null) return;
        String omsOrderNo = salesOrder.getOmsOrderNo();
        if (omsOrderNo == null || omsOrderNo.isBlank()) return;

        PurchaseOrder po = purchaseOrderRepository
                .findFirstByOmsOrderNoAndCreatedByOrderByIdDesc(omsOrderNo, assignee.getId())
                .orElseGet(PurchaseOrder::new);

        if (po.getPurchaseOrderNo() == null || po.getPurchaseOrderNo().isBlank()) {
            String assigneeMark = assignee.getUsername() != null ? assignee.getUsername().trim() : String.valueOf(assignee.getId());
            po.setPurchaseOrderNo("PO-" + omsOrderNo + "-" + assigneeMark);
        }
        populatePurchaseOrderAnchors(po, salesOrder);
        po.setOmsOrderNo(omsOrderNo);
        // 不带 D = 常规采购订单（自营），带 D = 第三方采购订单
        po.setPurchaseType(omsOrderNo != null && omsOrderNo.startsWith("D") ? "第三方采购订单" : "常规采购订单");
        po.setStatus("待确认");
        // 被指派方的采购单 = 向甲方采购，供应商 = 甲方（platformName），不是 deliveryParty（乙方）
        String supplier = salesOrder.getPartyATitle();
        if (supplier == null || supplier.isBlank()) {
            supplier = salesOrder.getPlatformName();
        }
        if (supplier == null || supplier.isBlank()) {
            supplier = salesOrder.getOperationEntityTitle();
        }
        if (supplier == null || supplier.isBlank()) {
            supplier = salesOrder.getDeliveryParty();
        }
        po.setSupplier(supplier);
        po.setModel(salesOrder.getModel());
        po.setQuantity(salesOrder.getQuantity());
        po.setTaxIncludedPurchasePrice(salesOrder.getDeliveryPartyPurchasePrice());
        if (salesOrder.getDeliveryPartyPurchasePrice() != null && salesOrder.getQuantity() != null) {
            po.setTaxIncludedPurchaseTotal(
                    salesOrder.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(salesOrder.getQuantity()))
            );
        }
        po.setDeliveryDate(salesOrder.getDeliveryDate());
        po.setSalesPerson(
                (partyBRepresentative != null && !partyBRepresentative.isBlank())
                        ? partyBRepresentative.trim()
                        : assignee.getRealName()
        );
        po.setCreatedBy(assignee.getId());
        po.setCreator(assignee.getRealName());

        purchaseOrderRepository.save(po);
        System.out.println("=== PurchaseOrder upserted for assignee: " + assignee.getUsername()
                + ", omsOrderNo=" + omsOrderNo + ", purchaseOrderNo=" + po.getPurchaseOrderNo() + " ===");
    }

    /**
     * 转派时：为指派方（如坚领）生成「向新乙方（如热像科技）采购」的采购单，便于坚领的采购管理显示 供应商=热像科技、金额=3550
     */
    private void createOrUpdatePurchaseOrderForAssigner(SalesOrder salesOrder, User assigner, String customPurchaseOrderNo) {
        if (salesOrder == null || assigner == null || assigner.getId() == null) return;
        String omsOrderNo = salesOrder.getOmsOrderNo();
        if (omsOrderNo == null || omsOrderNo.isBlank()) return;
        String supplier = salesOrder.getDeliveryParty() != null ? salesOrder.getDeliveryParty() : "";
        if (supplier.isBlank()) return;

        // 一单一采购：同一指派人同一供应商下，按「销售单ID」拆分采购单，避免多个销售单被合并覆盖成一条。
        // 同时保留幂等：重复点击生成合同时，仍更新同一条采购单而不重复新增。
        String uniquePurchaseNo = normalizeBlankToNull(customPurchaseOrderNo);
        if (uniquePurchaseNo == null) {
            uniquePurchaseNo = buildSinglePurchaseOrderNo(salesOrder, assigner, supplier);
        }
        PurchaseOrder po = purchaseOrderRepository
                .findFirstByPurchaseOrderNoOrderByIdDesc(uniquePurchaseNo)
                .orElseGet(PurchaseOrder::new);

        po.setPurchaseOrderNo(ensurePurchaseOrderNoAvailable(uniquePurchaseNo, po.getId()));
        populatePurchaseOrderAnchors(po, salesOrder);
        po.setOmsOrderNo(omsOrderNo);
        po.setPurchaseType(omsOrderNo != null && omsOrderNo.startsWith("D") ? "第三方采购订单" : "常规采购订单");
        po.setStatus("待确认");
        po.setSupplier(salesOrder.getDeliveryParty() != null ? salesOrder.getDeliveryParty() : "");
        po.setModel(salesOrder.getModel());
        po.setQuantity(salesOrder.getQuantity());
        po.setTaxIncludedPurchasePrice(salesOrder.getDeliveryPartyPurchasePrice());
        if (salesOrder.getDeliveryPartyPurchasePrice() != null && salesOrder.getQuantity() != null) {
            po.setTaxIncludedPurchaseTotal(
                    salesOrder.getDeliveryPartyPurchasePrice().multiply(new java.math.BigDecimal(salesOrder.getQuantity())));
        }
        po.setDeliveryDate(salesOrder.getDeliveryDate());
        po.setSalesPerson(assigner.getRealName());
        po.setCreatedBy(assigner.getId());
        po.setCreator(assigner.getRealName());
        purchaseOrderRepository.save(po);
        System.out.println("=== PurchaseOrder upserted for assigner (转派): " + assigner.getUsername()
                + ", supplier=" + po.getSupplier() + ", omsOrderNo=" + omsOrderNo + " ===");
    }

    private void populatePurchaseOrderAnchors(PurchaseOrder purchaseOrder, SalesOrder sourceOrder) {
        if (purchaseOrder == null || sourceOrder == null || sourceOrder.getId() == null) {
            return;
        }
        User sourceCreator = sourceOrder.getCreatedBy() == null
                ? null
                : userRepository.findById(sourceOrder.getCreatedBy()).orElse(null);
        SalesOrder anchoredSource = salesOrderMasterService.ensureAnchorsForOrder(sourceOrder, sourceCreator);
        purchaseOrder.setSourceSalesOrderId(anchoredSource.getId());
        purchaseOrder.setMasterId(anchoredSource.getMasterId());
        purchaseOrder.setAllocationId(anchoredSource.getAllocationId());
    }

    /**
     * 指派成功后，给被指派方自动生成“下一层销售订单”
     * 场景：sonmin -> 坚领 后，坚领销售管理中应出现“卖给飞础科”的销售订单，便于继续指派热像科技。
     * 一账户一套数据：链式单甲方=指派方公司（assigner），转派时热像的甲方=南京新测而非飞础科。
     */
    @SuppressWarnings("unchecked")
    private void createOrUpdateNextLevelSalesOrder(SalesOrder sourceOrder, User assignee, User assigner) {
        if (sourceOrder == null || assignee == null || assignee.getId() == null) return;

        User sourceCreator = sourceOrder.getCreatedBy() == null
                ? null
                : userRepository.findById(sourceOrder.getCreatedBy()).orElse(null);
        SalesOrder anchoredSource = salesOrderMasterService.ensureAnchorsForOrder(sourceOrder, sourceCreator);

        String chainMarker = "CHAIN_FROM:" + sourceOrder.getId();
        SalesOrder nextOrder = salesOrderRepository
                .findFirstByPurchaseOrderNoAndCreatedByOrderByIdDesc(chainMarker, assignee.getId())
                .orElseGet(SalesOrder::new);

        // 链式单甲方=指派方公司（assigner）；无 assigner 时兜底来源单 platformName 或创建人公司
        String partyATitle = null;
        if (assigner != null && assigner.getCompanyTitle() != null && !assigner.getCompanyTitle().isBlank()) {
            partyATitle = assigner.getCompanyTitle().trim();
        }
        if (partyATitle == null && sourceOrder.getPartyATitle() != null && !sourceOrder.getPartyATitle().isBlank()) {
            partyATitle = sourceOrder.getPartyATitle().trim();
        }
        if (partyATitle == null && sourceOrder.getPlatformName() != null && !sourceOrder.getPlatformName().isBlank()) {
            partyATitle = sourceOrder.getPlatformName().trim();
        }
        if (partyATitle == null && sourceOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(sourceOrder.getCreatedBy()).orElse(null);
            if (creator != null && creator.getCompanyTitle() != null && !creator.getCompanyTitle().isBlank()) {
                partyATitle = creator.getCompanyTitle().trim();
            }
        }
        if (partyATitle == null) partyATitle = "";

        nextOrder.setCreatedBy(assignee.getId());
        nextOrder.setMasterId(anchoredSource.getMasterId());
        nextOrder.setAllocationId(anchoredSource.getAllocationId());
        nextOrder.setLineNo(anchoredSource.getLineNo());
        nextOrder.setLineStatus("待指派");
        nextOrder.setIsMasterPrimaryLine(anchoredSource.getIsMasterPrimaryLine());
        nextOrder.setPartyATitle(partyATitle);
        nextOrder.setPlatformName(partyATitle);
        nextOrder.setPlatformOrderNo(sourceOrder.getPlatformOrderNo());
        nextOrder.setOrderType(sourceOrder.getOrderType());
        nextOrder.setOrderDate(sourceOrder.getOrderDate());
        nextOrder.setDeliveryDate(sourceOrder.getDeliveryDate());
        nextOrder.setEcommerceSalesId(assignee.getId());
        nextOrder.setEcommerceSalesName(assignee.getRealName());
        // 支付方式沿用指派时选择的（如背靠背），未设置时兜底账期
        nextOrder.setPaymentMethod(sourceOrder.getPaymentMethod() != null && !sourceOrder.getPaymentMethod().isBlank()
                ? sourceOrder.getPaymentMethod().trim()
                : "账期");
        nextOrder.setInvoiceTitle(partyATitle);
        // 收货信息 / 发货要求默认透出仅对上海热像科技账号生效；
        // 其他交付方（如 qdkc / 神州技测）需主链手动点击「同步」后才可见。
        if (isRexiangCompany(assignee.getCompanyTitle())) {
            nextOrder.setReceiverName(sourceOrder.getReceiverName());
            nextOrder.setReceiverPhone(sourceOrder.getReceiverPhone());
            nextOrder.setReceiverAddress(sourceOrder.getReceiverAddress());
            nextOrder.setDeliveryNoteUrl(sourceOrder.getDeliveryNoteUrl());
            nextOrder.setBoxLabelUrls(sourceOrder.getBoxLabelUrls());
            nextOrder.setDeliveryNotePrintQuantity(sourceOrder.getDeliveryNotePrintQuantity());
            nextOrder.setForbiddenCouriers(sourceOrder.getForbiddenCouriers());
            nextOrder.setPrintBoxLabel(sourceOrder.getPrintBoxLabel());
            nextOrder.setPrintBarcode128(sourceOrder.getPrintBarcode128());
            nextOrder.setNeedReceiptSlip(sourceOrder.getNeedReceiptSlip());
            nextOrder.setReceiptUrl(sourceOrder.getReceiptUrl());
            nextOrder.setReceiptTime(sourceOrder.getReceiptTime());
            if (sourceOrder.getReceiptStatus() != null && !sourceOrder.getReceiptStatus().isBlank()) {
                nextOrder.setReceiptStatus(sourceOrder.getReceiptStatus());
            }
            nextOrder.setTrackingNumber(sourceOrder.getTrackingNumber());
            nextOrder.setReturnReceiptTrackingNumber(sourceOrder.getReturnReceiptTrackingNumber());
            nextOrder.setReturnReceiptReceiverPhone(sourceOrder.getReturnReceiptReceiverPhone());
            nextOrder.setVehiclePlate(sourceOrder.getVehiclePlate());
            nextOrder.setDeliveryMethod(sourceOrder.getDeliveryMethod());
            nextOrder.setSnCode(sourceOrder.getSnCode());
            nextOrder.setLogisticsCompany(sourceOrder.getLogisticsCompany());
        } else {
            nextOrder.setReceiverName(null);
            nextOrder.setReceiverPhone(null);
            nextOrder.setReceiverAddress(null);
            nextOrder.setDeliveryNoteUrl(null);
            nextOrder.setBoxLabelUrls(null);
            nextOrder.setDeliveryNotePrintQuantity(null);
            nextOrder.setForbiddenCouriers(null);
            nextOrder.setPrintBoxLabel(null);
            nextOrder.setPrintBarcode128(null);
            nextOrder.setNeedReceiptSlip(null);
            nextOrder.setReceiptUrl(null);
            nextOrder.setReceiptTime(null);
            nextOrder.setReceiptStatus(null);
            nextOrder.setTrackingNumber(null);
            nextOrder.setReturnReceiptTrackingNumber(null);
            nextOrder.setReturnReceiptReceiverPhone(null);
            nextOrder.setVehiclePlate(null);
            nextOrder.setDeliveryMethod(null);
            nextOrder.setSnCode(null);
            nextOrder.setLogisticsCompany(null);
        }
        nextOrder.setMaterialNo(sourceOrder.getMaterialNo());
        nextOrder.setPlatformSku(sourceOrder.getPlatformSku());
        nextOrder.setModel(sourceOrder.getModel());
        nextOrder.setProductConfig(sourceOrder.getProductConfig());
        nextOrder.setWarrantyPeriod(sourceOrder.getWarrantyPeriod());
        nextOrder.setQuantity(sourceOrder.getQuantity());

        java.math.BigDecimal nextUnitPrice = resolveCurrentLevelCarryForwardUnitPrice(sourceOrder);
        nextOrder.setTaxIncludedPrice(nextUnitPrice);
        nextOrder.setTaxIncludedTotal(resolveCurrentLevelCarryForwardTotalAmount(sourceOrder, nextUnitPrice));

        // 下一层订单处于“待指派”，交付方/采购价由该层业务员后续维护
        nextOrder.setStatus("待指派");
        nextOrder.setAssignedUsername(null);
        nextOrder.setAssignTime(null);
        nextOrder.setDeductionRate(null);
        nextOrder.setDeliveryParty(null);
        nextOrder.setShippingParty(null);
        nextOrder.setDeliveryPartyPurchasePrice(null);
        nextOrder.setPurchaseOrderNo(chainMarker);

        // 沿用来源订单的 OMS 订单号，避免链式订单显示为 XXX（getInitials 对未映射中文返回 X）
        if (sourceOrder.getOmsOrderNo() != null && !sourceOrder.getOmsOrderNo().isBlank()) {
            nextOrder.setOmsOrderNo(sourceOrder.getOmsOrderNo());
        } else if (nextOrder.getOmsOrderNo() == null || nextOrder.getOmsOrderNo().isBlank()) {
            String generatedOmsNo = salesOrderService.generateOmsOrderNo(assignee.getId(), sourceOrder.getOrderType());
            nextOrder.setOmsOrderNo(generatedOmsNo);
        }

        // 同步详情：复制来源单 details，但将产品价格替换为“该层销售价”
        if (sourceOrder.getOrderDetails() != null && !sourceOrder.getOrderDetails().isBlank()) {
            try {
                Map<String, Object> details = objectMapper.readValue(sourceOrder.getOrderDetails(), Map.class);
                Object productsObj = details.get("products");
                if (productsObj instanceof List && nextUnitPrice != null) {
                    List<Map<String, Object>> products = (List<Map<String, Object>>) productsObj;
                    for (Map<String, Object> p : products) {
                        Object qtyObj = p.get("quantity");
                        java.math.BigDecimal q = java.math.BigDecimal.ONE;
                        if (qtyObj != null) {
                            try {
                                q = new java.math.BigDecimal(String.valueOf(qtyObj));
                            } catch (Exception ignored) {
                            }
                        }
                        p.put("taxIncludedPrice", nextUnitPrice.toString());
                        p.put("taxIncludedTotal", nextUnitPrice.multiply(q).toString());
                        p.put("deliveryParty", null);
                        p.put("shippingParty", null);
                        p.put("deductionRate", null);
                        p.put("deliveryPartyPurchasePrice", null);
                    }
                }
                if (!isRexiangCompany(assignee.getCompanyTitle())) {
                    Object logisticsObj = details.get("logistics");
                    if (logisticsObj instanceof List<?>) {
                        for (Object item : (List<?>) logisticsObj) {
                            if (!(item instanceof Map<?, ?> m)) continue;
                            Map<String, Object> logistics = new java.util.LinkedHashMap<>();
                            m.forEach((key, value) -> logistics.put(String.valueOf(key), value));
                            logistics.put("receiverName", null);
                            logistics.put("receiverPhone", null);
                            logistics.put("receiverAddress", null);
                            logistics.put("deliveryMethod", null);
                            logistics.put("trackingNumber", null);
                            logistics.put("returnReceiptTrackingNumber", null);
                            logistics.put("returnReceiptReceiverPhone", null);
                            logistics.put("logisticsCompany", null);
                            logistics.put("vehiclePlate", null);
                        }
                    }
                }
                nextOrder.setOrderDetails(objectMapper.writeValueAsString(details));
            } catch (Exception e) {
                System.err.println("createOrUpdateNextLevelSalesOrder parse orderDetails error: " + e.getMessage());
            }
        }

        salesOrderRepository.save(nextOrder);
        System.out.println("=== Next-level SalesOrder upserted for assignee: " + assignee.getUsername()
                + ", sourceId=" + sourceOrder.getId() + ", chainOrderId=" + nextOrder.getId()
                + ", omsOrderNo=" + nextOrder.getOmsOrderNo() + " ===");
    }

    @Transactional
    public Contract saveContract(Contract contract) {
        return contractRepository.save(contract);
    }

    @Transactional
    public void deleteContract(Long id) {
        contractRepository.deleteById(id);
    }

    @Transactional
    public Contract invalidateContractForReturn(Contract contract) {
        if (contract == null || contract.getId() == null) {
            return contract;
        }
        List<SalesOrder> activeOrders = getActiveContractOrders(contract);
        String nextStatus = isAggregateContract(contract) && !activeOrders.isEmpty()
                ? CONTRACT_STATUS_INVALID_PENDING_REGENERATE
                : CONTRACT_STATUS_INVALID;
        contract.setStatus(nextStatus);
        return contractRepository.save(contract);
    }

    @Transactional
    public Contract regenerateContract(Long contractId) {
        Contract source = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("合同不存在"));
        if (!isAggregateContract(source)) {
            throw new RuntimeException("仅聚合合同支持按剩余商品重生成");
        }
        String status = safe(source.getStatus());
        if (!CONTRACT_STATUS_INVALID_PENDING_REGENERATE.equals(status) && !CONTRACT_STATUS_INVALID.equals(status)) {
            throw new RuntimeException("当前合同状态不支持重生成");
        }
        String currentUsername = getCurrentUsernameSafe();
        if (currentUsername == null || currentUsername.isBlank()) {
            throw new RuntimeException("请先登录");
        }
        User currentUser = userRepository.findByUsername(currentUsername.trim()).orElse(null);
        if (currentUser == null) {
            throw new RuntimeException("当前用户不存在");
        }

        List<SalesOrder> activeOrders = getActiveContractOrders(source);
        if (activeOrders.isEmpty()) {
            throw new RuntimeException("合同已无可重生成的剩余商品行");
        }
        for (SalesOrder order : activeOrders) {
            if (!canCurrentUserMutateContractForSalesOrder(currentUser, order.getId())) {
                throw new RuntimeException("无权按剩余商品重生成该合同");
            }
        }

        Contract newContract;
        String inheritedPlatformName = normalizeBlankToNull(source.getSettlementPartyName()) != null
                ? source.getSettlementPartyName() : source.getPlatformName();
        String previousMergeSelectionKey = source.getMergeSelectionKey();
        if (normalizeBlankToNull(previousMergeSelectionKey) != null) {
            source.setMergeSelectionKey(previousMergeSelectionKey + "-HIST-" + source.getId());
            contractRepository.save(source);
        }
        newContract = createContractFromMasterSelection(
                activeOrders.stream().map(SalesOrder::getId).toList(),
                source.getTemplateUrl(),
                source.getPartyBRepresentative(),
                inheritedPlatformName,
                source.getPaymentMethod(),
                source.getPartyBName(),
                null,
                null,
                null,
                null,
                null,
                null
        );
        source.setStatus(CONTRACT_STATUS_REGENERATED);
        contractRepository.save(source);
        return newContract;
    }

    @Transactional
    public Contract signContract(Long contractId, String partyType) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("合同不存在"));
        if (!"待签署".equals(safe(contract.getStatus()))) {
            throw new RuntimeException("当前合同状态不允许签署");
        }
        List<SalesOrder> contractOrders;
        contractOrders = getContractOrders(contract);
        SalesOrder salesOrder = contractOrders.isEmpty() ? null : contractOrders.get(0);

        String currentUsername = getCurrentUsernameSafe();
        if (currentUsername == null) throw new RuntimeException("请先登录");
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        if (currentUser == null) {
            throw new RuntimeException("当前用户不存在");
        }
        if (!canCurrentUserAccessContract(currentUser, contract)) {
            throw new RuntimeException("无权查看或操作该合同");
        }

        String userCompanyTitle = currentUser.getCompanyTitle();
        
        if ("A".equals(partyType)) {
            if (!isSameText(userCompanyTitle, contract.getPartyAName())) {
                throw new RuntimeException("只有甲方才能签署甲方合同");
            }
            contract.setPartyASigned(true);
            contract.setPartyASignedTime(LocalDateTime.now());
        } else if ("B".equals(partyType)) {
            boolean companyMatch = isSameText(userCompanyTitle, contract.getPartyBName());
            boolean assignedUserMatch = salesOrder != null && isSameText(currentUsername, salesOrder.getAssignedUsername());
            boolean representativeMatch = isSameText(currentUsername, contract.getPartyBRepresentative())
                    || isSameText(currentUser.getRealName(), contract.getPartyBRepresentative());
            if (!(companyMatch || assignedUserMatch || representativeMatch)) {
                throw new RuntimeException("只有乙方才能签署乙方合同");
            }
            contract.setPartyBSigned(true);
            contract.setPartyBSignedTime(LocalDateTime.now());
        } else {
            throw new RuntimeException("无效的签署方类型");
        }

        String newGeneratedUrl;
        List<Map<String, Object>> products = buildProductsForOrders(contractOrders);

        try {
            // 乙方签署前兜底：若合同乙方名称为空则从订单交付方回填，再生成双章文档
            ensureContractPartyBFromSalesOrder(contract, salesOrder);
            String sealPartyType = partyType;
            if ("B".equals(partyType) && Boolean.TRUE.equals(contract.getPartyASigned())) {
                sealPartyType = null;
            }
            if (products != null && !products.isEmpty()) {
                newGeneratedUrl = documentService.generateContractDocumentWithProducts(contract, contract.getTemplateUrl(), products, sealPartyType);
            } else {
                newGeneratedUrl = documentService.generateContractDocumentWithPartySeal(contract, contract.getTemplateUrl(), sealPartyType);
            }
            contract.setGeneratedUrl(newGeneratedUrl);
        } catch (Exception e) {
            throw new RuntimeException("生成带电子章的合同失败: " + e.getMessage());
        }

        if (Boolean.TRUE.equals(contract.getPartyASigned()) && Boolean.TRUE.equals(contract.getPartyBSigned())) {
            contract.setStatus("已签署");
        } else if (Boolean.TRUE.equals(contract.getPartyASigned()) || Boolean.TRUE.equals(contract.getPartyBSigned())) {
            contract.setStatus("待签署");
        }

        Contract savedContract = contractRepository.save(contract);

        if (!contractOrders.isEmpty()) {
            if (Boolean.TRUE.equals(contract.getPartyASigned()) && Boolean.TRUE.equals(contract.getPartyBSigned())) {
                for (SalesOrder order : contractOrders) {
                    order.setStatus("已盖章");
                    salesOrderRepository.save(order);
                    String chainMarker = "CHAIN_FROM:" + order.getId();
                List<SalesOrder> nextChains = salesOrderRepository.findByPurchaseOrderNo(chainMarker);
                if (nextChains != null && !nextChains.isEmpty()) {
                    for (SalesOrder chain : nextChains) {
                        chain.setStatus("已盖章");
                        salesOrderRepository.save(chain);
                    }
                }
                    String omsNo = order.getOmsOrderNo();
                    String supplier = order.getDeliveryParty();
                if (omsNo != null && !omsNo.isBlank() && supplier != null && !supplier.isBlank()) {
                    List<PurchaseOrder> assignerPos = purchaseOrderRepository.findByOmsOrderNoAndSupplier(omsNo, supplier.trim());
                    for (PurchaseOrder po : assignerPos) {
                        po.setStatus("已盖章");
                        purchaseOrderRepository.save(po);
                    }
                }
                    salesOrderService.markErpEntryPendingForOmsOrder(order.getOmsOrderNo());
                }
            } else if (Boolean.TRUE.equals(contract.getPartyASigned())) {
                for (SalesOrder order : contractOrders) {
                    order.setStatus("待合同盖章");
                    salesOrderRepository.save(order);
                }
            }
        }

        if (contractInvolvesWatchlistParty(savedContract)) {
            boolean full = Boolean.TRUE.equals(savedContract.getPartyASigned()) && Boolean.TRUE.equals(savedContract.getPartyBSigned());
            String sideLabel = "A".equals(partyType) ? "甲方" : "乙方";
            String eventLine = full
                    ? "双方签署完成，合同已签署（销售订单等业务状态已同步）"
                    : (sideLabel + "已完成本侧签署，当前合同状态：" + (savedContract.getStatus() != null ? savedContract.getStatus() : "-"));
            notifyContractWatchlistIfNeeded(salesOrder, savedContract, eventLine);
        }

        try {
            System.out.println("=== Generating Image for contract ===");
            System.out.println("partyASigned: " + contract.getPartyASigned());
            System.out.println("partyBSigned: " + contract.getPartyBSigned());
            System.out.println("newGeneratedUrl: " + newGeneratedUrl);
            
            if (Boolean.TRUE.equals(contract.getPartyASigned()) || Boolean.TRUE.equals(contract.getPartyBSigned())) {
                String protectedImageUrl = pdfService.convertDocxToImage(newGeneratedUrl);
                System.out.println("Generated protectedImageUrl: " + protectedImageUrl);
                if (protectedImageUrl != null) {
                    savedContract.setProtectedImageUrl(protectedImageUrl);
                    savedContract.setProtectedPdfUrl(null);
                    savedContract.setGeneratedUrl(null);
                    savedContract = contractRepository.save(savedContract);
                    System.out.println("Contract saved with protectedImageUrl: " + savedContract.getProtectedImageUrl());
                }
            }
        } catch (Exception e) {
            System.err.println("生成合同图片失败: " + e.getMessage());
            e.printStackTrace();
        }

        return savedContract;
    }

    private boolean isAggregateContract(Contract contract) {
        return contract != null && "MASTER_SELECTION".equalsIgnoreCase(safe(contract.getContractScope()));
    }

    private List<SalesOrder> getContractOrders(Contract contract) {
        if (contract == null) {
            return new ArrayList<>();
        }
        List<SalesOrder> orders;
        if (isAggregateContract(contract)) {
            List<Long> mergedIds = parseMergedSalesOrderIds(contract.getMergedSalesOrderIds());
            orders = mergedIds.isEmpty() ? new ArrayList<>() : new ArrayList<>(salesOrderRepository.findAllById(mergedIds));
            orders.sort(java.util.Comparator.comparingLong(order -> order.getId() != null ? order.getId() : 0L));
            return orders;
        }
        SalesOrder singleOrder = salesOrderRepository.findById(contract.getSalesOrderId()).orElse(null);
        return singleOrder == null ? new ArrayList<>() : new ArrayList<>(java.util.List.of(singleOrder));
    }

    private List<SalesOrder> getActiveContractOrders(Contract contract) {
        List<SalesOrder> activeOrders = new ArrayList<>();
        for (SalesOrder order : getContractOrders(contract)) {
            if (order == null) {
                continue;
            }
            if ("已退回".equals(safe(order.getStatus()))) {
                continue;
            }
            activeOrders.add(order);
        }
        return activeOrders;
    }

    /**
     * 生成合同文档前兜底：若合同乙方名称为空则从订单 deliveryParty 回填，并补全乙方详情（合作管理）。
     * 用于追踪与修复「乙方信息输出空值」问题。
     */
    private void ensureContractPartyBFromSalesOrder(Contract contract, SalesOrder salesOrder) {
        if (contract == null) return;
        if (contract.getPartyBName() != null && !contract.getPartyBName().isBlank()) {
            return;
        }
        if (salesOrder == null || salesOrder.getDeliveryParty() == null || salesOrder.getDeliveryParty().isBlank()) {
            System.out.println("=== [ensureContractPartyB] 合同乙方为空且订单无交付方，无法回填。contractId=" + contract.getId() + " salesOrderId=" + (salesOrder != null ? salesOrder.getId() : null) + " ===");
            return;
        }
        String deliveryParty = salesOrder.getDeliveryParty().trim();
        contract.setPartyBName(deliveryParty);
        contract.setPartyB(deliveryParty);
        System.out.println("=== [ensureContractPartyB] 合同乙方原为空，已从订单 deliveryParty 回填: " + deliveryParty + " (contractId=" + contract.getId() + ") ===");
        PartnerInfo partyB = findLatestPartnerInfoByTitleOrName(deliveryParty);
        if (partyB == null && salesOrder.getDeliveryParty() != null && !salesOrder.getDeliveryParty().trim().equals(deliveryParty)) {
            partyB = findLatestPartnerInfoByTitleOrName(salesOrder.getDeliveryParty());
        }
        if (partyB != null) {
            contract.setPartyBAddress(partyB.getBankAddress());
            contract.setPartyBBank(partyB.getBankName());
            contract.setPartyBAccount(partyB.getBankAccount());
            contract.setPartyBTaxNo(partyB.getTaxNumber());
            contract.setPartyBPhone(partyB.getContactPhone());
            System.out.println("=== [ensureContractPartyB] 已从合作管理补全乙方详情: " + deliveryParty + " ===");
        } else {
            System.out.println("=== [ensureContractPartyB] 合作管理中未找到「" + deliveryParty + "」，乙方地址/开户行/税号等仍为空，请到合作管理-用户信息维护添加 ===");
        }
    }

    /** 甲方兜底：优先订单创建人公司；若缺失再用 operationEntityTitle；再默认飞础科。 */
    private String resolvePartyATitle(SalesOrder salesOrder) {
        if (salesOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
            if (creator != null && creator.getCompanyTitle() != null && !creator.getCompanyTitle().isBlank()) {
                return creator.getCompanyTitle().trim();
            }
        }
        if (salesOrder.getOperationEntityTitle() != null && !salesOrder.getOperationEntityTitle().isBlank()) {
            return salesOrder.getOperationEntityTitle().trim();
        }
        return "飞础科智慧科技（上海）有限公司";
    }

    @Transactional(readOnly = true)
    public Map<String, String> previewNumbersForSalesOrder(Long salesOrderId, String deliveryParty, String partyBRepresentative) {
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
                .orElseThrow(() -> new IllegalArgumentException("销售订单不存在"));
        String effectiveDeliveryParty = normalizeBlankToNull(deliveryParty);
        if (effectiveDeliveryParty == null) {
            effectiveDeliveryParty = normalizeBlankToNull(salesOrder.getDeliveryParty());
        }
        if (effectiveDeliveryParty == null) {
            throw new IllegalArgumentException("请先选择交付方");
        }
        AssigneeResolution assigneeResolution = resolveAssigneeByDeliveryPartyForPreview(effectiveDeliveryParty, partyBRepresentative);
        User resolvedAssignUser = assigneeResolution.user;
        User assignerUser = resolvePreviewAssignerUser(salesOrder, resolvedAssignUser);
        String contractNo = generateContractNo(salesOrder, resolvedAssignUser);
        String purchaseOrderNo = assignerUser != null
                ? buildSinglePurchaseOrderNo(salesOrder, assignerUser, effectiveDeliveryParty)
                : "";
        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("contractNo", contractNo);
        result.put("purchaseOrderNo", purchaseOrderNo);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, String> previewNumbersForMasterSelection(List<Long> salesOrderIds, String deliveryParty, String partyBRepresentative) {
        if (salesOrderIds == null || salesOrderIds.isEmpty()) {
            throw new IllegalArgumentException("请选择至少一条销售订单");
        }
        List<SalesOrder> selectedOrders = salesOrderRepository.findAllById(salesOrderIds);
        if (selectedOrders.isEmpty()) {
            throw new IllegalArgumentException("未找到有效的销售订单");
        }
        SalesOrder firstOrder = selectedOrders.get(0);
        String effectiveDeliveryParty = normalizeBlankToNull(deliveryParty);
        if (effectiveDeliveryParty == null) {
            effectiveDeliveryParty = normalizeBlankToNull(firstOrder.getDeliveryParty());
        }
        if (effectiveDeliveryParty == null) {
            throw new IllegalArgumentException("请先选择交付方");
        }
        AssigneeResolution assigneeResolution = resolveAssigneeByDeliveryParty(effectiveDeliveryParty, partyBRepresentative);
        User resolvedAssignUser = assigneeResolution.user;
        String mergeSelectionKey = buildMergeSelectionKey(new java.util.LinkedHashSet<>(salesOrderIds));
        User purchaseCreator = null;
        for (SalesOrder order : selectedOrders) {
            User candidate = resolvePreviewAssignerUser(order, resolvedAssignUser);
            if (candidate != null && candidate.getId() != null) {
                purchaseCreator = candidate;
                break;
            }
        }
        if (purchaseCreator == null) {
            purchaseCreator = firstOrder.getCreatedBy() != null ? userRepository.findById(firstOrder.getCreatedBy()).orElse(null) : null;
        }
        String contractNo = generateContractNo(firstOrder, resolvedAssignUser);
        String purchaseOrderNo = purchaseCreator != null
                ? buildMergedPurchaseOrderNo(firstOrder, purchaseCreator, effectiveDeliveryParty, mergeSelectionKey)
                : "";
        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("contractNo", contractNo);
        result.put("purchaseOrderNo", purchaseOrderNo);
        return result;
    }

    private User resolvePreviewAssignerUser(SalesOrder salesOrder, User resolvedAssignUser) {
        if (salesOrder == null) {
            return null;
        }
        String previousAssignedUsername = normalizeBlankToNull(salesOrder.getAssignedUsername());
        if (previousAssignedUsername != null && resolvedAssignUser != null && !previousAssignedUsername.equals(resolvedAssignUser.getUsername())) {
            User previousAssignee = userRepository.findByUsername(previousAssignedUsername).orElse(null);
            if (previousAssignee != null) {
                return previousAssignee;
            }
        }
        return salesOrder.getCreatedBy() != null ? userRepository.findById(salesOrder.getCreatedBy()).orElse(null) : null;
    }

    private String buildSinglePurchaseOrderNo(SalesOrder salesOrder, User assigner, String supplier) {
        String omsOrderNo = normalizeBlankToNull(salesOrder != null ? salesOrder.getOmsOrderNo() : null);
        if (omsOrderNo == null || assigner == null || assigner.getId() == null) {
            return "";
        }
        String assignerMark = assigner.getUsername() != null ? assigner.getUsername().trim() : String.valueOf(assigner.getId());
        String supplierText = supplier != null ? supplier : "";
        String supplierMark = supplierText.length() > 8 ? supplierText.substring(0, 8) : supplierText;
        return "PO-" + omsOrderNo + "-" + assignerMark + "-" + supplierMark + "-SO" + salesOrder.getId();
    }

    private String buildMergedPurchaseOrderNo(SalesOrder firstOrder, User assigner, String supplier, String mergeSelectionKey) {
        String assignerMark = normalizeBlankToNull(assigner != null ? assigner.getUsername() : null);
        if (assignerMark == null && assigner != null && assigner.getId() != null) {
            assignerMark = String.valueOf(assigner.getId());
        }
        String supplierMark = supplier != null && supplier.length() > 8 ? supplier.substring(0, 8) : (supplier != null ? supplier : "");
        return "PO-M" + firstOrder.getMasterId() + "-" + assignerMark + "-" + supplierMark + "-" + mergeSelectionKey.substring(0, Math.min(8, mergeSelectionKey.length()));
    }

    private String ensureContractNoAvailable(String contractNo, Long currentContractId) {
        String normalized = normalizeBlankToNull(contractNo);
        if (normalized == null) {
            throw new IllegalArgumentException("合同编号不能为空");
        }
        Contract existing = contractRepository.findByContractNo(normalized).orElse(null);
        if (existing != null && (currentContractId == null || !currentContractId.equals(existing.getId()))) {
            throw new IllegalArgumentException("合同编号已存在，请修改后重试");
        }
        return normalized;
    }

    private String ensurePurchaseOrderNoAvailable(String purchaseOrderNo, Long currentPurchaseOrderId) {
        String normalized = normalizeBlankToNull(purchaseOrderNo);
        if (normalized == null) {
            throw new IllegalArgumentException("采购订单号不能为空");
        }
        PurchaseOrder existing = purchaseOrderRepository.findFirstByPurchaseOrderNoOrderByIdDesc(normalized).orElse(null);
        if (existing != null && (currentPurchaseOrderId == null || !currentPurchaseOrderId.equals(existing.getId()))) {
            throw new IllegalArgumentException("采购订单号已存在，请修改后重试");
        }
        return normalized;
    }

    private AssigneeResolution resolveAssigneeByDeliveryPartyForPreview(String effectiveDeliveryParty, String partyBRepresentative) {
        try {
            return resolveAssigneeByDeliveryParty(effectiveDeliveryParty, partyBRepresentative);
        } catch (IllegalArgumentException ex) {
            String deliveryPartyTrimmed = normalizeBlankToNull(effectiveDeliveryParty);
            if (deliveryPartyTrimmed == null) {
                throw ex;
            }
            List<PartnerInfo> partyBList = partnerInfoRepository.findAllByTitleOrNameTrimmed(deliveryPartyTrimmed);
            if (partyBList == null || partyBList.isEmpty()) {
                throw ex;
            }
            for (PartnerInfo item : partyBList) {
                String username = normalizeBlankToNull(item.getUsername());
                if (username == null) {
                    continue;
                }
                User resolvedUser = userRepository.findByUsername(username).orElse(null);
                if (resolvedUser != null) {
                    return new AssigneeResolution(item, resolvedUser);
                }
            }
            throw ex;
        }
    }

    private String generateContractNo(SalesOrder salesOrder, User assignee) {
        LocalDate today = LocalDate.now();
        String prefix = buildContractNoPrefix(salesOrder, assignee, today);
        int nextSeq = contractRepository.findFirstByContractNoStartingWithOrderByContractNoDesc(prefix)
                .map(Contract::getContractNo)
                .map(existing -> parseNextContractSequence(existing, prefix))
                .orElse(1);
        if (nextSeq > 99) {
            throw new IllegalStateException("合同编号前缀「" + prefix + "」当天序号已超过 99，请联系管理员处理");
        }
        return prefix + String.format("%02d", nextSeq);
    }

    private String buildContractNoPrefix(SalesOrder salesOrder, User assignee, LocalDate today) {
        String datePart = today.format(DATE_FORMATTER);
        SalesOrder rootOrder = resolveRootSourceOrder(salesOrder);
        User currentCreator = findUserById(salesOrder != null ? salesOrder.getCreatedBy() : null);
        User rootCreator = findUserById(rootOrder != null ? rootOrder.getCreatedBy() : null);

        boolean assignToRexiang = isRexiangUser(assignee)
                || containsCompany(salesOrder != null ? salesOrder.getDeliveryParty() : null, REXIANG_TITLE);
        boolean currentFromFeichuke = isFeichukeUser(currentCreator);
        boolean rootFromFeichuke = isFeichukeUser(rootCreator);
        boolean chainOrder = isChainOrder(salesOrder);
        boolean rootThirdParty = isThirdPartyOrder(rootOrder != null ? rootOrder.getOrderType() : null);

        if (assignToRexiang) {
            if (chainOrder && rootFromFeichuke && rootThirdParty) {
                return "D" + datePart + resolveInitials(
                        firstNonBlank(
                                salesOrder != null ? salesOrder.getOfflineSales() : null,
                                rootOrder != null ? rootOrder.getOfflineSales() : null
                        )
                );
            }
            if (!chainOrder && currentFromFeichuke) {
                return "D" + datePart + resolveInitials(
                        firstNonBlank(
                                salesOrder != null ? salesOrder.getEcommerceSalesName() : null,
                                currentCreator != null ? currentCreator.getRealName() : null,
                                rootOrder != null ? rootOrder.getEcommerceSalesName() : null
                        )
                );
            }
            return datePart + resolveInitials(
                    firstNonBlank(
                            salesOrder != null ? salesOrder.getOfflineSales() : null,
                            rootOrder != null ? rootOrder.getOfflineSales() : null,
                            salesOrder != null ? salesOrder.getEcommerceSalesName() : null,
                            currentCreator != null ? currentCreator.getRealName() : null
                    )
            );
        }

        if (currentFromFeichuke) {
            return "CG" + datePart + resolveInitials(
                    firstNonBlank(
                            salesOrder != null ? salesOrder.getEcommerceSalesName() : null,
                            currentCreator != null ? currentCreator.getRealName() : null
                    )
            );
        }

        return datePart + resolveInitials(
                firstNonBlank(
                        salesOrder != null ? salesOrder.getOfflineSales() : null,
                        rootOrder != null ? rootOrder.getOfflineSales() : null,
                        salesOrder != null ? salesOrder.getEcommerceSalesName() : null,
                        currentCreator != null ? currentCreator.getRealName() : null
                )
        );
    }

    private int parseNextContractSequence(String contractNo, String prefix) {
        if (contractNo == null || !contractNo.startsWith(prefix) || contractNo.length() < prefix.length() + 2) {
            return 1;
        }
        String seqText = contractNo.substring(contractNo.length() - 2);
        try {
            return Integer.parseInt(seqText) + 1;
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private SalesOrder resolveRootSourceOrder(SalesOrder salesOrder) {
        if (salesOrder == null) {
            return null;
        }
        SalesOrder current = salesOrder;
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (current != null) {
            Long currentId = current.getId();
            if (currentId != null && !visited.add(currentId)) {
                return current;
            }
            String purchaseOrderNo = current.getPurchaseOrderNo();
            if (purchaseOrderNo == null || !purchaseOrderNo.startsWith(CHAIN_PREFIX)) {
                return current;
            }
            try {
                Long sourceId = Long.parseLong(purchaseOrderNo.substring(CHAIN_PREFIX.length()).trim());
                SalesOrder source = salesOrderRepository.findById(sourceId).orElse(null);
                if (source == null) {
                    return current;
                }
                current = source;
            } catch (NumberFormatException ex) {
                return current;
            }
        }
        return salesOrder;
    }

    private boolean isChainOrder(SalesOrder salesOrder) {
        return salesOrder != null
                && salesOrder.getPurchaseOrderNo() != null
                && salesOrder.getPurchaseOrderNo().startsWith(CHAIN_PREFIX);
    }

    private boolean isThirdPartyOrder(String orderType) {
        return orderType != null && orderType.trim().contains("第三方");
    }

    private User findUserById(Long userId) {
        return userId != null ? userRepository.findById(userId).orElse(null) : null;
    }

    private boolean isFeichukeUser(User user) {
        if (user == null) {
            return false;
        }
        if (user.getUsername() != null && FEICHUKE_USERNAMES.contains(user.getUsername().trim())) {
            return true;
        }
        return containsCompany(user.getCompanyTitle(), FEICHUKE_TITLE);
    }

    private boolean isRexiangUser(User user) {
        return user != null && containsCompany(user.getCompanyTitle(), REXIANG_TITLE);
    }

    private boolean containsCompany(String companyTitle, String targetCompany) {
        return companyTitle != null && !companyTitle.isBlank() && companyTitle.trim().contains(targetCompany);
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

    private String resolveInitials(String sourceName) {
        return salesOrderService.getCodeInitials(sourceName);
    }

    private String getValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private boolean isSameText(String left, String right) {
        if (left == null || right == null) return false;
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
