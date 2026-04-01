package com.oms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "oms.security.captcha")
public class CaptchaWhitelistConfig {

    /**
     * 是否启用验证码 IP 白名单。默认开启；无规则时不会生效。
     */
    private Boolean whitelistEnabled = true;

    /**
     * application.yml / 环境变量中的静态兜底 IP 白名单。
     * 支持单 IP 或 CIDR，如 127.0.0.1,123.146.0.21,192.168.0.0/24
     */
    private List<String> whitelistIps = new ArrayList<>();
}
