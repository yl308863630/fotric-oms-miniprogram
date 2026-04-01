package com.oms.service.ocr;

import java.awt.image.BufferedImage;

/**
 * OCR 引擎抽象，支持 RapidOCR / Tesseract 等实现。
 */
public interface OcrProvider {

    /**
     * 对图片执行 OCR，返回识别文本。
     *
     * @param image 输入图片
     * @return 识别到的文本，识别失败返回 null
     */
    String recognize(BufferedImage image);

    /**
     * 是否可用（依赖是否加载成功）。
     */
    boolean isAvailable();
}
