package com.oms.controller;

import com.oms.entity.OperationLog;
import com.oms.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin
public class OperationLogController {
    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/{targetType}/{targetId}")
    public List<OperationLog> getLogs(@PathVariable String targetType, @PathVariable String targetId) {
        return operationLogService.getLogs(targetType, targetId);
    }
}
