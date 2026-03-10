package com.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.entity.Contract;
import com.oms.entity.PartnerInfo;
import com.oms.entity.PurchaseOrder;
import com.oms.entity.SalesOrder;
import com.oms.entity.User;
import com.oms.entity.ContractTemplate;
import com.oms.repository.ContractRepository;
import com.oms.repository.ContractTemplateRepository;
import com.oms.repository.PartnerInfoRepository;
import com.oms.repository.PurchaseOrderRepository;
import com.oms.repository.SalesOrderRepository;
import com.oms.repository.UserRepository;
import com.oms.util.MoneyUtil;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public Page<Contract> getAllContracts(Pageable pageable) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("ContractService - Current username from SecurityContext: " + currentUsername);
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        System.out.println("ContractService - Current user: " + (currentUser != null ? currentUser.getRealName() + ", role: " + currentUser.getRole() : "null"));
        
        return contractRepository.findAll((Specification<Contract>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                System.out.println("ContractService - Adding permission filter for user: " + currentUsername);
                
                List<Long> visibleSalesOrderIds = new ArrayList<>();
                
                List<SalesOrder> allSalesOrders = salesOrderRepository.findAll();
                for (SalesOrder order : allSalesOrders) {
                    if ("已退回".equals(order.getStatus())) {
                        continue;
                    }
                    if (order.getCreatedBy() != null && order.getCreatedBy().equals(currentUser.getId())) {
                        visibleSalesOrderIds.add(order.getId());
                    } else if (order.getAssignedUsername() != null && order.getAssignedUsername().equals(currentUsername)) {
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

    public Optional<Contract> getContractById(Long id) {
        return contractRepository.findById(id);
    }

    public Optional<Contract> getContractBySalesOrderId(Long salesOrderId) {
        return contractRepository.findBySalesOrderId(salesOrderId);
    }

    @Transactional
    public Contract createContractFromSalesOrder(Long salesOrderId, String templateUrl, String partyBRepresentative, String platformName, String paymentMethod, String deliveryPartyFromRequest) {
        System.out.println("=== createContractFromSalesOrder called ===");
        System.out.println("salesOrderId: " + salesOrderId);
        System.out.println("templateUrl (raw): " + templateUrl);
        System.out.println("partyBRepresentative: " + partyBRepresentative);
        System.out.println("platformName: " + platformName);
        System.out.println("paymentMethod: " + paymentMethod);
        System.out.println("deliveryPartyFromRequest: " + deliveryPartyFromRequest);
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
        
        // 甲方抬头：仅当订单尚未有录入的甲方时，才用请求中的 platformName 回写；已有录入（如创建人填的震坤行）则保留，不被指派/生成合同时的默认值覆盖
        boolean orderHasPartyA = (salesOrder.getPlatformName() != null && !salesOrder.getPlatformName().isBlank())
                || (salesOrder.getOperationEntityTitle() != null && !salesOrder.getOperationEntityTitle().isBlank());
        if (!orderHasPartyA && platformName != null && !platformName.isEmpty()) {
            salesOrder.setPlatformName(platformName.trim());
            salesOrder.setOperationEntityTitle(platformName.trim());
            System.out.println("Updated salesOrder platformName and operationEntityTitle to: " + platformName);
        } else if (orderHasPartyA) {
            System.out.println("Preserved existing order Party A (platformName/operationEntityTitle), not overwriting with request: " + platformName);
        }
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            salesOrder.setPaymentMethod(paymentMethod);
            System.out.println("Updated salesOrder paymentMethod to: " + paymentMethod);
        }
        // 采购/指派列表有交付方但合同无乙方时：用请求体中的 deliveryParty 回填订单并保证合同能取到乙方
        if ((salesOrder.getDeliveryParty() == null || salesOrder.getDeliveryParty().isBlank()) && deliveryPartyFromRequest != null && !deliveryPartyFromRequest.isBlank()) {
            salesOrder.setDeliveryParty(deliveryPartyFromRequest.trim());
            salesOrder = salesOrderRepository.save(salesOrder);
            System.out.println("=== 订单 deliveryParty 原为空，已从请求体回填: " + salesOrder.getDeliveryParty() + " ===");
        } else {
            salesOrder = salesOrderRepository.save(salesOrder);
        }
        
        System.out.println("=== SalesOrder data ===");
        // 甲方：优先用订单已录入的 platformName/operationEntityTitle（创建人填的震坤行等），否则用请求传入的 platformName，再否则兜底
        String partyATitle = (salesOrder.getPlatformName() != null && !salesOrder.getPlatformName().isBlank())
                ? salesOrder.getPlatformName().trim()
                : ((salesOrder.getOperationEntityTitle() != null && !salesOrder.getOperationEntityTitle().isBlank())
                        ? salesOrder.getOperationEntityTitle().trim()
                        : ((platformName != null && !platformName.isBlank())
                                ? platformName.trim()
                                : resolvePartyATitle(salesOrder)));
        System.out.println("partyATitle (甲方): " + partyATitle);
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
        System.out.println("platformName: " + salesOrder.getPlatformName());
        System.out.println("paymentMethod: " + salesOrder.getPaymentMethod());
        System.out.println("receiverAddress: " + salesOrder.getReceiverAddress());
        System.out.println("deliveryDate: " + salesOrder.getDeliveryDate());
        System.out.println("orderDate: " + salesOrder.getOrderDate());

        String contractNo = generateContractNo();

        Contract contract = new Contract();
        contract.setContractNo(contractNo);
        contract.setName("销售合同-" + contractNo);
        contract.setPartyA(partyATitle);
        contract.setPartyB(effectiveDeliveryParty);
        contract.setSalesOrderId(salesOrderId);
        contract.setSalesId(salesOrder.getEcommerceSalesId());
        contract.setSalesName(salesOrder.getEcommerceSalesName());
        contract.setPartyAName(partyATitle);
        contract.setPartyBName(effectiveDeliveryParty);
        contract.setProductName(salesOrder.getModel());
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
        contract.setPlatformName(salesOrder.getPlatformName());
        // 仅工业电商侧订单有顶层客户名时填充，其他用户合同为空，模板中 ${settlementParty} 被替换为空不显示
        contract.setSettlementPartyName(salesOrder.getTopLevelCustomerName() != null && !salesOrder.getTopLevelCustomerName().isBlank()
                ? salesOrder.getTopLevelCustomerName() : "");
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

        if (partyATitle != null && !partyATitle.isBlank()) {
            String partyATitleTrimmed = partyATitle.trim();
            PartnerInfo partyA = partnerInfoRepository.findByTitle(partyATitleTrimmed);
            if (partyA == null && !partyATitleTrimmed.equals(partyATitle)) {
                partyA = partnerInfoRepository.findByTitle(partyATitle);
            }
            if (partyA != null) {
                System.out.println("=== PartyA PartnerInfo found ===");
                contract.setPartyAAddress(partyA.getBankAddress());
                contract.setPartyABank(partyA.getBankName());
                contract.setPartyAAccount(partyA.getBankAccount());
                contract.setPartyATaxNo(partyA.getTaxNumber());
                contract.setPartyAPhone(partyA.getContactPhone());
            } else {
                System.out.println("=== PartyA PartnerInfo NOT found for title: " + partyATitle + " ===");
            }
        }

        if (effectiveDeliveryParty != null && !effectiveDeliveryParty.isBlank()) {
            String deliveryPartyTrimmed = effectiveDeliveryParty.trim();
            PartnerInfo partyB = partnerInfoRepository.findByTitle(deliveryPartyTrimmed);
            if (partyB == null && !deliveryPartyTrimmed.equals(effectiveDeliveryParty)) {
                partyB = partnerInfoRepository.findByTitle(effectiveDeliveryParty);
            }
            if (partyB == null) {
                partyB = partnerInfoRepository.findOneByTitleTrimmed(deliveryPartyTrimmed).orElse(null);
            }
            List<User> partyBUsersByCompany = userRepository.findByCompanyTitle(deliveryPartyTrimmed);
            if (partyBUsersByCompany == null || partyBUsersByCompany.isEmpty()) {
                partyBUsersByCompany = userRepository.findByCompanyTitle(effectiveDeliveryParty);
            }
            User resolvedAssignUser = null;

            if (partyB != null) {
                System.out.println("=== PartyB PartnerInfo found ===");
                System.out.println("partyB.getBankAddress(): " + partyB.getBankAddress());
                System.out.println("partyB.getBankName(): " + partyB.getBankName());
                System.out.println("partyB.getBankAccount(): " + partyB.getBankAccount());
                System.out.println("partyB.getTaxNumber(): " + partyB.getTaxNumber());
                System.out.println("partyB.getContactPhone(): " + partyB.getContactPhone());
                System.out.println("partyB.getUsername(): " + partyB.getUsername());
                
                contract.setPartyBAddress(partyB.getBankAddress());
                contract.setPartyBBank(partyB.getBankName());
                contract.setPartyBAccount(partyB.getBankAccount());
                contract.setPartyBTaxNo(partyB.getTaxNumber());
                contract.setPartyBPhone(partyB.getContactPhone());
                
                if (partyBRepresentative != null && !partyBRepresentative.isEmpty()) {
                    contract.setPartyBRepresentative(partyBRepresentative);
                    System.out.println("=== PartyB Representative from parameter: " + partyBRepresentative + " ===");
                } else {
                    if (partyBUsersByCompany != null && !partyBUsersByCompany.isEmpty()) {
                        User partyBUser = partyBUsersByCompany.get(0);
                        contract.setPartyBRepresentative(partyBUser.getRealName());
                        System.out.println("=== PartyB Representative found: " + partyBUser.getRealName() + " ===");
                    }
                }
            } else {
                System.out.println("=== PartyB PartnerInfo NOT found for title: " + effectiveDeliveryParty
                        + " （请到「合作管理-用户信息维护」添加与交付方抬头完全一致的乙方信息，否则合同乙方地址/开户行/税号等为空） ===");
            }

            // 兜底指派：优先按输入的乙方业务员姓名在交付方公司用户中匹配；否则取该公司首个用户；再否则尝试 partnerInfo.username
            if (partyBRepresentative != null && !partyBRepresentative.isBlank()
                    && partyBUsersByCompany != null && !partyBUsersByCompany.isEmpty()) {
                for (User u : partyBUsersByCompany) {
                    if (u != null && u.getRealName() != null
                            && u.getRealName().trim().equals(partyBRepresentative.trim())) {
                        resolvedAssignUser = u;
                        break;
                    }
                }
            }
            if (resolvedAssignUser == null && partyBUsersByCompany != null && !partyBUsersByCompany.isEmpty()) {
                resolvedAssignUser = partyBUsersByCompany.get(0);
            }
            if (resolvedAssignUser == null && partyBRepresentative != null && !partyBRepresentative.isBlank()) {
                List<User> allUsers = userRepository.findAll();
                for (User u : allUsers) {
                    if (u == null) continue;
                    String rn = u.getRealName() != null ? u.getRealName().trim() : "";
                    String un = u.getUsername() != null ? u.getUsername().trim() : "";
                    String target = partyBRepresentative.trim();
                    if (target.equals(rn) || target.equals(un)) {
                        resolvedAssignUser = u;
                        break;
                    }
                }
            }
            if (resolvedAssignUser == null && partyB != null && partyB.getUsername() != null && !partyB.getUsername().isBlank()) {
                resolvedAssignUser = userRepository.findByUsername(partyB.getUsername()).orElse(null);
            }

            if (resolvedAssignUser != null && resolvedAssignUser.getUsername() != null && !resolvedAssignUser.getUsername().isBlank()) {
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
                    createOrUpdatePurchaseOrderForAssigner(salesOrder, assignerUser);
                }
                if (contract.getPartyBRepresentative() == null || contract.getPartyBRepresentative().isBlank()) {
                    contract.setPartyBRepresentative(resolvedAssignUser.getRealName());
                }
                salesOrder.setAssignedUsername(resolvedAssignUser.getUsername());
                salesOrder.setAssignTime(LocalDateTime.now());
                if ("已退回".equals(salesOrder.getStatus())) {
                    salesOrder.setStatus("待确认订单");
                }
                salesOrderRepository.save(salesOrder);
                if (!isSourceFactory(resolvedAssignUser)) {
                    createOrUpdateNextLevelSalesOrder(salesOrder, resolvedAssignUser);
                } else {
                    System.out.println("=== Skip next-level order: assignee is source factory (出货方) ===");
                }
                System.out.println("=== SalesOrder assignedUsername set to: " + resolvedAssignUser.getUsername() + " ===");
                System.out.println("=== SalesOrder status set to: " + salesOrder.getStatus() + " ===");
            } else {
                System.out.println("=== SalesOrder assignment skipped: no matched user for deliveryParty " + effectiveDeliveryParty + " ===");
            }
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
        }

        return savedContract;
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
        po.setOmsOrderNo(omsOrderNo);
        // 不带 D = 常规采购订单（自营），带 D = 第三方采购订单
        po.setPurchaseType(omsOrderNo != null && omsOrderNo.startsWith("D") ? "第三方采购订单" : "常规采购订单");
        po.setStatus("待确认");
        // 被指派方的采购单 = 向甲方采购，供应商 = 甲方（platformName），不是 deliveryParty（乙方）
        String supplier = salesOrder.getPlatformName();
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
    private void createOrUpdatePurchaseOrderForAssigner(SalesOrder salesOrder, User assigner) {
        if (salesOrder == null || assigner == null || assigner.getId() == null) return;
        String omsOrderNo = salesOrder.getOmsOrderNo();
        if (omsOrderNo == null || omsOrderNo.isBlank()) return;
        String supplier = salesOrder.getDeliveryParty() != null ? salesOrder.getDeliveryParty() : "";
        if (supplier.isBlank()) return;

        PurchaseOrder po = purchaseOrderRepository
                .findFirstByOmsOrderNoAndCreatedByAndSupplierOrderByIdDesc(omsOrderNo, assigner.getId(), supplier)
                .orElseGet(PurchaseOrder::new);

        if (po.getPurchaseOrderNo() == null || po.getPurchaseOrderNo().isBlank()) {
            String assignerMark = assigner.getUsername() != null ? assigner.getUsername().trim() : String.valueOf(assigner.getId());
            po.setPurchaseOrderNo("PO-" + omsOrderNo + "-" + assignerMark + "-" + (supplier.length() > 8 ? supplier.substring(0, 8) : supplier));
        }
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

    /**
     * 指派成功后，给被指派方自动生成“下一层销售订单”
     * 场景：sonmin -> 坚领 后，坚领销售管理中应出现“卖给飞础科”的销售订单，便于继续指派热像科技。
     */
    @SuppressWarnings("unchecked")
    private void createOrUpdateNextLevelSalesOrder(SalesOrder sourceOrder, User assignee) {
        if (sourceOrder == null || assignee == null || assignee.getId() == null) return;

        String chainMarker = "CHAIN_FROM:" + sourceOrder.getId();
        SalesOrder nextOrder = salesOrderRepository
                .findFirstByPurchaseOrderNoAndCreatedByOrderByIdDesc(chainMarker, assignee.getId())
                .orElseGet(SalesOrder::new);

        // 甲方抬头：来源订单创建人所属公司（如飞础科）；兜底来源平台名
        String partyATitle = sourceOrder.getPlatformName();
        if (sourceOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(sourceOrder.getCreatedBy()).orElse(null);
            if (creator != null && creator.getCompanyTitle() != null && !creator.getCompanyTitle().isBlank()) {
                partyATitle = creator.getCompanyTitle().trim();
            }
        }

        nextOrder.setCreatedBy(assignee.getId());
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
        nextOrder.setReceiverName(sourceOrder.getReceiverName());
        nextOrder.setReceiverPhone(sourceOrder.getReceiverPhone());
        nextOrder.setReceiverAddress(sourceOrder.getReceiverAddress());
        nextOrder.setMaterialNo(sourceOrder.getMaterialNo());
        nextOrder.setPlatformSku(sourceOrder.getPlatformSku());
        nextOrder.setModel(sourceOrder.getModel());
        nextOrder.setProductConfig(sourceOrder.getProductConfig());
        nextOrder.setWarrantyPeriod(sourceOrder.getWarrantyPeriod());
        nextOrder.setQuantity(sourceOrder.getQuantity());

        java.math.BigDecimal nextUnitPrice = sourceOrder.getDeliveryPartyPurchasePrice() != null
                ? sourceOrder.getDeliveryPartyPurchasePrice()
                : sourceOrder.getTaxIncludedPrice();
        nextOrder.setTaxIncludedPrice(nextUnitPrice);
        if (nextUnitPrice != null && sourceOrder.getQuantity() != null) {
            nextOrder.setTaxIncludedTotal(nextUnitPrice.multiply(new java.math.BigDecimal(sourceOrder.getQuantity())));
        } else {
            nextOrder.setTaxIncludedTotal(sourceOrder.getTaxIncludedTotal());
        }

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
    public Contract signContract(Long contractId, String partyType) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("合同不存在"));
        SalesOrder salesOrder = salesOrderRepository.findById(contract.getSalesOrderId()).orElse(null);

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
        
        if (currentUser == null) {
            throw new RuntimeException("当前用户不存在");
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
        List<Map<String, Object>> products = new ArrayList<>();
        
        if (salesOrder != null && salesOrder.getOrderDetails() != null && !salesOrder.getOrderDetails().isEmpty()) {
            try {
                Map<String, Object> details = objectMapper.readValue(salesOrder.getOrderDetails(), Map.class);
                if (details.get("products") != null) {
                    products = (List<Map<String, Object>>) details.get("products");
                    
                    System.out.println("=== [signContract] Calculating deduction prices for products ===");
                    System.out.println("Global deductionRate: " + salesOrder.getDeductionRate());
                    
                    for (Map<String, Object> product : products) {
                        try {
                            if (salesOrder.getDeliveryPartyPurchasePrice() != null) {
                                java.math.BigDecimal unit = salesOrder.getDeliveryPartyPurchasePrice();
                                Object qtyObj = product.get("quantity");
                                int qty = (qtyObj != null && qtyObj.toString().matches("\\d+")) ? Integer.parseInt(qtyObj.toString()) : (salesOrder.getQuantity() != null ? salesOrder.getQuantity() : 1);
                                java.math.BigDecimal total = unit.multiply(new java.math.BigDecimal(qty));
                                product.put("taxIncludedPrice", unit.setScale(2, java.math.RoundingMode.HALF_UP).toString());
                                product.put("taxIncludedTotal", total.setScale(2, java.math.RoundingMode.HALF_UP).toString());
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

        if (salesOrder != null) {
            if (Boolean.TRUE.equals(contract.getPartyASigned()) && Boolean.TRUE.equals(contract.getPartyBSigned())) {
                salesOrder.setStatus("已盖章");
            } else if (Boolean.TRUE.equals(contract.getPartyASigned())) {
                salesOrder.setStatus("待合同盖章");
            }
            salesOrderRepository.save(salesOrder);
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
        PartnerInfo partyB = partnerInfoRepository.findByTitle(deliveryParty);
        if (partyB == null && salesOrder.getDeliveryParty() != null && !salesOrder.getDeliveryParty().trim().equals(deliveryParty)) {
            partyB = partnerInfoRepository.findByTitle(salesOrder.getDeliveryParty());
        }
        if (partyB == null) {
            partyB = partnerInfoRepository.findOneByTitleTrimmed(deliveryParty).orElse(null);
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

    /** 甲方：订单有代运营主体则用，否则用订单创建人所在公司，再否则用系统默认（按账号甲方/乙方身份流转） */
    private String resolvePartyATitle(SalesOrder salesOrder) {
        if (salesOrder.getOperationEntityTitle() != null && !salesOrder.getOperationEntityTitle().isBlank()) {
            return salesOrder.getOperationEntityTitle().trim();
        }
        if (salesOrder.getCreatedBy() != null) {
            User creator = userRepository.findById(salesOrder.getCreatedBy()).orElse(null);
            if (creator != null && creator.getCompanyTitle() != null && !creator.getCompanyTitle().isBlank()) {
                return creator.getCompanyTitle().trim();
            }
        }
        return "飞础科智慧科技（上海）有限公司";
    }

    private String generateContractNo() {
        String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
        String prefix = "HT" + dateStr;
        
        int seq = 1;
        String contractNo;
        do {
            contractNo = prefix + String.format("%04d", seq++);
        } while (contractRepository.findByContractNo(contractNo).isPresent());
        
        return contractNo;
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
