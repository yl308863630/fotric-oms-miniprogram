package com.oms.service;

import com.oms.entity.Opportunity;
import com.oms.entity.OpportunityProduct;
import com.oms.repository.OpportunityProductRepository;
import com.oms.repository.OpportunityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class OpportunityService {

    @Autowired
    private OpportunityRepository repository;
    
    @Autowired
    private OpportunityProductRepository opportunityProductRepository;

    public List<Opportunity> getAllOpportunities() {
        return repository.findAllByOrderByCreateTimeDesc();
    }

    public Page<Opportunity> getOpportunitiesWithPagination(Pageable pageable) {
        return repository.findAllByOrderByCreateTimeDesc(pageable);
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
        return opportunityProductRepository.save(product);
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
    }
    
    @Transactional
    public void deleteOpportunityProduct(Long productId) {
        opportunityProductRepository.deleteById(productId);
    }
}
