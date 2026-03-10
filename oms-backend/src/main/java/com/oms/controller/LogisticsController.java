package com.oms.controller;

import com.oms.entity.LogisticsTrace;
import com.oms.service.LogisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logistics")
@CrossOrigin(origins = "*")
public class LogisticsController {

    @Autowired
    private LogisticsService logisticsService;

    @GetMapping("/query")
    public ResponseEntity<LogisticsTrace> queryLogistics(
            @RequestParam String company,
            @RequestParam String trackingNumber,
            @RequestParam(required = false) String checkPhoneNo) {
        LogisticsTrace result = logisticsService.queryLogistics(company, trackingNumber, checkPhoneNo);
        return ResponseEntity.ok(result);
    }
}
