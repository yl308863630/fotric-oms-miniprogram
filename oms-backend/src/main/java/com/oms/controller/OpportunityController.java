package com.oms.controller;

import com.oms.entity.Opportunity;
import com.oms.entity.OpportunityProduct;
import com.oms.service.OpportunityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/opportunities")
@CrossOrigin(origins = "*")
public class OpportunityController {

    @Autowired
    private OpportunityService service;

    @GetMapping
    public Page<Opportunity> getAllOpportunities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.getOpportunitiesWithPagination(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Opportunity> getOpportunityById(@PathVariable Long id) {
        return service.getOpportunityById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Opportunity createOpportunity(@RequestBody Opportunity opportunity) {
        return service.createOpportunity(opportunity);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Opportunity> updateOpportunity(@PathVariable Long id, @RequestBody Opportunity opportunityDetails) {
        try {
            return ResponseEntity.ok(service.updateOpportunity(id, opportunityDetails));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOpportunity(@PathVariable Long id) {
        service.deleteOpportunity(id);
        return ResponseEntity.noContent().build();
    }
    
    // 产品相关接口
    @GetMapping("/{id}/products")
    public List<OpportunityProduct> getOpportunityProducts(@PathVariable Long id) {
        return service.getOpportunityProducts(id);
    }
    
    @PostMapping("/{id}/products")
    public OpportunityProduct addOpportunityProduct(@PathVariable Long id, @RequestBody OpportunityProduct product) {
        product.setOpportunity(service.getOpportunityById(id)
                .orElseThrow(() -> new RuntimeException("Opportunity not found with id: " + id)));
        return service.addOpportunityProduct(product);
    }
    
    @PutMapping("/{id}/products")
    public ResponseEntity<Void> updateOpportunityProducts(@PathVariable Long id, @RequestBody List<OpportunityProduct> products) {
        service.updateOpportunityProducts(id, products);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> deleteOpportunityProduct(@PathVariable Long productId) {
        service.deleteOpportunityProduct(productId);
        return ResponseEntity.noContent().build();
    }
}
