package com.oms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jd-logistics")
public class JdLogisticsConfig {

    /** 京东物流业务接口网关，例如 https://api.jdl.com */
    private String baseUrl;

    /** OAuth 获取 token 地址，例如 https://oauth.jd.com/oauth/token */
    private String oauthUrl;

    /** OAuth 授权页地址，用于一次性换取 authorization_code */
    private String authorizeUrl = "https://oauth.jd.com/oauth/authorize";

    private String appKey;
    private String appSecret;

    /** 授权码模式回调地址（与京东开放平台配置一致） */
    private String redirectUri;

    /** 一次性授权码（首次换 token 用） */
    private String authorizationCode;

    /** 已获取的 access token（可直接使用） */
    private String accessToken;

    /** 长期刷新令牌（后续自动刷新用） */
    private String refreshToken;

    /** 客户编码，后续下单/轨迹查询会用到 */
    private String customerCode;

    /** 业务来源：1=B2C, 2=C2B */
    private Integer orderOrigin = 1;

    /** token 提前刷新天数，默认 7 天 */
    private Integer refreshBeforeDays = 7;
}

