package com.oms.dto;

import lombok.Data;

@Data
public class AuthorizationVerifyResult {
    private boolean valid;
    private String message;
    private Long authorizationRecordId;
    private String authorizationCode;
    private String platformName;
    private String grantorName;
    private String granteeName;
    /** 授权展示型号（与授权记录中的产品型号一致） */
    private String productModel;
    /** 授权项目（与授权记录中的项目名称一致） */
    private String projectName;
    private String validFrom;
    private String validTo;
}
