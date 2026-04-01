package com.oms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 顺丰速运 API 独立配置（速运类-路由查询、服务网点查询等）
 * 文档：https://qiao.sf-express.com/Api/ApiDetails?apiServiceCode=EXP_RECE_SEARCH_ROUTES
 */
@Data
@Component
@ConfigurationProperties(prefix = "sf-express")
public class SfExpressConfig {

    /** 顾客编码（partnerID） */
    private String partnerId = "FCKZH1AEF3YE";
    /** 沙箱校验码 */
    private String sandboxCheckword = "VBYxDKKlufvu7mui7ONaVxcINnLFcf2m";
    /** 生产校验码 */
    private String prodCheckword = "8lFzNnLpeDNftGCfauWq2D3ilTCxzh0i";

    /** 生产环境地址（速运类） */
    private String prodUrl = "https://bspgw.sf-express.com/std/service";
    /** 香港生产环境 */
    private String hkUrl = "https://sfapi-hk.sf-express.com/std/service";
    /** 沙箱环境地址 */
    private String sandboxUrl = "https://sfapi-sbox.sf-express.com/std/service";

    /** true=生产环境，false=沙箱 */
    private boolean useProd = false;

    public String getBaseUrl() {
        return useProd ? prodUrl : sandboxUrl;
    }

    public String getCheckword() {
        return useProd ? prodCheckword : sandboxCheckword;
    }
}
