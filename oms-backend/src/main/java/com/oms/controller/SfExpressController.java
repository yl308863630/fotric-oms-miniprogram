package com.oms.controller;

import com.oms.entity.LogisticsTrace;
import com.oms.service.SfExpressApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 顺丰速运独立 API：路由查询、服务网点查询
 * 文档：https://qiao.sf-express.com/Api/ApiDetails?apiServiceCode=EXP_RECE_SEARCH_ROUTES
 */
@RestController
@RequestMapping("/api/sf")
@CrossOrigin(origins = "*")
public class SfExpressController {

    private final SfExpressApiService sfExpressApiService;

    public SfExpressController(SfExpressApiService sfExpressApiService) {
        this.sfExpressApiService = sfExpressApiService;
    }

    /**
     * 路由查询（EXP_RECE_SEARCH_ROUTES）
     * @param trackingNumber 运单号，如 SF5122068084664
     * @param checkPhoneNo  收件人手机号后四位，如 8805
     */
    @GetMapping("/routes")
    public ResponseEntity<LogisticsTrace> searchRoutes(
            @RequestParam String trackingNumber,
            @RequestParam(required = false) String checkPhoneNo) {
        LogisticsTrace result = sfExpressApiService.searchRoutes(trackingNumber, checkPhoneNo);
        return ResponseEntity.ok(result);
    }

    /**
     * 服务网点查询（EXP_RECE_QUERY_GIS_DEPARTMENT）
     * 官方示例：address, x, y, opt(dq0), deptType(1|2|5), servType(1|2|5), distance(米)
     */
    @GetMapping("/outlets")
    public ResponseEntity<Map<String, Object>> queryOutlets(
            @RequestParam(required = false) String address,
            @RequestParam(required = false, defaultValue = "0") String language,
            @RequestParam(required = false) String x,
            @RequestParam(required = false) String y,
            @RequestParam(required = false) String opt,
            @RequestParam(required = false) String deptType,
            @RequestParam(required = false) String servType,
            @RequestParam(required = false) Integer distance) {
        Map<String, Object> result = sfExpressApiService.queryGisDepartment(
                address, language, x, y, opt, deptType, servType, distance);
        return ResponseEntity.ok(result);
    }
}
