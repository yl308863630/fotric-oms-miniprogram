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

    private static final int MAX_DETAILS_LENGTH = 950;

    @Transactional
    public void log(String operatorName, String action, String targetType, String targetId, String details) {
        OperationLog log = new OperationLog();
        log.setOperatorName(operatorName);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(truncateDetails(details));
        operationLogRepository.save(log);
    }

    /** 与实体列长度 1000 对齐，避免长文本导致写库失败、连带业务事务回滚（如退回订单） */
    private static String truncateDetails(String details) {
        if (details == null) {
            return null;
        }
        if (details.length() <= MAX_DETAILS_LENGTH) {
            return details;
        }
        return details.substring(0, MAX_DETAILS_LENGTH) + "…(截断)";
    }

    public List<OperationLog> getLogs(String targetType, String targetId) {
        return operationLogRepository.findByTargetTypeAndTargetIdOrderByCreateTimeDesc(targetType, targetId);
    }

    /** 仅返回当前操作人的日志（谁的销售/采购列表谁的操作日志，不跨账号） */
    public List<OperationLog> getLogsForOperator(String targetType, String targetId, String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return List.of();
        }
        return operationLogRepository.findByTargetTypeAndTargetIdAndOperatorNameOrderByCreateTimeDesc(targetType, targetId, operatorName.trim());
    }
}
