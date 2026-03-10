package com.oms.controller;

import com.oms.entity.PartnerInfo;
import com.oms.entity.User;
import com.oms.repository.UserRepository;
import com.oms.service.PartnerInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partner-info")
public class PartnerInfoController {
    @Autowired
    private PartnerInfoService partnerInfoService;

    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (name == null) return null;
        return userRepository.findByUsername(name).orElse(null);
    }

    @GetMapping
    public Page<PartnerInfo> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String identity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return partnerInfoService.getPartnerInfoWithPagination(name, identity, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerInfo> get(@PathVariable Long id) {
        PartnerInfo entity = partnerInfoService.getPartnerInfoById(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        User currentUser = getCurrentUser();
        if (!partnerInfoService.canAccess(currentUser, entity)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(entity);
    }

    @PostMapping
    public ResponseEntity<PartnerInfo> save(@RequestBody PartnerInfo partnerInfo) {
        return ResponseEntity.ok(partnerInfoService.savePartnerInfo(partnerInfo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PartnerInfo> update(@PathVariable Long id, @RequestBody PartnerInfo partnerInfo) {
        PartnerInfo updated = partnerInfoService.updatePartnerInfo(id, partnerInfo);
        if (updated == null) {
            PartnerInfo existing = partnerInfoService.getPartnerInfoById(id);
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = partnerInfoService.deletePartnerInfo(id);
        if (!deleted) {
            PartnerInfo existing = partnerInfoService.getPartnerInfoById(id);
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().build();
    }
}