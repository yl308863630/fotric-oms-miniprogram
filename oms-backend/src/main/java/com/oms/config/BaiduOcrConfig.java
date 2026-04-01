package com.oms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百度 OCR 配置。
 * <p>
 * 支持使用 api-key/secret-key，亦兼容用户习惯写法 app-id/secret。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "baidu.ocr")
public class BaiduOcrConfig {

    /**
     * 是否启用。未配置凭证时即使为 true 也不会调用。
     */
    private Boolean enabled = false;

    /**
     * 百度智能云 OCR 的 API Key。
     */
    private String apiKey = "";

    /**
     * 兼容用户按“appid”填写的场景，内部与 apiKey 二选一取值。
     */
    private String appId = "";

    /**
     * 百度智能云 OCR 的 Secret Key。
     */
    private String secretKey = "";

    /**
     * 兼容用户按“secret”填写的场景，内部与 secretKey 二选一取值。
     */
    private String secret = "";

    /**
     * 本地 RapidOCR/Tesseract 结果去掉空白后长度小于该值时，再尝试百度 OCR。
     */
    private Integer minLocalChars = 30;

    /**
     * 为 true 时：每次本地 OCR 后都会再调百度，并与本地结果取较长文本。
     */
    private Boolean alwaysTryAfterLocal = false;

    private Integer connectTimeoutMs = 15000;

    private Integer readTimeoutMs = 90000;

    /**
     * 百度 OCR 单图最大约 4MB，这里预留安全余量。
     */
    private Integer maxUploadBytes = 3_800_000;

    /**
     * 通用文字识别默认中英混合。
     */
    private String languageType = "CHN_ENG";

    /**
     * 是否启用方向检测。
     */
    private Boolean detectDirection = true;

    /**
     * 是否对结算场景调用百度「增值税发票识别」结构化接口（/ocr/v1/vat_invoice），
     * 与 {@link #enabled} 使用同一套 API Key / Secret Key，需在控制台开通对应能力。
     */
    private Boolean vatInvoiceEnabled = true;
}
