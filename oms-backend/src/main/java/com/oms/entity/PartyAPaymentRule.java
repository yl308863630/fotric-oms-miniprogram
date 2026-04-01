package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "party_a_payment_rules")
public class PartyAPaymentRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_a_title", nullable = false, length = 255)
    private String partyATitle;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** 基准事件：DELIVERY_DATE / INVOICE_DATE / RECONCILIATION_DATE */
    @Column(name = "base_event_type", nullable = false, length = 32)
    private String baseEventType;

    /** 基准落点日，如震坤行按每月 25 日开始对账 */
    @Column(name = "base_day_of_month")
    private Integer baseDayOfMonth;

    /** 基准周期截点，如交货日 <= 25 进入当月周期，否则进入下一周期 */
    @Column(name = "cycle_cutoff_day")
    private Integer cycleCutoffDay;

    /** 超过截点是否顺延到下一周期 */
    @Column(name = "carry_over_to_next_cycle")
    private Boolean carryOverToNextCycle = Boolean.TRUE;

    /** 基准时间后的偏移天数 */
    @Column(name = "offset_days", nullable = false)
    private Integer offsetDays = 0;

    /** 付款节点类型：NONE / FIXED_DAY_OF_NEXT_MONTH / INTERVAL_DAY_BUCKET */
    @Column(name = "payment_anchor_type", nullable = false, length = 32)
    private String paymentAnchorType;

    @Column(name = "anchor_day_1")
    private Integer anchorDay1;

    @Column(name = "anchor_day_2")
    private Integer anchorDay2;

    @Column(name = "anchor_day_3")
    private Integer anchorDay3;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "example_rule_text", columnDefinition = "TEXT")
    private String exampleRuleText;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (carryOverToNextCycle == null) {
            carryOverToNextCycle = Boolean.TRUE;
        }
        if (offsetDays == null) {
            offsetDays = 0;
        }
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
