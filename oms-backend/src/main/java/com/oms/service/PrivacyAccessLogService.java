package com.oms.service;

import com.oms.entity.PrivacyAccessLog;
import com.oms.repository.PrivacyAccessLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PrivacyAccessLogService {

    @Autowired
    private PrivacyAccessLogRepository repository;

    @Transactional
    public void log(Long operatorId, String operatorName, String clientIp,
                   String action, String targetType, String targetId, String fieldOrDescription) {
        PrivacyAccessLog log = new PrivacyAccessLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName != null ? operatorName : "");
        log.setClientIp(clientIp);
        log.setAction(action != null ? action : "COPY");
        log.setTargetType(targetType != null ? targetType : "");
        log.setTargetId(targetId != null ? targetId : "");
        log.setFieldOrDescription(fieldOrDescription);
        repository.save(log);
    }

    public Page<PrivacyAccessLog> findPage(String operatorName, String targetType, String action,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           Pageable pageable) {
        Specification<PrivacyAccessLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (operatorName != null && !operatorName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("operatorName")), "%" + operatorName.toLowerCase().trim() + "%"));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType.trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable);
    }
}
