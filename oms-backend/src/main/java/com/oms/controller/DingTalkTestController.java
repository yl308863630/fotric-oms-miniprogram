package com.oms.controller;

import com.oms.service.DingTalkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/dingtalk")
@CrossOrigin(origins = "*")
public class DingTalkTestController {

    @Autowired
    private DingTalkService dingTalkService;

    @GetMapping("/test")
    public String test() {
        try {
            dingTalkService.sendTextMessage("✅ OMS 订单系统 - 钉钉机器人测试成功！\n\n" +
                    "这是一条来自 OMS 订单系统的测试消息。\n" +
                    "时间: " + java.time.LocalDateTime.now());
            return "测试消息已发送，请查看钉钉群！";
        } catch (Exception e) {
            return "发送失败: " + e.getMessage();
        }
    }

    @GetMapping("/test-order")
    public String testOrder() {
        try {
            dingTalkService.sendOrderNotification(
                "SO20260311-001",
                "测试客户公司",
                "声像仪 ac65mini",
                new BigDecimal("30000.00")
            );
            return "新订单通知已发送！";
        } catch (Exception e) {
            return "发送失败: " + e.getMessage();
        }
    }

    @GetMapping("/test-logistics")
    public String testLogistics() {
        try {
            dingTalkService.sendLogisticsNotification(
                "SO20260311-001",
                "SF3262532854194",
                "已签收"
            );
            return "物流更新通知已发送！";
        } catch (Exception e) {
            return "发送失败: " + e.getMessage();
        }
    }

    @GetMapping("/test-stock")
    public String testStock() {
        try {
            dingTalkService.sendStockWarning(
                "声像仪 ac65mini",
                "A252088-B001",
                5
            );
            return "库存预警通知已发送！";
        } catch (Exception e) {
            return "发送失败: " + e.getMessage();
        }
    }
}