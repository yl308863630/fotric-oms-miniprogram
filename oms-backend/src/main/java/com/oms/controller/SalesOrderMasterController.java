package com.oms.controller;

import com.oms.entity.SalesOrderMaster;
import com.oms.service.SalesOrderMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/sales-order-masters")
public class SalesOrderMasterController {
    @Autowired
    private SalesOrderMasterService salesOrderMasterService;

    @GetMapping
    public Page<SalesOrderMaster> list(@RequestParam(required = false) String masterNo,
                                       @RequestParam(required = false) String rootOmsOrderNo,
                                       @RequestParam(required = false) String platformOrderNo,
                                       @RequestParam(required = false) String partyATitle,
                                       @RequestParam(required = false) String masterStatus,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return salesOrderMasterService.searchMasters(
                masterNo,
                rootOmsOrderNo,
                platformOrderNo,
                partyATitle,
                masterStatus,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"))
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return salesOrderMasterService.getMasterDetail(id);
    }

    @PostMapping("/backfill")
    public Map<String, Object> backfill(@RequestBody(required = false) Map<String, Object> request) {
        List<Long> orderIds = new ArrayList<>();
        Integer limit = null;
        boolean onlyMissing = true;
        if (request != null) {
            Object rawOrderIds = request.get("orderIds");
            if (rawOrderIds instanceof List<?>) {
                for (Object item : (List<?>) rawOrderIds) {
                    if (item instanceof Number number) {
                        orderIds.add(number.longValue());
                    } else if (item != null && !String.valueOf(item).trim().isEmpty()) {
                        orderIds.add(Long.parseLong(String.valueOf(item).trim()));
                    }
                }
            }
            if (request.get("limit") instanceof Number number) {
                limit = number.intValue();
            } else if (request.get("limit") != null && !String.valueOf(request.get("limit")).trim().isEmpty()) {
                limit = Integer.parseInt(String.valueOf(request.get("limit")).trim());
            }
            if (request.get("onlyMissing") != null) {
                onlyMissing = Boolean.parseBoolean(String.valueOf(request.get("onlyMissing")));
            }
        }
        return salesOrderMasterService.backfillAnchors(orderIds, limit, onlyMissing);
    }
}
