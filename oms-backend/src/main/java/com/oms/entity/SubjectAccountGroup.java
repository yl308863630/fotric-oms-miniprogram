package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "subject_account_group")
public class SubjectAccountGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 由抬头 + 联系人/手机号归一化生成，保证同一主体组只落一条记录 */
    @Column(name = "group_key", unique = true, nullable = false, length = 255)
    private String groupKey;

    @Column(name = "group_name")
    private String groupName;

    private String title;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "primary_username")
    private String primaryUsername;

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
