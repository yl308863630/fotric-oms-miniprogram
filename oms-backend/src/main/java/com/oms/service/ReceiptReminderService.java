package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.repository.SalesOrderRepository;
import com.oms.util.HolidayUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReceiptReminderService {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private OperationLogService operationLogService;

    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendReceiptReminder() {
        System.out.println("=== 开始执行签收单回传提醒任务 ===");
        
        // 查找已发货且需要签收单回传的订单
        List<SalesOrder> orders = salesOrderRepository.findByStatusAndNeedReceiptSlip("已发货", true);
        
        System.out.println("找到 " + orders.size() + " 个需要提醒的订单");
        
        LocalDateTime now = LocalDateTime.now();
        
        for (SalesOrder order : orders) {
            if (order.getTrackingNumber() == null || order.getTrackingNumber().isEmpty()) {
                continue;
            }
            
            // 获取物流单号上传时间（使用updateTime作为上传时间）
            LocalDateTime uploadTime = order.getUpdateTime();
            
            if (uploadTime == null) {
                continue;
            }
            
            LocalDate uploadDate = uploadTime.toLocalDate();
            
            // 计算3天和5天的提醒时间
            LocalDateTime reminder3Days = HolidayUtil.getReminderTime(uploadDate, 3);
            LocalDateTime reminder5Days = HolidayUtil.getReminderTime(uploadDate, 5);
            
            // 检查是否需要提醒
            boolean needReminder3Days = now.isAfter(reminder3Days) && now.isBefore(reminder3Days.plusHours(24));
            boolean needReminder5Days = now.isAfter(reminder5Days) && now.isBefore(reminder5Days.plusHours(24));
            
            if (needReminder3Days || needReminder5Days) {
                String days = needReminder3Days ? "3天" : "5天";
                sendReminderNotification(order, days);
            }
        }
        
        System.out.println("=== 签收单回传提醒任务执行完成 ===");
    }

    private void sendReminderNotification(SalesOrder order, String days) {
        try {
            // @rxkj-sw 和 rxkj-ck 的手机号（需要根据实际情况填写）
            List<String> atMobiles = List.of();
            
            String title = "📋 签收单回传提醒";
            String text = String.format(
                "## 📋 签收单回传提醒\n\n" +
                "**订单号：** %s\n\n" +
                "**物流单号：** %s\n\n" +
                "**收货人：** %s\n\n" +
                "**收货地址：** %s\n\n" +
                "**物流单号上传时间：** %s\n\n" +
                "**已过：** %s\n\n" +
                "**请及时上传签收单！**\n\n" +
                "---\n" +
                "*来自 OMS 订单系统*",
                order.getOmsOrderNo(),
                order.getTrackingNumber(),
                order.getReceiverName() != null ? order.getReceiverName() : "未填写",
                order.getReceiverAddress() != null ? order.getReceiverAddress() : "未填写",
                order.getUpdateTime() != null ? order.getUpdateTime().toString() : "未知",
                days
            );
            
            dingTalkService.sendMarkdownMessage(title, text, atMobiles);
            
            System.out.println("已发送签收单回传提醒，订单号：" + order.getOmsOrderNo() + "，已过：" + days);
            
            // 记录操作日志
            operationLogService.log(
                "系统",
                "签收单回传提醒",
                "SALES_ORDER",
                String.valueOf(order.getId()),
                "发送签收单回传提醒，已过" + days
            );
            
        } catch (Exception e) {
            System.err.println("发送签收单回传提醒失败，订单号：" + order.getOmsOrderNo() + "，错误：" + e.getMessage());
            e.printStackTrace();
        }
    }
}