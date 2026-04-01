package com.oms.service;

import com.oms.entity.SalesOrder;
import com.oms.repository.SalesOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderAssignReminderService {
    
    @Autowired
    private SalesOrderRepository salesOrderRepository;
    
    @Autowired
    private DingTalkService dingTalkService;
    
    /**
     * 每天早上9点检查待指派订单并发送提醒
     */
    @Scheduled(cron = "0 0 9 * * ?")
    @Transactional
    public void sendOrderAssignReminder() {
        System.out.println("=== 开始执行订单指派提醒任务 ===");
        
        // 查找状态为"待指派"的订单
        List<SalesOrder> pendingOrders = salesOrderRepository.findByStatus("待指派");
        
        System.out.println("找到 " + pendingOrders.size() + " 个待指派订单");
        
        if (pendingOrders.isEmpty()) {
            System.out.println("没有待指派订单，无需提醒");
            return;
        }
        
        // 构建提醒消息
        StringBuilder message = new StringBuilder();
        message.append("⚠️ **订单指派提醒**\n\n");
        message.append("以下订单尚未指派，请及时处理：\n\n");
        
        for (SalesOrder order : pendingOrders) {
            message.append("- 订单号：").append(order.getOmsOrderNo() != null ? order.getOmsOrderNo() : "未设置").append("\n");
            message.append("  客户：").append(order.getPlatformName() != null ? order.getPlatformName() : "未设置").append("\n");
            message.append("  产品：").append(order.getModel() != null ? order.getModel() : "未设置").append("\n");
            message.append("  数量：").append(order.getQuantity() != null ? order.getQuantity() : 0).append("\n");
            message.append("  创建时间：").append(order.getCreateTime() != null ? order.getCreateTime() : "未设置").append("\n\n");
        }
        
        message.append("请尽快登录系统进行订单指派操作！");
        
        try {
            // 发送钉钉通知
            dingTalkService.sendMarkdownMessage(
                "订单指派提醒",
                message.toString(),
                null // 暂时不@具体人员，后续可以根据需要添加
            );
            System.out.println("订单指派提醒通知发送成功");
        } catch (Exception e) {
            System.err.println("发送订单指派提醒失败: " + e.getMessage());
        }
        
        System.out.println("=== 订单指派提醒任务执行完成 ===");
    }
    
    /**
     * 手动触发订单指派提醒
     */
    public void triggerOrderAssignReminder() {
        sendOrderAssignReminder();
    }
}
