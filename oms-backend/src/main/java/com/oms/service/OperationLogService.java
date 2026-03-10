package com.oms.service;

import com.oms.entity.OperationLog;
import com.oms.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class OperationLogService {
    @Autowired
    private OperationLogRepository operationLogRepository;

    @Transactional
    public void log(String operatorName, String action, String targetType, String targetId, String details) {
        OperationLog log = new OperationLog();
        log.setOperatorName(operatorName);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        operationLogRepository.save(log);
    }

    public List<OperationLog> getLogs(String targetType, String targetId) {
        return operationLogRepository.findByTargetTypeAndTargetIdOrderByCreateTimeDesc(targetType, targetId);
    }
}
