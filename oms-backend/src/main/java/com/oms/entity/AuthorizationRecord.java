package com.oms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "authorization_records")
public class AuthorizationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_code", unique = true, nullable = false, length = 64)
    private String authorizationCode;

    @Column(name = "platform_name", length = 255)
    private String platformName;

    @Column(name = "grantor_name", length = 255)
    private String grantorName;

    @Column(name = "grantee_name", length = 255)
    private String granteeName;

    @Column(name = "authorized_subject", length = 255)
    private String authorizedSubject;

    @Column(name = "product_model", columnDefinition = "TEXT")
    private String productModel;

    @Column(name = "project_name", columnDefinition = "TEXT")
    private String projectName;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "template_url", length = 500)
    private String templateUrl;

    @Column(name = "word_url", length = 500)
    private String wordUrl;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "final_pdf_url", length = 500)
    private String finalPdfUrl;

    @Column(name = "qr_url", length = 500)
    private String qrUrl;

    @Column(name = "verify_token", columnDefinition = "TEXT")
    private String verifyToken;

    @Column(name = "document_sha256", length = 128)
    private String documentSha256;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 64)
    private String createdByName;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (updateTime == null) updateTime = LocalDateTime.now();
        if (status == null || status.isBlank()) status = "草稿";
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
