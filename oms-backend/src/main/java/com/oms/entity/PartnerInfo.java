package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "partner_info")
public class PartnerInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "text")
    private String identities;

    private String title;

    private String taxNumber;

    private String bankName;

    private String bankAccount;

    private String bankAddress;

    private String contactPerson;

    private String contactPhone;

    private String email;

    private String username;

    /** 创建人用户 id，用于按抬头权限：仅创建人（或 admin/飞础科）可查看与编辑 */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}