package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "authorization_import_mappings")
public class AuthorizationImportMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mapping_name", nullable = false, length = 100)
    private String mappingName;

    @Column(name = "mapping_json", columnDefinition = "TEXT")
    private String mappingJson;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (updateTime == null) updateTime = LocalDateTime.now();
        if (isDefault == null) isDefault = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
