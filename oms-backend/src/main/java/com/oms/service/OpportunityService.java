package com.oms.service;

import com.oms.entity.Opportunity;
import com.oms.entity.OpportunityProduct;
import com.oms.repository.OpportunityProductRepository;
import com.oms.repository.OpportunityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OpportunityService {

    @Autowired
    private OpportunityRepository repository;
    
    @Autowired
    private OpportunityProductRepository opportunityProductRepository;

    public List<Opportunity> getAllOpportunities() {
        List<Opportunity> list = repository.findAllByOrderByCreateTimeDesc();
        for (Opportunity opp : list) {
            applySummarySnapshot(opp);
        }
        return list;
    }

    public Page<Opportunity> getOpportunitiesWithPagination(Pageable pageable) {
        Page<Opportunity> page = repository.findAllByOrderByCreateTimeDesc(pageable);
        return page.map(opp -> {
            applySummarySnapshot(opp);
            return opp;
        });
    }

    @Transactional
    public Opportunity createOpportunity(Opportunity opportunity) {
        // 自动生成客户编码: KH + yyyyMMddHHmmss
        if (opportunity.getCustomerCode() == null || opportunity.getCustomerCode().isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            opportunity.setCustomerCode("KH" + timestamp);
        }
        return repository.save(opportunity);
    }

    @Transactional
    public Opportunity updateOpportunity(Long id, Opportunity opportunityDetails) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
        Opportunity opportunity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + id));

        opportunity.setTitle(opportunityDetails.getTitle());
        opportunity.setCategory(opportunityDetails.getCategory());
        opportunity.setSource(opportunityDetails.getSource());
        opportunity.setCustomerName(opportunityDetails.getCustomerName());
        opportunity.setInquiryHeader(opportunityDetails.getInquiryHeader());
        opportunity.setOrderHeader(opportunityDetails.getOrderHeader());
        opportunity.setContact(opportunityDetails.getContact());
        opportunity.setPhone(opportunityDetails.getPhone());
        opportunity.setPlatform(opportunityDetails.getPlatform());
        opportunity.setServiceProvider(opportunityDetails.getServiceProvider());
        opportunity.setShippingChannel(opportunityDetails.getShippingChannel());
        opportunity.setExpectedDate(opportunityDetails.getExpectedDate());
        opportunity.setRegion(opportunityDetails.getRegion());
        opportunity.setIndustry(opportunityDetails.getIndustry());
        opportunity.setProblemSolved(opportunityDetails.getProblemSolved());
        opportunity.setBudget(opportunityDetails.getBudget());
        opportunity.setCompetitor(opportunityDetails.getCompetitor());
        opportunity.setDeliveryPeriod(opportunityDetails.getDeliveryPeriod());
        opportunity.setEcommerceSales(opportunityDetails.getEcommerceSales());
        opportunity.setOfflineSales(opportunityDetails.getOfflineSales());
        opportunity.setStage(opportunityDetails.getStage());
        opportunity.setFollowUpTime(opportunityDetails.getFollowUpTime());
        opportunity.setLatestFollowUpRecord(opportunityDetails.getLatestFollowUpRecord());

        return repository.save(opportunity);
    }

    @Transactional
    public void deleteOpportunity(Long id) {
        if (id != null) {
            // 先删除关联的产品
            opportunityProductRepository.deleteByOpportunityId(id);
            repository.deleteById(id);
        }
    }

    public Optional<Opportunity> getOpportunityById(Long id) {
        if (id == null) return Optional.empty();
        return repository.findById(id);
    }
    
    public List<OpportunityProduct> getOpportunityProducts(Long opportunityId) {
        return opportunityProductRepository.findByOpportunityId(opportunityId);
    }
    
    @Transactional
    public OpportunityProduct addOpportunityProduct(OpportunityProduct product) {
        OpportunityProduct saved = opportunityProductRepository.save(product);
        if (saved.getOpportunity() != null && saved.getOpportunity().getId() != null) {
            recalculateOpportunitySummary(saved.getOpportunity().getId());
        }
        return saved;
    }
    
    @Transactional
    public void updateOpportunityProducts(Long opportunityId, List<OpportunityProduct> products) {
        // 先删除原有产品
        opportunityProductRepository.deleteByOpportunityId(opportunityId);
        // 保存新产品
        for (OpportunityProduct product : products) {
            product.setOpportunity(repository.findById(opportunityId)
                    .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + opportunityId)));
            opportunityProductRepository.save(product);
        }
        recalculateOpportunitySummary(opportunityId);
    }
    
    @Transactional
    public void deleteOpportunityProduct(Long productId) {
        OpportunityProduct existing = opportunityProductRepository.findById(productId).orElse(null);
        Long opportunityId = existing != null && existing.getOpportunity() != null ? existing.getOpportunity().getId() : null;
        opportunityProductRepository.deleteById(productId);
        if (opportunityId != null) {
            recalculateOpportunitySummary(opportunityId);
        }
    }

    @Transactional
    public void recalculateOpportunitySummary(Long opportunityId) {
        if (opportunityId == null) return;
        Opportunity opp = repository.findById(opportunityId).orElse(null);
        if (opp == null) return;

        List<OpportunityProduct> list = opportunityProductRepository.findByOpportunityId(opportunityId);
        if (list == null) list = new ArrayList<>();

        int quantitySum = 0;
        BigDecimal amountSum = BigDecimal.ZERO;
        Set<String> models = new LinkedHashSet<>();

        for (OpportunityProduct p : list) {
            int qty = p.getQuantity() != null ? p.getQuantity() : 0;
            quantitySum += qty;

            BigDecimal rowTotal = p.getTotalPrice();
            if (rowTotal == null && p.getSellingPrice() != null && qty > 0) {
                rowTotal = p.getSellingPrice().multiply(BigDecimal.valueOf(qty));
            }
            if (rowTotal != null) {
                amountSum = amountSum.add(rowTotal);
            }

            String model = p.getProductModel() != null ? p.getProductModel().trim() : "";
            if (!model.isEmpty()) models.add(model);
        }

        List<String> modelList = new ArrayList<>(models);
        String modelSummary;
        if (modelList.isEmpty()) {
            modelSummary = "";
        } else if (modelList.size() <= 3) {
            modelSummary = String.join("、", modelList);
        } else {
            modelSummary = String.join("、", modelList.subList(0, 3)) + " 等" + modelList.size() + "款";
        }

        opp.setProductModel(modelSummary);
        opp.setQuantity(quantitySum);
        opp.setEstimatedAmount(amountSum);
        repository.save(opp);
    }

    private void applySummarySnapshot(Opportunity opp) {
        if (opp == null || opp.getId() == null) return;
        List<OpportunityProduct> list = opportunityProductRepository.findByOpportunityId(opp.getId());
        if (list == null || list.isEmpty()) {
            opp.setProductModel("");
            opp.setQuantity(0);
            opp.setEstimatedAmount(BigDecimal.ZERO);
            return;
        }
        int quantitySum = 0;
        BigDecimal amountSum = BigDecimal.ZERO;
        Set<String> models = new LinkedHashSet<>();
        for (OpportunityProduct p : list) {
            int qty = p.getQuantity() != null ? p.getQuantity() : 0;
            quantitySum += qty;
            BigDecimal rowTotal = p.getTotalPrice();
            if (rowTotal == null && p.getSellingPrice() != null && qty > 0) {
                rowTotal = p.getSellingPrice().multiply(BigDecimal.valueOf(qty));
            }
            if (rowTotal != null) amountSum = amountSum.add(rowTotal);
            String model = p.getProductModel() != null ? p.getProductModel().trim() : "";
            if (!model.isEmpty()) models.add(model);
        }
        List<String> modelList = new ArrayList<>(models);
        String modelSummary;
        if (modelList.isEmpty()) modelSummary = "";
        else if (modelList.size() <= 3) modelSummary = String.join("、", modelList);
        else modelSummary = String.join("、", modelList.subList(0, 3)) + " 等" + modelList.size() + "款";
        opp.setProductModel(modelSummary);
        opp.setQuantity(quantitySum);
        opp.setEstimatedAmount(amountSum);
    }
}
