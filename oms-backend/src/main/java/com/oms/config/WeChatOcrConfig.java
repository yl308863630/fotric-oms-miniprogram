package com.oms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信通用印刷体 OCR（/cv/ocr/comm），需在服务市场开通并按额度计费/免费额度。
 * <p>
 * 配置 AppID、AppSecret 后由 {@link com.oms.service.ocr.WeChatOcrService} 自动获取并缓存 access_token。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.ocr")
public class WeChatOcrConfig {

    /**
     * 是否启用。未配置 appId/appSecret 时即使为 true 也不会调用。
     */
    private Boolean enabled = false;

    private String appId = "";

    private String appSecret = "";

    /**
     * 本地 RapidOCR/Tesseract 结果去掉空白后长度小于该值时，再尝试微信 OCR（节省额度）。
     */
    private Integer minLocalChars = 30;

    /**
     * 为 true 时：每次本地 OCR 后都会再调微信，并与本地结果取较长文本（消耗额度快，仅建议在额度充足或样本极少时开启）。
     */
    private Boolean alwaysTryAfterLocal = false;

    private Integer connectTimeoutMs = 15000;

    private Integer readTimeoutMs = 90000;

    /**
     * 上传图片 JPEG 目标最大字节数，超过则降质量或缩小尺寸（微信对体积有限制）。
     */
    private Integer maxUploadBytes = 1_800_000;
}
