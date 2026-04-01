package com.oms.repository;

import com.oms.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    List<OperationLog> findByTargetTypeAndTargetIdOrderByCreateTimeDesc(String targetType, String targetId);
    /** 仅查当前操作人的日志（谁的列表谁的操作日志，不跨账号） */
    List<OperationLog> findByTargetTypeAndTargetIdAndOperatorNameOrderByCreateTimeDesc(String targetType, String targetId, String operatorName);
}
